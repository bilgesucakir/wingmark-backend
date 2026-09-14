package com.wingmark.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    private String deviceInfo;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
