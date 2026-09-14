package com.wingmark.backend.service;

import com.wingmark.backend.dto.badge.BadgeResponse;
import com.wingmark.backend.dto.badge.CreateBadgeRequest;
import com.wingmark.backend.dto.badge.UserBadgeResponse;

import java.util.List;
import java.util.UUID;

public interface BadgeService {

    List<BadgeResponse> listCatalog();

    List<UserBadgeResponse> listForUser(UUID userId);

    BadgeResponse create(CreateBadgeRequest request);

    void delete(UUID badgeId);

    /** Recomputes progress for every badge against the user's current logs and awards any newly earned ones. */
    void evaluateForUser(UUID userId);
}
