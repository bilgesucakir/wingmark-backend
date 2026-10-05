package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for deleting accounts that have been inactive for a long time. Switched off by default; even when
 * switched on it only logs what it would do until {@code dryRun} is set to false.
 *
 * @param enabled              whether the daily job runs at all
 * @param dryRun               true: only log which accounts would be warned or deleted
 * @param inactiveAfterDays    days without activity before an account is deleted (default 730, two years)
 * @param warnDaysBeforeDelete days before the deletion date that the warning email is sent (default 7)
 * @param maxWarningsPerRun    most warning emails per run, to respect the daily mail cap
 * @param maxDeletionsPerRun   most accounts deleted per run, a safety net against a wrong setting
 * @param cron                 when the job runs (Spring cron, UTC); default 03:30 every day
 */
@ConfigurationProperties(prefix = "wingmark.inactivity")
public record InactivityProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("730") int inactiveAfterDays,
        @DefaultValue("7") int warnDaysBeforeDelete,
        @DefaultValue("50") int maxWarningsPerRun,
        @DefaultValue("20") int maxDeletionsPerRun,
        @DefaultValue("0 30 3 * * *") String cron
) {
}
