package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for deleting accounts whose email address was never verified. Off by default; when switched on it only
 * logs until {@code dryRun} is set to false.
 *
 * @param enabled       whether the daily job runs at all
 * @param dryRun        true: only log which accounts would be deleted
 * @param olderThanDays age in days after which an unverified account is deleted (default 7)
 * @param maxPerRun     most accounts deleted per run, a safety net against a wrong setting
 * @param cron          when the job runs (Spring cron, UTC); default 04:15 every day
 */
@ConfigurationProperties(prefix = "wingmark.unverified-cleanup")
public record UnverifiedAccountProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("7") int olderThanDays,
        @DefaultValue("100") int maxPerRun,
        @DefaultValue("0 15 4 * * *") String cron
) {
}
