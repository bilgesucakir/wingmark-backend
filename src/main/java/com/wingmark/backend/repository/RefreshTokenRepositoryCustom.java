package com.wingmark.backend.repository;

import java.time.Instant;
import java.util.UUID;

/** Refresh token bulk operations. */
public interface RefreshTokenRepositoryCustom {

    /** Revokes all of the user's still-active refresh tokens at the given time. */
    void revokeAllForUser(UUID userId, Instant revokedAt);
}
