package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for deleting stored photos that nothing refers to any more. Off by default; when switched on it only logs
 * until {@code dryRun} is set to false.
 *
 * @param enabled   whether the daily job runs at all
 * @param dryRun    true: only log which files would be deleted
 * @param graceDays files younger than this are never deleted, because a photo is uploaded before the log that uses it is saved
 * @param maxPerRun most photos deleted per run, a safety net against a wrong setting
 * @param cron      when the job runs (Spring cron, UTC); default 04:45 every day
 */
@ConfigurationProperties(prefix = "wingmark.upload-cleanup")
public record UploadCleanupProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("7") int graceDays,
        @DefaultValue("100") int maxPerRun,
        @DefaultValue("0 45 4 * * *") String cron
) {
}
