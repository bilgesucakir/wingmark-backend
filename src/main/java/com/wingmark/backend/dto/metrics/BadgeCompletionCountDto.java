package com.wingmark.backend.dto.metrics;

import java.util.UUID;

public record BadgeCompletionCountDto(
        UUID badgeId,
        String badgeName,
        long earnedCount
) {
}
