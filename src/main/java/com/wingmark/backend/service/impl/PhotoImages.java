package com.wingmark.backend.service.impl;

import com.wingmark.backend.exception.ApiException;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.FileStorageException;
import org.springframework.http.HttpStatus;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Image handling shared by every photo store: validation, EXIF-stripping re-encode, thumbnails, and file naming. */
final class PhotoImages {

    static final String UPLOADS_PREFIX = "/uploads/";
    /** Longest edge in pixels after re-encoding. */
    static final int MAX_DIMENSION = 1280;
    /** Longest edge in pixels of the thumbnail shown in lists and the guide. */
    static final int THUMBNAIL_DIMENSION = 400;
    /** Suffix of the thumbnail stored next to each photo: {@code <name>_thumb.jpg}. */
    static final String THUMBNAIL_SUFFIX = "_thumb.jpg";

    private static final float JPEG_QUALITY = 0.75f;
    private static final float THUMBNAIL_QUALITY = 0.7f;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of("image/jpeg", ".jpg", "image/png", ".png");
    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png");

    /** A validated upload: a new random base name, the re-encoded photo and its thumbnail. */
    record Processed(String base, String extension, String contentType, byte[] photo, byte[] thumbnail) {
        String photoName() {
            return base + extension;
        }

        String thumbnailName() {
            return base + THUMBNAIL_SUFFIX;
        }
    }

    private PhotoImages() {
    }

    /** Checks an upload is a decodable JPEG or PNG and re-encodes it (dropping EXIF data) with its thumbnail. */
    static Processed process(MultipartFile file) {
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
        try (InputStream in = file.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new BadRequestException(ErrorCode.INVALID_FILE, "Uploaded file is not a valid image");
            }
            // Re-encoding drops EXIF metadata (including GPS tags) - the sighting's location
            // is already captured explicitly on the log - and downscaling caps storage use.
            byte[] photo = encode(downscale(image, MAX_DIMENSION), extension.substring(1), JPEG_QUALITY);
            return new Processed(UUID.randomUUID().toString(), extension, contentType, photo, thumbnail(image));
        } catch (IOException e) {
            // Includes images ImageIO can open but not decode (e.g. CMYK JPEGs): a bad file, not a server fault.
            throw new BadRequestException(ErrorCode.INVALID_FILE, "Uploaded file could not be read as an image");
        }
    }

    /** Builds the thumbnail of an already stored photo; empty if the bytes are not a readable image. */
    static Optional<byte[]> thumbnailOf(byte[] storedPhoto) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(storedPhoto));
            return image == null ? Optional.empty() : Optional.of(thumbnail(image));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static byte[] thumbnail(BufferedImage image) throws IOException {
        return encode(downscale(image, THUMBNAIL_DIMENSION), "jpg", THUMBNAIL_QUALITY);
    }

    /** Returns the stored filename for one of our own upload URLs (relative or absolute); empty for anything else. */
    static Optional<String> storedFilename(String url) {
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

    /** Returns the thumbnail URL for one of our own photo URLs; empty for external URLs and for thumbnails themselves. */
    static Optional<String> thumbnailUrl(String photoUrl) {
        return storedFilename(photoUrl)
                .filter(name -> !name.endsWith(THUMBNAIL_SUFFIX) && name.contains("."))
                .map(name -> UPLOADS_PREFIX + baseOf(name) + THUMBNAIL_SUFFIX);
    }

    /** Returns the filename without its thumbnail suffix or extension, so a photo and its thumbnail share a key. */
    static String baseOf(String filename) {
        if (filename.endsWith(THUMBNAIL_SUFFIX)) {
            return filename.substring(0, filename.length() - THUMBNAIL_SUFFIX.length());
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }

    /** Returns the thumbnail's filename for a photo's filename. */
    static String thumbnailNameOf(String photoName) {
        return baseOf(photoName) + THUMBNAIL_SUFFIX;
    }

    /** Returns the image content type for a filename extension; null if it is not a served image type. */
    static String contentTypeForExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? null : CONTENT_TYPES_BY_EXTENSION.get(filename.substring(dot + 1).toLowerCase());
    }

    /** The extensions a stored photo can have, with the dot. */
    static Iterable<String> photoExtensions() {
        return IMAGE_EXTENSIONS.values();
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
