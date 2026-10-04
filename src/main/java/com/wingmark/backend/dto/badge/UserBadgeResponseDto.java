package com.wingmark.backend.dto.badge;

import java.time.Instant;
import java.util.UUID;

/** A badge with one user's progress and earned status. */
public record UserBadgeResponseDto(
        UUID badgeId,
        String badgeName,
        String badgeIcon,
        boolean earned,
        Instant earnedAt,
        int progress,
        int targetValue
) {
}
