package com.wingmark.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to send a new verification link. */
public record ResendVerificationEmailRequestDto(
        @NotBlank @Email @Size(max = 254, message = "Email must be at most 254 characters") String email
) {
}
