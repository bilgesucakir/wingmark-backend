package com.wingmark.backend.dto.admin;

import com.wingmark.backend.enums.Role;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminUpdateUserRequestDto(
        String firstName,
        String lastName,
        @NotNull Role role,
        boolean emailVerified,
        UUID favoriteSpeciesId
) {
}
