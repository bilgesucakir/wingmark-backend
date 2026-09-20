package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.metrics.AdminMetricsResponseDto;
import com.wingmark.backend.dto.metrics.BadgeCompletionCountDto;
import com.wingmark.backend.dto.metrics.LocaleUsageCountDto;
import com.wingmark.backend.dto.metrics.RegionLogCountDto;
import com.wingmark.backend.dto.metrics.SpeciesFavoriteCountDto;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserBadge;
import com.wingmark.backend.entity.UserSettings;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.service.AdminMetricsService;
import com.wingmark.backend.util.LocalizedTextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMetricsServiceImpl implements AdminMetricsService {

    private static final int TOP_REGIONS_LIMIT = 10;

    /**
     * BirdLog.locationName is free text the user typed (e.g. "home", "konum1") and is
     * not a reliable region label - the same real place gets spelled differently by
     * different users, and made-up labels don't correspond to a place at all. Latitude
     * and longitude are always numeric, so regions are grid cells of this size (in
     * degrees) instead: roughly 11km per cell at the equator, which clusters repeat
     * visits to the same park/area without needing a geocoding service.
     */
    private static final double REGION_GRID_DEGREES = 0.1;

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SpeciesRepository speciesRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BirdLogRepository birdLogRepository;

    @Override
    public AdminMetricsResponseDto compute(Locale locale) {
        List<User> users = userRepository.findAll();

        return new AdminMetricsResponseDto(
                users.size(),
                favoriteSpeciesCounts(users, locale),
                badgeCompletionCounts(locale),
                topRegionCounts(),
                localeUsageCounts(),
                Instant.now()
        );
    }

    private List<SpeciesFavoriteCountDto> favoriteSpeciesCounts(List<User> users, Locale locale) {
        Map<UUID, Long> countsBySpeciesId = users.stream()
                .map(User::getFavoriteSpeciesId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        return countsBySpeciesId.entrySet().stream()
                .map(entry -> new SpeciesFavoriteCountDto(
                        entry.getKey(),
                        resolveSpeciesName(entry.getKey(), locale),
                        entry.getValue()
                ))
                .sorted(Comparator.comparingLong(SpeciesFavoriteCountDto::userCount).reversed())
                .toList();
    }

    private String resolveSpeciesName(UUID speciesId, Locale locale) {
        return speciesRepository.findById(speciesId)
                .map(Species::getCommonName)
                .map(commonName -> LocalizedTextResolver.resolve(commonName, locale))
                .orElse(null);
    }

    private List<BadgeCompletionCountDto> badgeCompletionCounts(Locale locale) {
        Map<UUID, Long> earnedCountsByBadgeId = userBadgeRepository.findAll().stream()
                .filter(userBadge -> userBadge.getEarnedAt() != null)
                .collect(Collectors.groupingBy(UserBadge::getBadgeId, Collectors.counting()));

        return badgeRepository.findAll().stream()
                .map(badge -> new BadgeCompletionCountDto(
                        badge.getId(),
                        LocalizedTextResolver.resolve(badge.getName(), locale),
                        earnedCountsByBadgeId.getOrDefault(badge.getId(), 0L)
                ))
                .sorted(Comparator.comparingLong(BadgeCompletionCountDto::earnedCount).reversed())
                .toList();
    }

    private List<RegionLogCountDto> topRegionCounts() {
        Map<String, Long> countsByRegion = birdLogRepository.findAll().stream()
                .filter(log -> log.getLatitude() != null && log.getLongitude() != null)
                .collect(Collectors.groupingBy(this::regionGridKey, Collectors.counting()));

        return countsByRegion.entrySet().stream()
                .map(entry -> new RegionLogCountDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(RegionLogCountDto::logCount).reversed())
                .limit(TOP_REGIONS_LIMIT)
                .toList();
    }

    private String regionGridKey(BirdLog log) {
        double gridLat = snapToGrid(log.getLatitude());
        double gridLng = snapToGrid(log.getLongitude());
        return String.format(Locale.ROOT, "%.1f, %.1f", gridLat, gridLng);
    }

    private double snapToGrid(double value) {
        return Math.round(value / REGION_GRID_DEGREES) * REGION_GRID_DEGREES;
    }

    private List<LocaleUsageCountDto> localeUsageCounts() {
        Map<String, Long> countsByLocale = userSettingsRepository.findAll().stream()
                .map(this::normalizeLocale)
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

        return countsByLocale.entrySet().stream()
                .map(entry -> new LocaleUsageCountDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(LocaleUsageCountDto::userCount).reversed())
                .toList();
    }

    private String normalizeLocale(UserSettings settings) {
        String locale = settings.getLocale();
        if (!StringUtils.hasText(locale)) {
            return "unset";
        }
        return locale.trim().substring(0, Math.min(2, locale.trim().length())).toLowerCase(Locale.ROOT);
    }
}
