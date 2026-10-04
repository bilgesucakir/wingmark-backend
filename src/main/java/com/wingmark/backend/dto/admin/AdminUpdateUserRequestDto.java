package com.wingmark.backend.dto.admin;

import com.wingmark.backend.enums.Role;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Admin request to replace a user's name, role, verified flag, favorite species and picture. */
public record AdminUpdateUserRequestDto(
        String firstName,
        String lastName,
        @NotNull Role role,
        boolean emailVerified,
        UUID favoriteSpeciesId,
        String profilePicture
) {
}
