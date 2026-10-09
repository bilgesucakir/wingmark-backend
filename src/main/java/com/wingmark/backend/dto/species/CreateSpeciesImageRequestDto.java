package com.wingmark.backend.dto.species;

import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Admin request to attach a reference image to a species. */
public record CreateSpeciesImageRequestDto(
        @NotNull LifeStageImage lifeStage,
        @NotNull ImageGender gender,
        @NotBlank String imageUrl,
        String caption,
        // Set when attaching a third-party photo (e.g. a Wikimedia Commons file); null for your own uploads.
        String licenseCode,
        String attribution,
        String sourceUrl,
        // Page address of a Wikimedia Commons file; when set, licenseCode, attribution and sourceUrl are read from Commons and these three are ignored.
        String commonsFileUrl
) {
}
