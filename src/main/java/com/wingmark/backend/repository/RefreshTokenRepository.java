package com.wingmark.backend.repository;

import com.wingmark.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :revokedAt where r.userId = :userId and r.revokedAt is null")
    void revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
