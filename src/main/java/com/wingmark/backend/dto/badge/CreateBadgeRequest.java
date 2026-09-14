package com.wingmark.backend.dto.badge;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBadgeRequest(
        @NotBlank String name,
        String description,
        String icon,
        @NotNull BadgeCriteriaType criteriaType,
        @NotNull @Positive Integer criteriaValue,
        String criteriaMetadata,
        BadgeTier tier
) {
}
