package com.wingmark.backend.dto.metrics;

import java.util.UUID;

/** Number of users who favorited a species. */
public record SpeciesFavoriteCountDto(
        UUID speciesId,
        String speciesName,
        long userCount
) {
}
