package com.wingmark.backend.entity;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Hashed email-verification token with an expiry. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "email_verification_tokens")
public class EmailVerificationToken extends BaseEntity {

    private UUID userId;

    @Indexed(unique = true)
    private String tokenHash;

    /** TTL index: Mongo deletes the token once it has expired - an expired token can never be used again. */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    private Instant usedAt;

    public boolean isActive() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}
