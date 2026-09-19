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

    private Instant expiresAt;

    private Instant usedAt;

    public boolean isActive() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}
