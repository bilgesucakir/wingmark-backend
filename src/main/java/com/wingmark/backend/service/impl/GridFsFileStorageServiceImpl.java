package com.wingmark.backend.service.impl;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.wingmark.backend.config.UploadProperties;
import com.wingmark.backend.exception.ApiException;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.FileStorageException;
import com.wingmark.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/** Stores uploaded images in the MongoDB GridFS bucket {@code uploads}, served at {@code /uploads/{uuid}.{ext}} by {@code UploadsController}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GridFsFileStorageServiceImpl implements FileStorageService {

    static final int MAX_DIMENSION = PhotoImages.MAX_DIMENSION;
    static final int THUMBNAIL_DIMENSION = PhotoImages.THUMBNAIL_DIMENSION;
    static final String THUMBNAIL_SUFFIX = PhotoImages.THUMBNAIL_SUFFIX;

    private final GridFsTemplate gridFsTemplate;
    private final UploadProperties uploadProperties;

    @Override
    public String store(MultipartFile file) {
        return store(file, null);
    }

    @Override
    public String store(MultipartFile file, UUID ownerId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.INVALID_FILE, "Uploaded file is empty");
        }
        if (ownerId != null && uploadProperties.maxPhotosPerUser() > 0
                && countPhotosOwnedBy(ownerId) >= uploadProperties.maxPhotosPerUser()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.PHOTO_QUOTA_EXCEEDED,
                    "You have reached the limit of " + uploadProperties.maxPhotosPerUser()
                            + " stored photos; delete some sightings or photos first");
        }
        PhotoImages.Processed processed = PhotoImages.process(file);
        saveRaw(processed.photoName(), processed.photo(), processed.contentType(), ownerId);
        saveRaw(processed.thumbnailName(), processed.thumbnail(), "image/jpeg", null);
        return PhotoImages.UPLOADS_PREFIX + processed.photoName();
    }

    /** Saves bytes unchanged in GridFS under the given filename. */
    private void saveRaw(String filename, byte[] content, String contentType, UUID ownerId) {
        try {
            Document metadata = new Document("contentType", contentType);
            if (ownerId != null) {
                metadata.append("ownerId", ownerId.toString());
            }
            gridFsTemplate.store(new ByteArrayInputStream(content), filename, contentType, metadata);
        } catch (RuntimeException e) {
            log.error("Failed to store uploaded file {} in GridFS", filename, e);
            throw new FileStorageException("Failed to store uploaded file", e);
        }
    }

    @Override
    public Optional<String> storedFilename(String url) {
        return PhotoImages.storedFilename(url);
    }

    @Override
    public long countPhotosOwnedBy(UUID ownerId) {
        return photoFilenamesOwnedBy(ownerId).size();
    }

    /** Returns the filenames of the photos (thumbnails not counted) stored with this owner. */
    public Set<String> photoFilenamesOwnedBy(UUID ownerId) {
        Query query = Query.query(Criteria.where("metadata.ownerId").is(ownerId.toString())
                .and("filename").regex("^(?!.*_thumb\\.jpg$)"));
        Set<String> names = new HashSet<>();
        for (GridFSFile file : gridFsTemplate.find(query)) {
            names.add(file.getFilename());
        }
        return names;
    }

    /** Returns the recorded owner of a stored photo, or empty if it has none (older uploads). */
    public Optional<UUID> ownerOf(String filename) {
        GridFSFile file = gridFsTemplate.findOne(byName(filename));
        if (file == null || file.getMetadata() == null || file.getMetadata().getString("ownerId") == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(file.getMetadata().getString("ownerId")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<StoredFileInfo> listFiles() {
        List<StoredFileInfo> files = new ArrayList<>();
        for (GridFSFile file : gridFsTemplate.find(new Query())) {
            files.add(new StoredFileInfo(file.getFilename(), file.getUploadDate().toInstant(), file.getLength()));
        }
        return files;
    }

    @Override
    public Optional<String> thumbnailUrl(String photoUrl) {
        return PhotoImages.thumbnailUrl(photoUrl);
    }

    @Override
    public boolean exists(String filename) {
        return gridFsTemplate.findOne(byName(filename)) != null;
    }

    @Override
    public Optional<StoredFile> load(String filename) {
        GridFSFile file = gridFsTemplate.findOne(byName(filename));
        if (file == null) {
            // Photos uploaded before thumbnails existed get theirs the first time one is asked for.
            return filename.endsWith(THUMBNAIL_SUFFIX) ? createMissingThumbnail(filename) : Optional.empty();
        }
        try (InputStream in = gridFsTemplate.getResource(file).getInputStream()) {
            return Optional.of(new StoredFile(in.readAllBytes(), contentTypeOf(file, filename)));
        } catch (IOException e) {
            throw new FileStorageException("Failed to read uploaded file", e);
        }
    }

    /** Builds and stores the thumbnail of an existing photo; empty if the photo itself does not exist. */
    private Optional<StoredFile> createMissingThumbnail(String thumbnailName) {
        String base = PhotoImages.baseOf(thumbnailName);
        for (String extension : PhotoImages.photoExtensions()) {
            Optional<StoredFile> original = load(base + extension);
            if (original.isEmpty()) {
                continue;
            }
            Optional<byte[]> thumbnail = PhotoImages.thumbnailOf(original.get().content());
            if (thumbnail.isEmpty()) {
                log.warn("Could not create the thumbnail for {}", base + extension);
                return Optional.empty();
            }
            saveRaw(thumbnailName, thumbnail.get(), "image/jpeg", null);
            return Optional.of(new StoredFile(thumbnail.get(), "image/jpeg"));
        }
        return Optional.empty();
    }

    @Override
    public void delete(String filename) {
        try {
            gridFsTemplate.delete(byName(filename));
            if (!filename.endsWith(THUMBNAIL_SUFFIX) && filename.contains(".")) {
                gridFsTemplate.delete(byName(PhotoImages.thumbnailNameOf(filename)));
            }
        } catch (RuntimeException e) {
            log.error("Failed to delete uploaded file {}", filename, e);
        }
    }

    private static String contentTypeOf(GridFSFile file, String filename) {
        Document metadata = file.getMetadata();
        if (metadata != null && metadata.getString("contentType") != null) {
            return metadata.getString("contentType");
        }
        String byExtension = PhotoImages.contentTypeForExtension(filename);
        return byExtension != null ? byExtension : "application/octet-stream";
    }

    private static Query byName(String filename) {
        return Query.query(Criteria.where("filename").is(filename));
    }
}
