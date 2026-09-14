package com.wingmark.backend.dto.user;

import java.util.UUID;

public record UpdateProfileRequestDto(
        String firstName,
        String lastName,
        String profilePicture,
        UUID favoriteSpeciesId
) {
}
