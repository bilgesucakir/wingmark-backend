package com.wingmark.backend.service.impl;

import com.mongodb.client.gridfs.model.GridFSFile;
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    static final int MAX_DIMENSION = 1600;

    private final GridFsTemplate gridFsTemplate;

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.INVALID_FILE, "Uploaded file is empty");
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
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new BadRequestException(ErrorCode.INVALID_FILE, "Uploaded file is not a valid image");
            }
            // Re-encoding drops EXIF metadata (including GPS tags) - the sighting's location
            // is already captured explicitly on the log - and downscaling caps storage use.
            encoded = encode(downscale(image), extension.substring(1));
        } catch (IOException e) {
            // Includes images ImageIO can open but not decode (e.g. CMYK JPEGs): a bad file, not a server fault.
            throw new BadRequestException(ErrorCode.INVALID_FILE, "Uploaded file could not be read as an image");
        }

        String filename = UUID.randomUUID() + extension;
        saveRaw(filename, encoded, contentType);
        return UPLOADS_PREFIX + filename;
    }

    /** Saves bytes unchanged in GridFS under the given filename. */
    private void saveRaw(String filename, byte[] content, String contentType) {
        try {
            gridFsTemplate.store(new ByteArrayInputStream(content), filename, contentType,
                    new Document("contentType", contentType));
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
    public boolean exists(String filename) {
        return gridFsTemplate.findOne(byName(filename)) != null;
    }

    @Override
    public Optional<StoredFile> load(String filename) {
        GridFSFile file = gridFsTemplate.findOne(byName(filename));
        if (file == null) {
            return Optional.empty();
        }
        try (InputStream in = gridFsTemplate.getResource(file).getInputStream()) {
            return Optional.of(new StoredFile(in.readAllBytes(), contentTypeOf(file, filename)));
        } catch (IOException e) {
            throw new FileStorageException("Failed to read uploaded file", e);
        }
    }

    @Override
    public void delete(String filename) {
        try {
            gridFsTemplate.delete(byName(filename));
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

    private static BufferedImage downscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_DIMENSION) {
            return image;
        }
        double scale = (double) MAX_DIMENSION / longest;
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

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        BufferedImage toWrite = image;
        if ("jpg".equals(format) && image.getColorModel().hasAlpha()) {
            // JPEG has no alpha channel; ImageIO refuses ARGB input for it.
            toWrite = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = toWrite.createGraphics();
            g.drawImage(image, 0, 0, Color.WHITE, null);
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(toWrite, format, out)) {
            throw new FileStorageException("No image writer available for " + format);
        }
        return out.toByteArray();
    }
}
