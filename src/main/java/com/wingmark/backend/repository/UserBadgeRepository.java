package com.wingmark.backend.repository;

import com.wingmark.backend.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    List<UserBadge> findByUserId(UUID userId);

    Optional<UserBadge> findByUserIdAndBadgeId(UUID userId, UUID badgeId);
}
