package com.wingmark.backend.config;

import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.impl.GridFsFileStorageServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Uploads used to live on a local disk (UPLOAD_DIR - /data/uploads on Render). On startup,
 * copies any image there that isn't in GridFS yet, keeping its filename so every stored
 * /uploads/... URL keeps resolving. Idempotent (already-imported files are skipped), and a
 * no-op when the directory doesn't exist - e.g. on a laptop, or once the old disk is removed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyUploadImporter implements ApplicationRunner {

    private final StorageProperties storageProperties;
    private final GridFsFileStorageServiceImpl fileStorageService;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(storageProperties.uploadDir())) {
            return;
        }
        Path dir = Path.of(storageProperties.uploadDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            log.debug("Legacy upload dir {} not present - nothing to import", dir);
            return;
        }

        List<Path> files;
        try (Stream<Path> listing = Files.list(dir)) {
            files = listing.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            log.warn("Could not list legacy upload dir {} - skipping import", dir, e);
            return;
        }

        int imported = 0;
        int skipped = 0;
        for (Path path : files) {
            String filename = path.getFileName().toString();
            String contentType = GridFsFileStorageServiceImpl.contentTypeForExtension(filename);
            if (contentType == null || fileStorageService.exists(filename)) {
                skipped++;
                continue;
            }
            try {
                fileStorageService.saveRaw(filename, Files.readAllBytes(path), contentType);
                imported++;
            } catch (IOException | RuntimeException e) {
                log.error("Failed to import legacy upload {}", path, e);
            }
        }
        log.info("Legacy upload import from {}: {} imported into GridFS, {} skipped (already there or not an image)",
                dir, imported, skipped);
    }
}
