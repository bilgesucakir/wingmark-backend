package com.wingmark.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Request carrying a refresh token. */
public record RefreshTokenRequestDto(
        @NotBlank String refreshToken
) {
}
