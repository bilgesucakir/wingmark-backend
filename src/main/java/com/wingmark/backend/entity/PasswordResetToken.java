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
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    public boolean isActive() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}
