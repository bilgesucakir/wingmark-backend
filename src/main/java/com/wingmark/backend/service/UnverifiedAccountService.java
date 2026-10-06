package com.wingmark.backend.service;

import java.time.Instant;

/** Deletes accounts that never verified their email address. */
public interface UnverifiedAccountService {

    /** What one run did; in dry-run mode the count is what it would have deleted. */
    record Result(int deleted, boolean dryRun) {
    }

    /** Deletes unverified, never-logged-in accounts older than the configured age, without sending any email. Admins are skipped. */
    Result purge(Instant now);
}
