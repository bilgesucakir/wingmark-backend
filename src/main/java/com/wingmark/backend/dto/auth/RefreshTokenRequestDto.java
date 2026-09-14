package com.wingmark.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDto(
        @NotBlank String refreshToken
) {
}
