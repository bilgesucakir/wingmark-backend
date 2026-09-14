package com.wingmark.backend.dto.badge;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public record CreateBadgeRequestDto(
        @NotBlank String name,
        String description,
        String icon,
        @NotNull BadgeCriteriaType criteriaType,
        @NotNull @Positive Integer criteriaValue,
        Map<String, Object> criteriaMetadata,
        BadgeTier tier
) {
}
