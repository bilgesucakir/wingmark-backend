package com.wingmark.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Email and password login request. */
public record LoginRequestDto(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
