package com.wingmark.backend.dto.metrics;

import java.util.UUID;

/** Number of users who earned a badge. */
public record BadgeCompletionCountDto(
        UUID badgeId,
        String badgeName,
        long earnedCount
) {
}
