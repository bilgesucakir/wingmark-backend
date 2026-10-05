package com.wingmark.backend.service;

import java.time.Instant;

/** Warns and then deletes accounts with no activity for a long time. */
public interface InactiveAccountService {

    /** What one run did. In dry-run mode the counts are what it would have done. */
    record Result(int warned, int deleted, boolean dryRun) {
    }

    /**
     * Sends the warning email to accounts that reach the inactivity limit within the warning period, and deletes
     * accounts that passed the limit at least the warning period after being warned. Activity is a login, a token
     * refresh or a created or changed bird log. Admins are never touched.
     */
    Result runCleanup(Instant now);
}
