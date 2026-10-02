package com.wingmark.backend.service;

import com.wingmark.backend.enums.DeletionInitiator;

import java.util.UUID;

/** Permanently removes a user account together with everything that belongs to it. */
public interface AccountDeletionService {

    /**
     * Deletes the user and all their data: bird logs, badge progress, settings, consent
     * records, refresh / password-reset / email-verification tokens, and uploaded photos no
     * one else references. Then records a personal-data-free audit entry and emails the
     * former account holder a confirmation. The caller is responsible for authorization checks.
     */
    void deleteAccount(UUID userId, DeletionInitiator initiatedBy);
}
