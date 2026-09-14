package com.wingmark.backend.dto.species;

public record PhotoCandidateDto(
        String observationId,
        String photoUrl,
        String licenseCode,
        String attribution,
        String observationUrl
) {
}
