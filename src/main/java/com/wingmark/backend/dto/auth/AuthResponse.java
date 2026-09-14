package com.wingmark.backend.dto.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInMs
) {
}
