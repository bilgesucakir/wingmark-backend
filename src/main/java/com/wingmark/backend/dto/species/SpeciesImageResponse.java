package com.wingmark.backend.dto.species;

import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;

import java.util.UUID;

public record SpeciesImageResponse(
        UUID id,
        LifeStageImage lifeStage,
        ImageGender gender,
        String imageUrl,
        String caption
) {
}
