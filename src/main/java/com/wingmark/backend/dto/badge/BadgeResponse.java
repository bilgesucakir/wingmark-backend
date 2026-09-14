package com.wingmark.backend.dto.badge;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;

import java.util.UUID;

public record BadgeResponse(
        UUID id,
        String name,
        String description,
        String icon,
        BadgeCriteriaType criteriaType,
        Integer criteriaValue,
        BadgeTier tier
) {
}
