package com.wingmark.backend.dto.user;

import java.util.UUID;

/** Request to update the caller's profile. */
public record UpdateProfileRequestDto(
        String firstName,
        String lastName,
        String profilePicture,
        UUID favoriteSpeciesId
) {
}
