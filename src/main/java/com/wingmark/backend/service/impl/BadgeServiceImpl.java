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
import com.wingmark.backend.entity.UserSettings;
import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.service.EmailService;
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

    private static final int EARLY_BIRD_FROM_HOUR = 4;
    private static final int EARLY_BIRD_TO_HOUR = 6;
    private static final Set<String> RARE_STATUSES = Set.of("endangered", "critically endangered");

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BirdLogRepository birdLogRepository;
    private final SpeciesRepository speciesRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EmailService emailService;

    @Override
    public List<BadgeResponseDto> getAll() {
        return sortedBadges().stream().filter(badge -> !badge.isSecret()).map(this::toResponse).toList();
    }

    @Override
    public List<BadgeResponseDto> getAllForAdmin() {
        return sortedBadges().stream().map(this::toResponse).toList();
    }

    @Override
    public List<UserBadgeResponseDto> getByUserId(UUID userId, Locale locale) {
        return userBadges(userId, locale, false);
    }

    @Override
    public List<UserBadgeResponseDto> getByUserIdForAdmin(UUID userId, Locale locale) {
        return userBadges(userId, locale, true);
    }

    /**
     * Builds the per-user badge list. A secret badge the user has not earned is reduced to its id, the secret flag,
     * earned = false and its tier, unless {@code revealSecrets} is set (admin views).
     */
    private List<UserBadgeResponseDto> userBadges(UUID userId, Locale locale, boolean revealSecrets) {
        List<Badge> all = sortedBadges();
        Map<UUID, UserBadge> userBadges = userBadgeRepository.findByUserId(userId).stream()
                .collect(java.util.stream.Collectors.toMap(UserBadge::getBadgeId, ub -> ub, (a, b) -> a));
        Set<UUID> earnedIds = earnedBadgeIds(userBadges);
        boolean hasFavoriteSpecies = favoriteSpeciesId(userId) != null;
        List<Badge> visible = all.stream().filter(badge -> isCountable(badge, hasFavoriteSpecies, earnedIds)).toList();
        int othersTarget = (int) visible.stream().filter(badge -> badge.getCriteriaType() != BadgeCriteriaType.ALL_OTHER_BADGES).count();

        return visible.stream()
                .map(badge -> {
                    UserBadge userBadge = userBadges.get(badge.getId());
                    boolean isEarned = userBadge != null && userBadge.getEarnedAt() != null;
                    if (badge.isSecret() && !isEarned && !revealSecrets) {
                        return new UserBadgeResponseDto(badge.getId(), true, false, null, null, null, null, badge.getTier(), null, null);
                    }
                    int progress = userBadge != null ? userBadge.getProgress() : 0;
                    int target = badge.getCriteriaType() == BadgeCriteriaType.ALL_OTHER_BADGES ? othersTarget : badge.getCriteriaValue();
                    return new UserBadgeResponseDto(
                            badge.getId(),
                            badge.isSecret(),
                            isEarned,
                            isEarned ? userBadge.getEarnedAt() : null,
                            LocalizedTextResolver.resolve(badge.getName(), locale),
                            badge.getIcon(),
                            badge.getDescription() == null ? null : LocalizedTextResolver.resolve(badge.getDescription(), locale),
                            badge.getTier(),
                            progress,
                            target
                    );
                })
                .toList();
    }

    private static Set<UUID> earnedBadgeIds(Map<UUID, UserBadge> userBadges) {
        Set<UUID> earned = new HashSet<>();
        userBadges.forEach((badgeId, userBadge) -> {
            if (userBadge.getEarnedAt() != null) {
                earned.add(badgeId);
            }
        });
        return earned;
    }

    /**
     * Whether a badge is in the user's list: favorite-species badges only for users with a favorite species (or who
     * already earned them), so a collection badge never needs something the user cannot get.
     */
    private static boolean isCountable(Badge badge, boolean hasFavoriteSpecies, Set<UUID> earnedIds) {
        return badge.getCriteriaType() != BadgeCriteriaType.FAVORITE_SPECIES_LOGS || hasFavoriteSpecies || earnedIds.contains(badge.getId());
    }

    @Override
    public BadgeResponseDto create(CreateBadgeRequestDto request) {
        validateName(request.name());
        validateCriteria(request.criteriaType(), request.criteriaValue(), request.criteriaMetadata());

        Badge badge = Badge.builder()
                .name(request.name())
                .description(request.description())
                .icon(request.icon())
                .criteriaType(request.criteriaType())
                .criteriaValue(request.criteriaValue())
                .criteriaMetadata(request.criteriaMetadata())
                .tier(request.tier())
                .displayOrder(request.displayOrder())
                .secret(Boolean.TRUE.equals(request.secret()))
                .build();
        Badge saved = badgeRepository.save(badge);
        evaluateAllUsers();
        return toResponse(saved);
    }

    @Override
    public BadgeResponseDto update(UUID badgeId, UpdateBadgeRequestDto request) {
        validateName(request.name());
        validateCriteria(request.criteriaType(), request.criteriaValue(), request.criteriaMetadata());

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
        badge.setSecret(Boolean.TRUE.equals(request.secret()));
        Badge saved = badgeRepository.save(badge);
        evaluateAllUsers();
        return toResponse(saved);
    }

    @Override
    public void delete(UUID badgeId) {
        if (!badgeRepository.existsById(badgeId)) {
            throw ResourceNotFoundException.of("Badge", badgeId);
        }
        badgeRepository.deleteById(badgeId);
        userBadgeRepository.deleteByBadgeId(badgeId);
        evaluateAllUsers();
    }

    @Override
    public void evaluateForUser(UUID userId) {
        evaluate(userId, true);
    }

    /**
     * Recomputes every badge for one user, the collection badge last. {@code notify} is true only for a change the
     * user made themselves (a log or profile change): then earning the collection badge sends its one-time email.
     * Re-evaluating everyone after a badge was added, edited or removed, or at startup, never sends it.
     */
    private void evaluate(UUID userId, boolean notify) {
        List<Badge> badges = badgeRepository.findAll();
        for (Badge badge : badges) {
            if (badge.getCriteriaType() != BadgeCriteriaType.ALL_OTHER_BADGES) {
                upsertUserBadge(userId, badge, computeProgress(userId, badge), badge.getCriteriaValue());
            }
        }
        // Last, because it counts what the other badges just awarded.
        for (Badge badge : badges) {
            if (badge.getCriteriaType() == BadgeCriteriaType.ALL_OTHER_BADGES) {
                evaluateAllOtherBadges(userId, badge, badges, notify);
            }
        }
    }

    /** Sets the progress of a collection badge: earned badges among the others the user can earn. */
    private void evaluateAllOtherBadges(UUID userId, Badge collection, List<Badge> allBadges, boolean notify) {
        Map<UUID, UserBadge> userBadges = userBadgeRepository.findByUserId(userId).stream()
                .collect(java.util.stream.Collectors.toMap(UserBadge::getBadgeId, ub -> ub, (a, b) -> a));
        Set<UUID> earnedIds = earnedBadgeIds(userBadges);
        boolean hasFavoriteSpecies = favoriteSpeciesId(userId) != null;
        List<Badge> others = allBadges.stream()
                .filter(badge -> badge.getCriteriaType() != BadgeCriteriaType.ALL_OTHER_BADGES)
                .filter(badge -> isCountable(badge, hasFavoriteSpecies, earnedIds))
                .toList();
        int earned = (int) others.stream().filter(badge -> earnedIds.contains(badge.getId())).count();
        boolean newlyEarned = upsertUserBadge(userId, collection, earned, Math.max(others.size(), 1));
        if (newlyEarned && notify) {
            sendCompletionEmail(userId);
        }
    }

    /** Sends the one-time congratulation; a failure is logged and never affects the badge, which is already saved. */
    private void sendCompletionEmail(UUID userId) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                String language = userSettingsRepository.findByUserId(userId).map(UserSettings::getLocale).orElse("en");
                emailService.sendAllBadgesEarnedEmail(user.getEmail(), language);
            });
        } catch (RuntimeException e) {
            log.warn("Could not send the all-badges email for user {}", userId, e);
        }
    }

    @Override
    public int recomputeForAllUsers() {
        int processed = 0;
        for (User user : userRepository.findAll()) {
            try {
                evaluate(user.getId(), false);
                processed++;
            } catch (RuntimeException e) {
                log.error("Could not recompute badges for user {}", user.getId(), e);
            }
        }
        return processed;
    }

    /** Returns all badges by {@code displayOrder}, lowest first. Badges without one come last, in stored order. */
    private List<Badge> sortedBadges() {
        List<Badge> badges = new ArrayList<>(badgeRepository.findAll());
        badges.sort(Comparator.comparing(Badge::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        return badges;
    }

    /**
     * Recomputes every badge for every user after a badge was added, edited or removed (the collection badge depends on
     * all the others), so a new or edited badge (for example a lowered target)
     * is awarded without waiting for each user's next log change. Earned badges stay earned if the target is raised.
     */
    private void evaluateAllUsers() {
        for (User user : userRepository.findAll()) {
            evaluate(user.getId(), false);
        }
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
            case SAME_GENUS_SPECIES, SAME_GENUS_ANY -> computeSameGenusSpecies(userId, badge);
            case EARLY_BIRD_LOGS -> computeEarlyBirdLogs(userId);
            case FAMILY_PORTRAIT -> computeFamilyPortrait(userId);
            case RARE_SPECIES_LOGS -> computeRareSpeciesLogs(userId);
            case ALL_OTHER_BADGES -> 0;
            case FAVORITE_SPECIES_LOGS -> computeFavoriteSpeciesLogs(userId);
        };
    }

    /**
     * Returns the largest number of distinct species the user logged within one genus (the first word of the
     * scientific name, case-insensitive), or within {@code criteriaMetadata.genus} when set on a
     * {@code SAME_GENUS_SPECIES} badge. Logs without a species are ignored; pet logs count like any other. A subspecies
     * such as "Anas platyrhynchos domesticus" counts as its species ("Anas platyrhynchos").
     */
    private int computeSameGenusSpecies(UUID userId, Badge badge) {
        Set<UUID> speciesIds = new HashSet<>();
        birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(userId)
                .forEach(birdLog -> speciesIds.add(birdLog.getSpeciesId()));
        if (speciesIds.isEmpty()) {
            return 0;
        }

        Map<String, Set<String>> speciesByGenus = new HashMap<>();
        for (Species species : speciesRepository.findAllById(speciesIds)) {
            String genus = genusOf(species.getScientificName());
            if (genus != null) {
                speciesByGenus.computeIfAbsent(genus, key -> new HashSet<>()).add(speciesKeyOf(species.getScientificName()));
            }
        }

        String requestedGenus = badge.getCriteriaType() == BadgeCriteriaType.SAME_GENUS_SPECIES
                ? genusOf(extractGenus(badge.getCriteriaMetadata())) : null;
        if (requestedGenus != null) {
            return speciesByGenus.getOrDefault(requestedGenus, Set.of()).size();
        }
        return speciesByGenus.values().stream().mapToInt(Set::size).max().orElse(0);
    }

    /** Returns the lower-cased genus and species words of a scientific name, so subspecies fold into their species. */
    private static String speciesKeyOf(String scientificName) {
        String[] words = scientificName.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return words.length > 1 ? words[0] + " " + words[1] : words[0];
    }

    /** Returns how many non-pet logs were made between 04:00 and 06:00 local time; logs without a UTC offset are not counted. */
    private int computeEarlyBirdLogs(UUID userId) {
        int count = 0;
        for (BirdLog birdLog : birdLogRepository.findByUserId(userId)) {
            if (!birdLog.isPet() && isEarlyBird(birdLog.getObservedAt(), birdLog.getUtcOffsetMinutes())) {
                count++;
            }
        }
        return count;
    }

    /** Returns whether the local time (UTC time plus the offset) is from 04:00 up to, not including, 06:00. */
    static boolean isEarlyBird(Instant observedAt, Integer utcOffsetMinutes) {
        if (observedAt == null || utcOffsetMinutes == null) {
            return false;
        }
        int hour = observedAt.plusSeconds(utcOffsetMinutes * 60L).atZone(java.time.ZoneOffset.UTC).getHour();
        return hour >= EARLY_BIRD_FROM_HOUR && hour < EARLY_BIRD_TO_HOUR;
    }

    /**
     * Returns how many of the three parts (a male, a female and a baby) the user has logged for the species where it
     * is furthest. Each part needs its own non-pet log; one log is never used for two parts.
     */
    private int computeFamilyPortrait(UUID userId) {
        Map<UUID, int[]> logsBySpeciesAndPart = new HashMap<>();
        for (BirdLog birdLog : birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(userId)) {
            if (birdLog.isPet()) {
                continue;
            }
            int parts = (birdLog.getGender() == Gender.MALE ? 1 : 0) | (birdLog.getGender() == Gender.FEMALE ? 2 : 0)
                    | (birdLog.getLifeStage() == LifeStage.BABY ? 4 : 0);
            if (parts != 0) {
                logsBySpeciesAndPart.computeIfAbsent(birdLog.getSpeciesId(), key -> new int[8])[parts]++;
            }
        }
        int best = 0;
        for (int[] logsByParts : logsBySpeciesAndPart.values()) {
            best = Math.max(best, coveredParts(logsByParts));
            if (best == 3) {
                break;
            }
        }
        return best;
    }

    /** Returns the most parts (bit 1 male, 2 female, 4 baby) that distinct logs can fill, given log counts per part combination. */
    static int coveredParts(int[] logsByParts) {
        for (int size = 3; size >= 1; size--) {
            for (int wanted = 1; wanted < 8; wanted++) {
                if (Integer.bitCount(wanted) == size && canFill(wanted, logsByParts.clone())) {
                    return size;
                }
            }
        }
        return 0;
    }

    private static boolean canFill(int wanted, int[] available) {
        if (wanted == 0) {
            return true;
        }
        int part = Integer.lowestOneBit(wanted);
        for (int combination = 1; combination < 8; combination++) {
            if ((combination & part) != 0 && available[combination] > 0) {
                available[combination]--;
                if (canFill(wanted & ~part, available)) {
                    return true;
                }
                available[combination]++;
            }
        }
        return false;
    }

    /** Returns how many non-pet logs are of species whose conservation status is Endangered or Critically Endangered. */
    private int computeRareSpeciesLogs(UUID userId) {
        List<BirdLog> logs = birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(userId).stream()
                .filter(birdLog -> !birdLog.isPet())
                .toList();
        if (logs.isEmpty()) {
            return 0;
        }
        Set<UUID> rareSpeciesIds = new HashSet<>();
        Set<UUID> speciesIds = new HashSet<>();
        logs.forEach(birdLog -> speciesIds.add(birdLog.getSpeciesId()));
        for (Species species : speciesRepository.findAllById(speciesIds)) {
            if (isRare(species)) {
                rareSpeciesIds.add(species.getId());
            }
        }
        return (int) logs.stream().filter(birdLog -> rareSpeciesIds.contains(birdLog.getSpeciesId())).count();
    }

    /** Matches the English conservation status exactly (trimmed, ignoring case); the other languages are display text only. */
    static boolean isRare(Species species) {
        String status = species.getConservationStatus() == null ? null : species.getConservationStatus().get("en");
        return status != null && RARE_STATUSES.contains(status.trim().toLowerCase(Locale.ROOT));
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
    private static void validateCriteria(BadgeCriteriaType type, Integer criteriaValue, Map<String, Object> criteriaMetadata) {
        if (type == BadgeCriteriaType.FAMILY_PORTRAIT && criteriaValue != null && criteriaValue > 3) {
            throw new IllegalArgumentException("A FAMILY_PORTRAIT badge has three parts, so criteriaValue must be 1 to 3");
        }
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
        List<BirdLog> logs = birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(userId);

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

    /**
     * Returns the size of the densest cluster of sightings, counting raw sightings rather than distinct species.
     * Every log is tried as a cluster center.
     * Progress can drop but a badge once earned is never revoked.
     */
    private int computeMaxSightingsInRadius(UUID userId, Badge badge) {
        double radiusMeters = extractRadiusMeters(badge.getCriteriaMetadata());
        List<BirdLog> logs = birdLogRepository.findByUserId(userId);

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

    /** Saves the progress and returns true when this call is the moment the badge became earned. */
    private boolean upsertUserBadge(UUID userId, Badge badge, int progress, int target) {
        UserBadge userBadge = userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())
                .orElseGet(() -> UserBadge.builder()
                        .userId(userId)
                        .badgeId(badge.getId())
                        .progress(0)
                        .build());

        userBadge.setProgress(progress);
        boolean newlyEarned = userBadge.getEarnedAt() == null && progress >= target;
        if (newlyEarned) {
            userBadge.setEarnedAt(Instant.now());
        }
        userBadgeRepository.save(userBadge);
        return newlyEarned;
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
                badge.getDisplayOrder(),
                badge.isSecret()
        );
    }
}
