package com.wingmark.backend.dto.auth;

import java.util.UUID;

/**
 * Returned by register instead of a token pair: the account can't be used until its email
 * is verified, after which the client logs in normally.
 */
public record RegisterResponseDto(
        UUID userId,
        String email,
        String username,
        boolean emailVerified,
        String message
) {
}
