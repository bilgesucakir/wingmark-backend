package com.wingmark.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email and password login request. */
public record LoginRequestDto(
        @NotBlank @Email @Size(max = 254, message = "Email must be at most 254 characters") String email,
        @NotBlank @Size(max = 72, message = "Password must be at most 72 characters") String password
) {
}
