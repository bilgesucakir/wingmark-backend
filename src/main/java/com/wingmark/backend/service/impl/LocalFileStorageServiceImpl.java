package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.StorageProperties;
import com.wingmark.backend.exception.FileStorageException;
import com.wingmark.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final StorageProperties storageProperties;

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Uploaded file is empty");
        }

        String contentType = file.getContentType();
        String extension = resolveExtension(contentType, file.getOriginalFilename());
        String filename = UUID.randomUUID() + extension;

        try {
            Path uploadDir = Path.of(storageProperties.uploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(filename).normalize();

            if (!target.getParent().equals(uploadDir)) {
                throw new FileStorageException("Invalid file path");
            }

            if (contentType != null && IMAGE_CONTENT_TYPES.contains(contentType)) {
                // Re-encoding via ImageIO drops EXIF metadata (including GPS tags),
                // since the sighting's location is already captured explicitly on the log.
                storeStrippedImage(file, target, extension);
            } else {
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/" + filename;
        } catch (IOException e) {
            log.error("Failed to store uploaded file", e);
            throw new FileStorageException("Failed to store uploaded file", e);
        }
    }

    private void storeStrippedImage(MultipartFile file, Path target, String extension) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new FileStorageException("Uploaded file is not a valid image");
            }
            String formatName = extension.replace(".", "");
            if (!ImageIO.write(image, formatName, target.toFile())) {
                throw new FileStorageException("Unsupported image format: " + formatName);
            }
        }
    }

    private String resolveExtension(String contentType, String originalFilename) {
        if (contentType != null) {
            return switch (contentType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> extensionFromFilename(originalFilename);
            };
        }
        return extensionFromFilename(originalFilename);
    }

    private String extensionFromFilename(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        return "";
    }
}
