package com.wingmark.backend.service;

import java.util.UUID;

/** Permanently removes a user account together with everything that belongs to it. */
public interface AccountDeletionService {

    /**
     * Deletes the user and all their data: bird logs, badge progress, settings, refresh /
     * password-reset / email-verification tokens, and uploaded photos no one else references.
     * The caller is responsible for authorization checks.
     */
    void deleteAccount(UUID userId);
}
