package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.CreateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UpdateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UserBadgeResponseDto;
import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserBadge;
import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.service.BadgeService;
import com.wingmark.backend.util.GeoUtils;
import com.wingmark.backend.util.LocalizedTextResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Badge catalog and per-user badge progress. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeServiceImpl implements BadgeService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BirdLogRepository birdLogRepository;
    private final SpeciesRepository speciesRepository;
    private final UserRepository userRepository;

    @Override
    public List<BadgeResponseDto> getAll() {
        return sortedBadges().stream().map(this::toResponse).toList();
    }

    @Override
    public List<UserBadgeResponseDto> getByUserId(UUID userId, Locale locale) {
        boolean hasFavoriteSpecies = favoriteSpeciesId(userId) != null;
        List<Badge> badges = sortedBadges().stream()
                .filter(badge -> hasFavoriteSpecies || badge.getCriteriaType() != BadgeCriteriaType.FAVORITE_SPECIES_LOGS)
                .toList();
        Map<UUID, UserBadge> earned = userBadgeRepository.findByUserId(userId).stream()
                .collect(java.util.stream.Collectors.toMap(UserBadge::getBadgeId, ub -> ub));

        return badges.stream()
                .map(badge -> {
                    UserBadge userBadge = earned.get(badge.getId());
                    int progress = userBadge != null ? userBadge.getProgress() : 0;
                    boolean isEarned = userBadge != null && userBadge.getEarnedAt() != null;
                    return new UserBadgeResponseDto(
                            badge.getId(),
                            LocalizedTextResolver.resolve(badge.getName(), locale),
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
    public BadgeResponseDto create(CreateBadgeRequestDto request) {
        validateName(request.name());
        validateCriteria(request.criteriaType(), request.criteriaMetadata());

        Badge badge = Badge.builder()
                .name(request.name())
                .description(request.description())
                .icon(request.icon())
                .criteriaType(request.criteriaType())
                .criteriaValue(request.criteriaValue())
                .criteriaMetadata(request.criteriaMetadata())
                .tier(request.tier())
                .displayOrder(request.displayOrder())
                .build();
        return toResponse(badgeRepository.save(badge));
    }

    @Override
    public BadgeResponseDto update(UUID badgeId, UpdateBadgeRequestDto request) {
        validateName(request.name());
        validateCriteria(request.criteriaType(), request.criteriaMetadata());

        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> ResourceNotFoundException.of("Badge", badgeId));
        badge.setName(request.name());
        badge.setDescription(request.description());
        badge.setIcon(request.icon());
        badge.setCriteriaType(request.criteriaType());
        badge.setCriteriaValue(request.criteriaValue());
        badge.setCriteriaMetadata(request.criteriaMetadata());
        badge.setTier(request.tier());
        badge.setDisplayOrder(request.displayOrder());
        return toResponse(badgeRepository.save(badge));
    }

    @Override
    public void delete(UUID badgeId) {
        if (!badgeRepository.existsById(badgeId)) {
            throw ResourceNotFoundException.of("Badge", badgeId);
        }
        badgeRepository.deleteById(badgeId);
    }

    @Override
    public void evaluateForUser(UUID userId) {
        List<Badge> badges = badgeRepository.findAll();
        for (Badge badge : badges) {
            int progress = computeProgress(userId, badge);
            upsertUserBadge(userId, badge, progress);
        }
    }

    /** Returns all badges by {@code displayOrder}, lowest first. Badges without one come last, in stored order. */
    private List<Badge> sortedBadges() {
        List<Badge> badges = new ArrayList<>(badgeRepository.findAll());
        badges.sort(Comparator.comparing(Badge::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        return badges;
    }

    private void validateName(Map<String, String> name) {
        if (!StringUtils.hasText(name.get("en"))) {
            throw new IllegalArgumentException("name must include a non-blank 'en' translation");
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
            case SIGHTINGS_IN_RADIUS -> computeMaxSightingsInRadius(userId, badge);
            case SPECIES_LOGS -> computeSpeciesLogs(userId, badge);
            case SAME_GENUS_SPECIES -> computeSameGenusSpecies(userId, badge);
            case FAVORITE_SPECIES_LOGS -> computeFavoriteSpeciesLogs(userId);
        };
    }

    /**
     * Returns the largest number of distinct species the user logged within one genus (the first word of the
     * scientific name, case-insensitive), or within {@code criteriaMetadata.genus} when set. Pet logs and logs
     * without a species are ignored.
     */
    private int computeSameGenusSpecies(UUID userId, Badge badge) {
        Set<UUID> speciesIds = new HashSet<>();
        birdLogRepository.findByUserIdAndSpeciesIdIsNotNullAndPetFalse(userId)
                .forEach(birdLog -> speciesIds.add(birdLog.getSpeciesId()));
        if (speciesIds.isEmpty()) {
            return 0;
        }

        Map<String, Set<UUID>> speciesByGenus = new HashMap<>();
        for (Species species : speciesRepository.findAllById(speciesIds)) {
            String genus = genusOf(species.getScientificName());
            if (genus != null) {
                speciesByGenus.computeIfAbsent(genus, key -> new HashSet<>()).add(species.getId());
            }
        }

        String requestedGenus = genusOf(extractGenus(badge.getCriteriaMetadata()));
        if (requestedGenus != null) {
            return speciesByGenus.getOrDefault(requestedGenus, Set.of()).size();
        }
        return speciesByGenus.values().stream().mapToInt(Set::size).max().orElse(0);
    }

    /** Returns the lower-cased first word of a scientific name, or null if it is blank. */
    private static String genusOf(String scientificName) {
        if (!StringUtils.hasText(scientificName)) {
            return null;
        }
        return scientificName.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    private static String extractGenus(Map<String, Object> criteriaMetadata) {
        return criteriaMetadata != null && criteriaMetadata.get("genus") instanceof String genus ? genus : null;
    }

    /** Rejects a {@code SAME_GENUS_SPECIES} badge whose {@code criteriaMetadata.genus}, when given, is not a non-blank string. */
    private static void validateCriteria(BadgeCriteriaType type, Map<String, Object> criteriaMetadata) {
        if (type == BadgeCriteriaType.SAME_GENUS_SPECIES && criteriaMetadata != null
                && criteriaMetadata.containsKey("genus") && !StringUtils.hasText(extractGenus(criteriaMetadata))) {
            throw new IllegalArgumentException("criteriaMetadata.genus must be a non-blank string when given");
        }
    }

    /** Returns the number of logs of the user's favorite species, 0 if they have none. */
    private int computeFavoriteSpeciesLogs(UUID userId) {
        UUID favoriteSpeciesId = favoriteSpeciesId(userId);
        return favoriteSpeciesId == null ? 0 : (int) birdLogRepository.countByUserIdAndSpeciesId(userId, favoriteSpeciesId);
    }

    /** Returns the user's favorite species id, or null if they have none or that species no longer exists. */
    private UUID favoriteSpeciesId(UUID userId) {
        UUID favoriteSpeciesId = userRepository.findById(userId).map(User::getFavoriteSpeciesId).orElse(null);
        return favoriteSpeciesId != null && speciesRepository.existsById(favoriteSpeciesId) ? favoriteSpeciesId : null;
    }

    private int computeSpeciesLogs(UUID userId, Badge badge) {
        UUID speciesId = extractSpeciesId(badge.getCriteriaMetadata());
        if (speciesId == null) {
            log.warn("Badge {} is SPECIES_LOGS but criteriaMetadata.speciesId is missing/invalid; progress stays 0", badge.getId());
            return 0;
        }
        return (int) birdLogRepository.countByUserIdAndSpeciesId(userId, speciesId);
    }

    private UUID extractSpeciesId(Map<String, Object> criteriaMetadata) {
        if (criteriaMetadata == null) {
            return null;
        }
        Object speciesId = criteriaMetadata.get("speciesId");
        if (speciesId == null) {
            return null;
        }
        try {
            return UUID.fromString(speciesId.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    /** Returns the size of the densest cluster of sightings, counting raw sightings rather than distinct species. Every log is tried as a cluster center. Progress can drop but a badge once earned is never revoked. */
    private int computeMaxSightingsInRadius(UUID userId, Badge badge) {
        double radiusMeters = extractRadiusMeters(badge.getCriteriaMetadata());
        List<BirdLog> logs = birdLogRepository.findByUserIdAndPetFalse(userId);

        int maxSightings = 0;
        for (BirdLog center : logs) {
            long sightingsInRange = logs.stream()
                    .filter(other -> GeoUtils.distanceMeters(
                            center.getLatitude(), center.getLongitude(),
                            other.getLatitude(), other.getLongitude()) <= radiusMeters)
                    .count();
            maxSightings = Math.max(maxSightings, (int) sightingsInRange);
        }
        return maxSightings;
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
                badge.getCriteriaMetadata(),
                badge.getTier(),
                badge.getDisplayOrder()
        );
    }
}
