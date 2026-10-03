package com.wingmark.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one number")
        String password,
        @NotBlank @Size(min = 3, max = 30) String username,
        String firstName,
        String lastName,
        // Exact versions the user ticked "I accept" for (see GET /api/legal). Required once
        // that document is published; ignored while it isn't.
        String acceptedTermsVersion,
        String acceptedPrivacyVersion
) {
}
