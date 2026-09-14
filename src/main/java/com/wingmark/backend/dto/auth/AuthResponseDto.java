package com.wingmark.backend.dto.auth;

public record AuthResponseDto(
        String accessToken,
        String refreshToken,
        long expiresInMs
) {
}
