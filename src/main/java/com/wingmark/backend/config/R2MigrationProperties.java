package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for copying photos from the database (GridFS) to R2. Off by default; when switched on it only reports
 * until {@code dryRun} is set to false. It never deletes anything from the database.
 *
 * @param enabled   whether the job runs at all (needs {@code UPLOAD_STORAGE=r2})
 * @param dryRun    true: only report how many files and bytes it would copy
 * @param maxPerRun most files copied per run, which bounds the number of R2 requests
 * @param cron      when the job runs (Spring cron, UTC); default every hour at minute 20
 */
@ConfigurationProperties(prefix = "wingmark.r2-migration")
public record R2MigrationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("50") int maxPerRun,
        @DefaultValue("0 20 * * * *") String cron
) {
}
