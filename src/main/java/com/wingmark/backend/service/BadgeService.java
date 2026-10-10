package com.wingmark.backend.service;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.CreateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UpdateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UserBadgeResponseDto;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Manages the badge catalog and per-user badge progress/awards. */
public interface BadgeService {

    /** Returns the public badge catalog: every badge definition except secret ones. */
    List<BadgeResponseDto> getAll();

    /** Admin-only: returns every badge definition, secret ones included. */
    List<BadgeResponseDto> getAllForAdmin();

    /** Returns every badge with this user's current progress and earned status, with badgeName resolved to the given locale. */
    List<UserBadgeResponseDto> getByUserId(UUID userId, Locale locale);

    /** Admin-only: like {@link #getByUserId} but with full details for secret badges the user has not earned. */
    List<UserBadgeResponseDto> getByUserIdForAdmin(UUID userId, Locale locale);

    /** Admin-only: adds a new badge definition to the catalog. */
    BadgeResponseDto create(CreateBadgeRequestDto request);

    /** Admin-only: updates an existing badge definition. */
    BadgeResponseDto update(UUID badgeId, UpdateBadgeRequestDto request);

    /** Admin-only: removes a badge definition (and any users' progress toward it). */
    void delete(UUID badgeId);

    /** Recomputes progress for every badge against the user's current logs and awards any newly earned ones. */
    void evaluateForUser(UUID userId);

    /** Recomputes every badge for every user, e.g. after a progress rule changed. Returns the number of users processed. */
    int recomputeForAllUsers();
}
