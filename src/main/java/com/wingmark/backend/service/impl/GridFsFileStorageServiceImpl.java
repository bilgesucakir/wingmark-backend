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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stores uploaded images in the MongoDB GridFS bucket {@code uploads}, served at {@code /uploads/{uuid}.{ext}} by {@code UploadsController}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GridFsFileStorageServiceImpl implements FileStorageService {

    private static final String UPLOADS_PREFIX = "/uploads/";
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png");
    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");
    /** Longest edge in pixels after re-encoding. */
    static final int MAX_DIMENSION = 1280;
    /** Longest edge in pixels of the thumbnail shown in lists and the guide. */
    static final int THUMBNAIL_DIMENSION = 400;
    /** Suffix of the thumbnail stored next to each photo: {@code <name>_thumb.jpg}. */
    static final String THUMBNAIL_SUFFIX = "_thumb.jpg";
    private static final float JPEG_QUALITY = 0.75f;
    private static final float THUMBNAIL_QUALITY = 0.7f;

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

        // Only images, and only formats the JDK can decode and re-encode. Anything else is
        // refused rather than stored as-is: /uploads is served publicly from the same origin
        // as the admin panel, so an uploaded .html/.svg would be a stored-XSS vector.
        String contentType = file.getContentType();
        String extension = IMAGE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG and PNG images are accepted (got " + contentType + ")");
        }

        byte[] encoded;
        byte[] thumbnail;
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new BadRequestException(ErrorCode.INVALID_FILE, "Uploaded file is not a valid image");
            }
            // Re-encoding drops EXIF metadata (including GPS tags) - the sighting's location
            // is already captured explicitly on the log - and downscaling caps storage use.
            encoded = encode(downscale(image, MAX_DIMENSION), extension.substring(1), JPEG_QUALITY);
            thumbnail = thumbnailBytes(image);
        } catch (IOException e) {
            // Includes images ImageIO can open but not decode (e.g. CMYK JPEGs): a bad file, not a server fault.
            throw new BadRequestException(ErrorCode.INVALID_FILE, "Uploaded file could not be read as an image");
        }

        String base = UUID.randomUUID().toString();
        String filename = base + extension;
        saveRaw(filename, encoded, contentType, ownerId);
        saveRaw(base + THUMBNAIL_SUFFIX, thumbnail, "image/jpeg", null);
        return UPLOADS_PREFIX + filename;
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
        if (url == null) {
            return Optional.empty();
        }
        int index = url.lastIndexOf(UPLOADS_PREFIX);
        if (index < 0) {
            return Optional.empty();
        }
        String filename = url.substring(index + UPLOADS_PREFIX.length());
        if (filename.isEmpty() || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return Optional.empty();
        }
        return Optional.of(filename);
    }

    @Override
    public long countPhotosOwnedBy(UUID ownerId) {
        Query query = Query.query(Criteria.where("metadata.ownerId").is(ownerId.toString())
                .and("filename").regex("^(?!.*_thumb\\.jpg$)"));
        long count = 0;
        for (GridFSFile ignored : gridFsTemplate.find(query)) {
            count++;
        }
        return count;
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
        return storedFilename(photoUrl)
                .filter(name -> !name.endsWith(THUMBNAIL_SUFFIX) && name.contains("."))
                .map(name -> UPLOADS_PREFIX + name.substring(0, name.lastIndexOf('.')) + THUMBNAIL_SUFFIX);
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
        String base = thumbnailName.substring(0, thumbnailName.length() - THUMBNAIL_SUFFIX.length());
        for (String extension : IMAGE_EXTENSIONS.values()) {
            Optional<StoredFile> original = load(base + extension);
            if (original.isEmpty()) {
                continue;
            }
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(original.get().content()));
                if (image == null) {
                    return Optional.empty();
                }
                byte[] thumbnail = thumbnailBytes(image);
                saveRaw(thumbnailName, thumbnail, "image/jpeg", null);
                return Optional.of(new StoredFile(thumbnail, "image/jpeg"));
            } catch (IOException e) {
                log.warn("Could not create the thumbnail for {}", base + extension, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static byte[] thumbnailBytes(BufferedImage image) throws IOException {
        return encode(downscale(image, THUMBNAIL_DIMENSION), "jpg", THUMBNAIL_QUALITY);
    }

    @Override
    public void delete(String filename) {
        try {
            gridFsTemplate.delete(byName(filename));
            if (!filename.endsWith(THUMBNAIL_SUFFIX) && filename.contains(".")) {
                gridFsTemplate.delete(byName(filename.substring(0, filename.lastIndexOf('.')) + THUMBNAIL_SUFFIX));
            }
        } catch (RuntimeException e) {
            log.error("Failed to delete uploaded file {}", filename, e);
        }
    }

    /** Content type for a filename extension; null if it is not a served image type. */
    private static String contentTypeForExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? null : CONTENT_TYPES_BY_EXTENSION.get(filename.substring(dot + 1).toLowerCase());
    }

    private static String contentTypeOf(GridFSFile file, String filename) {
        Document metadata = file.getMetadata();
        if (metadata != null && metadata.getString("contentType") != null) {
            return metadata.getString("contentType");
        }
        String byExtension = contentTypeForExtension(filename);
        return byExtension != null ? byExtension : "application/octet-stream";
    }

    private static Query byName(String filename) {
        return Query.query(Criteria.where("filename").is(filename));
    }

    private static BufferedImage downscale(BufferedImage image, int maxDimension) {
        int width = image.getWidth();
        int height = image.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxDimension) {
            return image;
        }
        double scale = (double) maxDimension / longest;
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, type);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return scaled;
    }

    private static byte[] encode(BufferedImage image, String format, float jpegQuality) throws IOException {
        BufferedImage toWrite = image;
        if ("jpg".equals(format) && image.getColorModel().hasAlpha()) {
            // JPEG has no alpha channel; ImageIO refuses ARGB input for it.
            toWrite = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = toWrite.createGraphics();
            g.drawImage(image, 0, 0, Color.WHITE, null);
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if ("jpg".equals(format)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try (ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(jpegQuality);
                writer.setOutput(imageOut);
                writer.write(null, new IIOImage(toWrite, null, null), param);
            } finally {
                writer.dispose();
            }
        } else if (!ImageIO.write(toWrite, format, out)) {
            throw new FileStorageException("No image writer available for " + format);
        }
        return out.toByteArray();
    }
}
