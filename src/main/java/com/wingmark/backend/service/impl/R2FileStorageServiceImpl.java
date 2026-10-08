package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.UploadProperties;
import com.wingmark.backend.entity.UploadedFile;
import com.wingmark.backend.exception.ApiException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.FileStorageException;
import com.wingmark.backend.repository.UploadedFileRepository;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.storage.ObjectStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores new photos in Cloudflare R2 and still reads, counts, lists and deletes the older ones kept in the database
 * (GridFS), so nothing breaks while photos are migrated. The bucket is private: photos are served by the backend.
 * Exists only when {@code UPLOAD_STORAGE=r2}.
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "wingmark.uploads.storage", havingValue = "r2")
public class R2FileStorageServiceImpl implements FileStorageService {

    /** Every photo and thumbnail lives under this prefix in the bucket, with the same file name as in the URL. */
    static final String KEY_PREFIX = "photos/";

    private final ObjectStore objectStore;
    private final UploadedFileRepository uploadedFiles;
    private final GridFsFileStorageServiceImpl gridFs;
    private final UploadProperties uploadProperties;

    public R2FileStorageServiceImpl(ObjectStore objectStore, UploadedFileRepository uploadedFiles,
                                    GridFsFileStorageServiceImpl gridFs, UploadProperties uploadProperties) {
        this.objectStore = objectStore;
        this.uploadedFiles = uploadedFiles;
        this.gridFs = gridFs;
        this.uploadProperties = uploadProperties;
    }

    @Override
    public String store(MultipartFile file) {
        return store(file, null);
    }

    @Override
    public String store(MultipartFile file, UUID ownerId) {
        PhotoImages.Processed processed = PhotoImages.process(file);
        if (ownerId != null && uploadProperties.maxPhotosPerUser() > 0
                && countPhotosOwnedBy(ownerId) >= uploadProperties.maxPhotosPerUser()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.PHOTO_QUOTA_EXCEEDED,
                    "You have reached the limit of " + uploadProperties.maxPhotosPerUser()
                            + " stored photos; delete some sightings or photos first");
        }
        try {
            objectStore.put(key(processed.photoName()), processed.photo(), processed.contentType());
            objectStore.put(key(processed.thumbnailName()), processed.thumbnail(), "image/jpeg");
            uploadedFiles.save(UploadedFile.builder().filename(processed.photoName()).ownerId(ownerId)
                    .sizeBytes(processed.photo().length).build());
        } catch (RuntimeException e) {
            // Do not leave half an upload behind.
            objectStore.delete(key(processed.photoName()));
            objectStore.delete(key(processed.thumbnailName()));
            if (e instanceof FileStorageException) {
                throw e;
            }
            throw new FileStorageException("Failed to store uploaded file", e);
        }
        return PhotoImages.UPLOADS_PREFIX + processed.photoName();
    }

    @Override
    public Optional<String> storedFilename(String url) {
        return PhotoImages.storedFilename(url);
    }

    @Override
    public Optional<String> thumbnailUrl(String photoUrl) {
        return PhotoImages.thumbnailUrl(photoUrl);
    }

    @Override
    public boolean exists(String filename) {
        return objectStore.size(key(filename)).isPresent() || gridFs.exists(filename);
    }

    @Override
    public Optional<StoredFile> load(String filename) {
        Optional<ObjectStore.StoredObject> object = objectStore.get(key(filename));
        if (object.isPresent()) {
            return Optional.of(new StoredFile(object.get().content(), contentTypeOf(object.get(), filename)));
        }
        Optional<StoredFile> older = gridFs.load(filename);
        if (older.isPresent() || !filename.endsWith(PhotoImages.THUMBNAIL_SUFFIX)) {
            return older;
        }
        return createMissingThumbnail(filename);
    }

    /** Builds the thumbnail of a photo that is in R2 but has none yet, and stores it. */
    private Optional<StoredFile> createMissingThumbnail(String thumbnailName) {
        String base = PhotoImages.baseOf(thumbnailName);
        for (String extension : PhotoImages.photoExtensions()) {
            Optional<ObjectStore.StoredObject> photo = objectStore.get(key(base + extension));
            if (photo.isEmpty()) {
                continue;
            }
            Optional<byte[]> thumbnail = PhotoImages.thumbnailOf(photo.get().content());
            if (thumbnail.isEmpty()) {
                return Optional.empty();
            }
            objectStore.put(key(thumbnailName), thumbnail.get(), "image/jpeg");
            return Optional.of(new StoredFile(thumbnail.get(), "image/jpeg"));
        }
        return Optional.empty();
    }

    @Override
    public void delete(String filename) {
        // Deleted from both places: a photo the user removed must not survive in the older store.
        objectStore.delete(key(filename));
        if (!filename.endsWith(PhotoImages.THUMBNAIL_SUFFIX) && filename.contains(".")) {
            objectStore.delete(key(PhotoImages.thumbnailNameOf(filename)));
            uploadedFiles.deleteByFilename(filename);
        }
        gridFs.delete(filename);
    }

    @Override
    public long countPhotosOwnedBy(UUID ownerId) {
        // Union, because a migrated photo is in both places until the database copy is removed.
        Set<String> names = new HashSet<>(gridFs.photoFilenamesOwnedBy(ownerId));
        uploadedFiles.findByOwnerId(ownerId).forEach(file -> names.add(file.getFilename()));
        return names.size();
    }

    @Override
    public List<StoredFileInfo> listFiles() {
        List<StoredFileInfo> files = new ArrayList<>();
        Set<String> indexedBases = new HashSet<>();
        for (UploadedFile file : uploadedFiles.findAll()) {
            indexedBases.add(PhotoImages.baseOf(file.getFilename()));
            files.add(new StoredFileInfo(file.getFilename(), file.getCreatedAt(), file.getSizeBytes()));
        }
        // The older store's files, except photos and thumbnails whose copy is already in R2.
        gridFs.listFiles().stream()
                .filter(file -> !indexedBases.contains(PhotoImages.baseOf(file.filename())))
                .forEach(files::add);
        return files;
    }

    private static String key(String filename) {
        return KEY_PREFIX + filename;
    }

    private static String contentTypeOf(ObjectStore.StoredObject object, String filename) {
        if (object.contentType() != null && !object.contentType().isBlank()) {
            return object.contentType();
        }
        String byExtension = PhotoImages.contentTypeForExtension(filename);
        return byExtension != null ? byExtension : "application/octet-stream";
    }
}
