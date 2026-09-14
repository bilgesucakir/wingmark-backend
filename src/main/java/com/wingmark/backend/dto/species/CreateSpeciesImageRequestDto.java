package com.wingmark.backend.dto.species;

import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSpeciesImageRequestDto(
        @NotNull LifeStageImage lifeStage,
        @NotNull ImageGender gender,
        @NotBlank String imageUrl,
        String caption
) {
}
