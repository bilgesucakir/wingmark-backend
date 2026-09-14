package com.wingmark.backend.service;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.CreateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UserBadgeResponseDto;

import java.util.List;
import java.util.UUID;

/** Manages the badge catalog and per-user badge progress/awards. */
public interface BadgeService {

    /** Returns every badge definition in the catalog. */
    List<BadgeResponseDto> getAll();

    /** Returns every badge with this user's current progress and earned status. */
    List<UserBadgeResponseDto> getByUserId(UUID userId);

    /** Admin-only: adds a new badge definition to the catalog. */
    BadgeResponseDto create(CreateBadgeRequestDto request);

    /** Admin-only: removes a badge definition (and any users' progress toward it). */
    void delete(UUID badgeId);

    /** Recomputes progress for every badge against the user's current logs and awards any newly earned ones. */
    void evaluateForUser(UUID userId);
}
