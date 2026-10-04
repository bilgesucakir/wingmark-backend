package com.wingmark.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request to email a password-reset code. */
public record ForgotPasswordRequestDto(
        @NotBlank @Email String email
) {
}
