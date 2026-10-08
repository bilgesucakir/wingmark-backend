package com.wingmark.backend.config;

import com.wingmark.backend.service.OrphanedUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Runs the unreferenced-photo cleanup on a schedule; exists only when {@code UPLOAD_CLEANUP_ENABLED=true}. */
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wingmark.upload-cleanup.enabled", havingValue = "true")
public class UploadCleanupJob {

    private final OrphanedUploadService orphanedUploadService;

    /** Deletes unreferenced photos, once per schedule tick. */
    @Scheduled(cron = "${wingmark.upload-cleanup.cron}", zone = "UTC")
    public void run() {
        orphanedUploadService.cleanup(Instant.now());
    }
}
