package com.wingmark.backend.dto.metrics;

import java.time.Instant;
import java.util.List;

public record AdminMetricsResponseDto(
        long totalUsers,
        List<SpeciesFavoriteCountDto> favoriteSpecies,
        List<BadgeCompletionCountDto> badgeCompletions,
        List<RegionLogCountDto> topRegions,
        List<LocaleUsageCountDto> localeUsage,
        Instant calculatedAt
) {
}
