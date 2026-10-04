package com.wingmark.backend.service;

import com.wingmark.backend.enums.DeletionInitiator;

import java.util.UUID;

/** Permanently removes a user account together with everything that belongs to it. */
public interface AccountDeletionService {

    /** Deletes the user, their data and uploaded photos nobody else uses, records an audit entry without personal data and emails a confirmation. Callers check authorization. */
    void deleteAccount(UUID userId, DeletionInitiator initiatedBy);
}
