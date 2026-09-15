package com.wingmark.backend.dto.admin;

import com.wingmark.backend.enums.Role;
import jakarta.validation.constraints.NotNull;

public record AdminUpdateUserRequestDto(
        String firstName,
        String lastName,
        @NotNull Role role,
        boolean emailVerified
) {
}
