package com.wingmark.backend.repository;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepositoryCustom {

    void revokeAllForUser(UUID userId, Instant revokedAt);
}
