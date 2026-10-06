package com.wingmark.backend.service;

import java.time.Instant;

/** Deletes accounts that never verified their email address. */
public interface UnverifiedAccountService {

    /** What one run did; in dry-run mode the count is what it would have deleted. */
    record Result(int deleted, boolean dryRun) {
    }

    /**
     * Deletes unverified, never-logged-in accounts whose verification email was accepted by the mail server more than
     * the configured number of days ago, without sending any email. Accounts that never had a verification email
     * accepted are kept, so a signup that never got its mail is not lost. Admins are skipped.
     */
    Result purge(Instant now);
}
