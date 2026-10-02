package com.wingmark.backend.dto.auth;

import com.wingmark.backend.enums.ConsentType;

import java.util.List;

public record AuthResponseDto(
        String accessToken,
        String refreshToken,
        long expiresInMs,
        // Legal documents whose current version this user still has to accept - show the
        // acceptance screen when non-empty. Empty while nothing is published or all accepted.
        List<ConsentType> pendingConsents
) {
}
