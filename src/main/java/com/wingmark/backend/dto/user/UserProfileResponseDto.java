package com.wingmark.backend.dto.user;

import com.wingmark.backend.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponseDto(
        UUID id,
        String email,
        String username,
        String firstName,
        String lastName,
        String profilePicture,
        UUID favoriteSpeciesId,
        String favoriteSpeciesName,
        Role role,
        boolean emailVerified,
        Instant createdAt
) {
}
