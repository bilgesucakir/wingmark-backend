package com.wingmark.backend.dto.species;

/** A candidate photo from iNaturalist with its license and attribution. */
public record PhotoCandidateDto(
        String observationId,
        String photoUrl,
        String licenseCode,
        String attribution,
        String observationUrl
) {
}
