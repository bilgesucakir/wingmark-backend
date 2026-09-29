package com.wingmark.backend.dto.user;

import jakarta.validation.constraints.NotBlank;

/** Re-confirms the caller's password before their account and all its data are permanently deleted. */
public record DeleteAccountRequestDto(
        @NotBlank String password
) {
}
