package com.wingmark.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/** Fails the start-up with a clear message when the photo storage settings do not make sense, instead of failing on the first upload. */
@Configuration
@RequiredArgsConstructor
public class R2StorageConfig {

    private static final Set<String> STORAGE_MODES = Set.of("gridfs", "r2");

    private final UploadProperties uploadProperties;
    private final R2Properties r2Properties;
    private final R2MigrationProperties migrationProperties;

    /** Checks UPLOAD_STORAGE, the R2 settings it needs, and that the migration is only enabled with R2. */
    @PostConstruct
    void validate() {
        String storage = uploadProperties.storage();
        if (!STORAGE_MODES.contains(storage)) {
            throw new IllegalStateException("UPLOAD_STORAGE must be 'gridfs' or 'r2', not '" + storage + "'");
        }
        if ("r2".equals(storage)) {
            List<String> missing = r2Properties.missing();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("UPLOAD_STORAGE=r2 needs these settings: " + String.join(", ", missing));
            }
        } else if (migrationProperties.enabled()) {
            throw new IllegalStateException("R2_MIGRATION_ENABLED=true needs UPLOAD_STORAGE=r2");
        }
    }
}
