package com.wingmark.backend.entity;

import com.wingmark.backend.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** A user account. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "users")
public class User extends BaseEntity {

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    @Indexed(unique = true)
    private String username;

    private String firstName;

    private String lastName;

    /** Either a preset-avatar identifier, or a path/URL to an uploaded picture. */
    private String profilePicture;

    private UUID favoriteSpeciesId;

    @Builder.Default
    private Role role = Role.USER;

    @Builder.Default
    private boolean emailVerified = false;

    /** Last login or token refresh (refresh updates it at most once a day); used to find inactive accounts. */
    private Instant lastLoginAt;

    /** When a verification email was last accepted by the mail server, or null if none ever was; the abandoned-signup cleanup counts from it. */
    private Instant verificationEmailSentAt;

    /** When the inactivity warning email was sent for the current period of inactivity, or null. */
    private Instant inactivityWarningSentAt;

    /**
     * Token version stamped into access tokens as the {@code tv} claim.
     * Tokens with a lower value are rejected; it is bumped on password change, reset and logout-all.
     * Null counts as 0.
     */
    private Integer tokenVersion;

    public int currentTokenVersion() {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    public void invalidateIssuedTokens() {
        tokenVersion = currentTokenVersion() + 1;
    }
}
