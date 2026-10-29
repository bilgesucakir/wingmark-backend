package com.wingmark.backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Re-confirms the caller's password before their account and all its data are permanently deleted. */
public record DeleteAccountRequestDto(
        @NotBlank @Size(max = 72, message = "Password must be at most 72 characters") String password
) {
}
