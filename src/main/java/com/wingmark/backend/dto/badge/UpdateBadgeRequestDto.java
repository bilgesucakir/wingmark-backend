package com.wingmark.backend.dto.badge;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/** name must include at least a non-blank "en" translation; description translations are optional. */
public record UpdateBadgeRequestDto(
        @NotEmpty Map<String, String> name,
        Map<String, String> description,
        String icon,
        @NotNull BadgeCriteriaType criteriaType,
        @NotNull @Positive Integer criteriaValue,
        Map<String, Object> criteriaMetadata,
        BadgeTier tier
) {
}
