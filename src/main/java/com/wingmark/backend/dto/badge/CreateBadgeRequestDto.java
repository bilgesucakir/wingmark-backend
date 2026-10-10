package com.wingmark.backend.dto.badge;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Map;

/** name must include at least a non-blank "en" translation; description translations are optional. */
public record CreateBadgeRequestDto(
        @NotEmpty Map<String, String> name,
        Map<String, String> description,
        String icon,
        @NotNull BadgeCriteriaType criteriaType,
        @NotNull @Positive Integer criteriaValue,
        Map<String, Object> criteriaMetadata,
        BadgeTier tier,
        @PositiveOrZero Integer displayOrder,
        Boolean secret
) {

    /** Request without the secret flag (the badge is not secret). */
    public CreateBadgeRequestDto(Map<String, String> name, Map<String, String> description, String icon,
            BadgeCriteriaType criteriaType, Integer criteriaValue, Map<String, Object> criteriaMetadata,
            BadgeTier tier, Integer displayOrder) {
        this(name, description, icon, criteriaType, criteriaValue, criteriaMetadata, tier, displayOrder, null);
    }
}
