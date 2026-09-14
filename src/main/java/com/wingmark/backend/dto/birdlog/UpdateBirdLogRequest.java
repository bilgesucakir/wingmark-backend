package com.wingmark.backend.dto.birdlog;

import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.enums.SpeciesStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateBirdLogRequest(
        UUID speciesId,
        SpeciesStatus speciesStatus,
        boolean pet,
        String customName,
        @NotNull LifeStage lifeStage,
        @NotNull Gender gender,
        String photoUrl,
        String note,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        String locationName
) {
}
