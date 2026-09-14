package com.wingmark.backend.dto.birdlog;

import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.enums.SpeciesStatus;
import com.wingmark.backend.enums.Visibility;

import java.time.Instant;
import java.util.UUID;

public record BirdLogResponseDto(
        UUID id,
        UUID userId,
        UUID speciesId,
        String speciesCommonName,
        SpeciesStatus speciesStatus,
        boolean pet,
        String customName,
        LifeStage lifeStage,
        Gender gender,
        String photoUrl,
        String note,
        Double latitude,
        Double longitude,
        String locationName,
        Instant observedAt,
        Visibility visibility,
        Instant createdAt
) {
}
