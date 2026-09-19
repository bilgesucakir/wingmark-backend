package com.wingmark.backend.dto.badge;

import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;

import java.util.Map;
import java.util.UUID;

public record BadgeResponseDto(
        UUID id,
        Map<String, String> name,
        Map<String, String> description,
        String icon,
        BadgeCriteriaType criteriaType,
        Integer criteriaValue,
        Map<String, Object> criteriaMetadata,
        BadgeTier tier
) {
}
