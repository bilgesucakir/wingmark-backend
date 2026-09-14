package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.CreateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UserBadgeResponseDto;
import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.UserBadge;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import com.wingmark.backend.service.BadgeService;
import com.wingmark.backend.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeServiceImpl implements BadgeService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BirdLogRepository birdLogRepository;

    @Override
    public List<BadgeResponseDto> getAll() {
        return badgeRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<UserBadgeResponseDto> getByUserId(UUID userId) {
        List<Badge> badges = badgeRepository.findAll();
        Map<UUID, UserBadge> earned = userBadgeRepository.findByUserId(userId).stream()
                .collect(java.util.stream.Collectors.toMap(UserBadge::getBadgeId, ub -> ub));

        return badges.stream()
                .map(badge -> {
                    UserBadge userBadge = earned.get(badge.getId());
                    int progress = userBadge != null ? userBadge.getProgress() : 0;
                    boolean isEarned = userBadge != null && userBadge.getEarnedAt() != null;
                    return new UserBadgeResponseDto(
                            badge.getId(),
                            badge.getName(),
                            badge.getIcon(),
                            isEarned,
                            isEarned ? userBadge.getEarnedAt() : null,
                            progress,
                            badge.getCriteriaValue()
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public BadgeResponseDto create(CreateBadgeRequestDto request) {
        Badge badge = Badge.builder()
                .name(request.name())
                .description(request.description())
                .icon(request.icon())
                .criteriaType(request.criteriaType())
                .criteriaValue(request.criteriaValue())
                .criteriaMetadata(request.criteriaMetadata())
                .tier(request.tier())
                .build();
        return toResponse(badgeRepository.save(badge));
    }

    @Override
    @Transactional
    public void delete(UUID badgeId) {
        if (!badgeRepository.existsById(badgeId)) {
            throw ResourceNotFoundException.of("Badge", badgeId);
        }
        badgeRepository.deleteById(badgeId);
    }

    @Override
    @Transactional
    public void evaluateForUser(UUID userId) {
        List<Badge> badges = badgeRepository.findAll();
        for (Badge badge : badges) {
            int progress = computeProgress(userId, badge);
            upsertUserBadge(userId, badge, progress);
        }
    }

    private int computeProgress(UUID userId, Badge badge) {
        return switch (badge.getCriteriaType()) {
            case TOTAL_LOGS -> (int) birdLogRepository.countByUserId(userId);
            case UNIQUE_SPECIES -> (int) birdLogRepository.countDistinctSpeciesByUserId(userId);
            case BABY_LOGS -> (int) birdLogRepository.countByUserIdAndLifeStage(userId, LifeStage.BABY);
            case UNKNOWN_SPECIES_LOGS -> (int) birdLogRepository.countByUserIdAndSpeciesIdIsNull(userId);
            case PET_LOGS -> (int) birdLogRepository.countByUserIdAndPetTrue(userId);
            case SPECIES_IN_RADIUS -> computeMaxSpeciesInRadius(userId, badge);
        };
    }

    private int computeMaxSpeciesInRadius(UUID userId, Badge badge) {
        double radiusMeters = extractRadiusMeters(badge.getCriteriaMetadata());
        List<BirdLog> logs = birdLogRepository.findByUserIdAndSpeciesIdIsNotNullAndPetFalse(userId);

        int maxDistinctSpecies = 0;
        for (BirdLog center : logs) {
            long distinctSpecies = logs.stream()
                    .filter(other -> GeoUtils.distanceMeters(
                            center.getLatitude(), center.getLongitude(),
                            other.getLatitude(), other.getLongitude()) <= radiusMeters)
                    .map(BirdLog::getSpeciesId)
                    .distinct()
                    .count();
            maxDistinctSpecies = Math.max(maxDistinctSpecies, (int) distinctSpecies);
        }
        return maxDistinctSpecies;
    }

    private double extractRadiusMeters(Map<String, Object> criteriaMetadata) {
        if (criteriaMetadata == null) {
            return 5000;
        }
        Object radius = criteriaMetadata.get("radiusMeters");
        if (radius instanceof Number number) {
            return number.doubleValue();
        }
        if (radius != null) {
            log.warn("Badge criteriaMetadata.radiusMeters was not numeric ({}), defaulting to 5000m radius", radius);
        }
        return 5000;
    }

    private void upsertUserBadge(UUID userId, Badge badge, int progress) {
        UserBadge userBadge = userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())
                .orElseGet(() -> UserBadge.builder()
                        .userId(userId)
                        .badgeId(badge.getId())
                        .progress(0)
                        .build());

        userBadge.setProgress(progress);
        if (userBadge.getEarnedAt() == null && progress >= badge.getCriteriaValue()) {
            userBadge.setEarnedAt(Instant.now());
        }
        userBadgeRepository.save(userBadge);
    }

    private BadgeResponseDto toResponse(Badge badge) {
        return new BadgeResponseDto(
                badge.getId(),
                badge.getName(),
                badge.getDescription(),
                badge.getIcon(),
                badge.getCriteriaType(),
                badge.getCriteriaValue(),
                badge.getTier()
        );
    }
}
