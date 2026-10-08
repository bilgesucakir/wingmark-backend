package com.wingmark.backend.config;

import com.wingmark.backend.service.UploadMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the photo migration to R2 on a schedule; exists only when {@code R2_MIGRATION_ENABLED=true}. */
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wingmark.r2-migration.enabled", havingValue = "true")
public class R2MigrationJob {

    private final UploadMigrationService uploadMigrationService;

    /** Copies the next batch of photos, once per schedule tick. */
    @Scheduled(cron = "${wingmark.r2-migration.cron}", zone = "UTC")
    public void run() {
        uploadMigrationService.migrate();
    }
}
