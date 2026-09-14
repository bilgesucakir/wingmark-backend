package com.wingmark.backend.dto.badge;

import java.time.Instant;
import java.util.UUID;

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
