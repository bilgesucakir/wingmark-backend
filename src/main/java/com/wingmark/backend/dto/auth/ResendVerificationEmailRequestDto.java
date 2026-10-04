package com.wingmark.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request to send a new verification link. */
public record ResendVerificationEmailRequestDto(
        @NotBlank @Email String email
) {
}
