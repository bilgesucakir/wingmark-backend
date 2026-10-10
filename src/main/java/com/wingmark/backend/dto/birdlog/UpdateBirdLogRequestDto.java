package com.wingmark.backend.dto.birdlog;

import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.enums.SpeciesStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Request to update a bird log. */
public record UpdateBirdLogRequestDto(
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
        String locationName,
        Instant observedAt,
        @Min(-840) @Max(840) Integer utcOffsetMinutes
) {

    /** Request without the UTC offset. */
    public UpdateBirdLogRequestDto(UUID speciesId, SpeciesStatus speciesStatus, boolean pet, String customName,
            LifeStage lifeStage, Gender gender, String photoUrl, String note, Double latitude, Double longitude,
            String locationName, Instant observedAt) {
        this(speciesId, speciesStatus, pet, customName, lifeStage, gender, photoUrl, note, latitude, longitude,
                locationName, observedAt, null);
    }
}
