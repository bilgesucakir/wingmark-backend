package com.wingmark.backend.dto.metrics;

import java.util.UUID;

public record SpeciesFavoriteCountDto(
        UUID speciesId,
        String speciesName,
        long userCount
) {
}
