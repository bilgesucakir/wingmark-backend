package com.wingmark.backend.dto.badge;

import com.wingmark.backend.enums.BadgeTier;

import java.time.Instant;
import java.util.UUID;

/**
 * A badge with one user's progress and earned status. For a secret badge the user has not earned, only
 * {@code badgeId}, {@code secret}, {@code earned} and {@code tier} are set and every other field is null.
 *
 * @param badgeId          badge id; stays the same when a locked secret badge becomes earned
 * @param secret           whether this is a secret badge
 * @param earned           whether the user has earned it
 * @param earnedAt         when it was earned, or null
 * @param badgeName        name in the request language, or null for a locked secret badge
 * @param badgeIcon        icon key, or null for a locked secret badge
 * @param badgeDescription description in the request language, or null if none or locked
 * @param tier             tier, or null if the badge has none; shown even for a locked secret badge
 * @param progress         current progress, or null for a locked secret badge
 * @param targetValue      progress needed, or null for a locked secret badge
 */
public record UserBadgeResponseDto(
        UUID badgeId,
        boolean secret,
        boolean earned,
        Instant earnedAt,
        String badgeName,
        String badgeIcon,
        String badgeDescription,
        BadgeTier tier,
        Integer progress,
        Integer targetValue
) {
}
