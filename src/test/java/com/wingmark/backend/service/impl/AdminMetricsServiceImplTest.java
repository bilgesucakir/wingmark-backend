package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.metrics.AdminMetricsResponseDto;
import com.wingmark.backend.entity.Badge;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMetricsServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private BadgeRepository badgeRepository;
    @Mock
    private UserBadgeRepository userBadgeRepository;
    @Mock
    private BirdLogRepository birdLogRepository;

    private AdminMetricsServiceImpl metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new AdminMetricsServiceImpl(
                userRepository, userSettingsRepository, speciesRepository,
                badgeRepository, userBadgeRepository, birdLogRepository);
    }

    @Test
    void countsUsersFavoringEachSpeciesAndResolvesTheirLocalizedName() {
        UUID sparrowId = UUID.randomUUID();
        UUID robinId = UUID.randomUUID();
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().id(UUID.randomUUID()).favoriteSpeciesId(sparrowId).build(),
                User.builder().id(UUID.randomUUID()).favoriteSpeciesId(sparrowId).build(),
                User.builder().id(UUID.randomUUID()).favoriteSpeciesId(robinId).build(),
                User.builder().id(UUID.randomUUID()).build() // no favorite - excluded
        ));
        when(speciesRepository.findById(sparrowId)).thenReturn(Optional.of(
                Species.builder().id(sparrowId).commonName(Map.of("en", "House Sparrow")).build()));
        when(speciesRepository.findById(robinId)).thenReturn(Optional.of(
                Species.builder().id(robinId).commonName(Map.of("en", "European Robin")).build()));
        when(badgeRepository.findAll()).thenReturn(List.of());
        when(userBadgeRepository.findAll()).thenReturn(List.of());
        when(birdLogRepository.findAll()).thenReturn(List.of());
        when(userSettingsRepository.findAll()).thenReturn(List.of());

        AdminMetricsResponseDto response = metricsService.compute(Locale.ENGLISH);

        assertThat(response.totalUsers()).isEqualTo(4);
        assertThat(response.favoriteSpecies())
                .extracting("speciesId", "speciesName", "userCount")
                .containsExactly(
                        tuple(sparrowId, "House Sparrow", 2L),
                        tuple(robinId, "European Robin", 1L)
                );
    }

    @Test
    void includesEveryBadgeEvenWithZeroCompletions() {
        UUID earnedBadgeId = UUID.randomUUID();
        UUID unearnedBadgeId = UUID.randomUUID();
        when(userRepository.findAll()).thenReturn(List.of());
        when(badgeRepository.findAll()).thenReturn(List.of(
                Badge.builder().id(earnedBadgeId).name(Map.of("en", "First Flight")).build(),
                Badge.builder().id(unearnedBadgeId).name(Map.of("en", "Rare Sighting")).build()
        ));
        when(userBadgeRepository.findAll()).thenReturn(List.of(
                UserBadge.builder().userId(UUID.randomUUID()).badgeId(earnedBadgeId).earnedAt(Instant.now()).build(),
                UserBadge.builder().userId(UUID.randomUUID()).badgeId(earnedBadgeId).earnedAt(Instant.now()).build(),
                // in-progress, not yet earned - must not count
                UserBadge.builder().userId(UUID.randomUUID()).badgeId(unearnedBadgeId).earnedAt(null).build()
        ));
        when(birdLogRepository.findAll()).thenReturn(List.of());
        when(userSettingsRepository.findAll()).thenReturn(List.of());

        AdminMetricsResponseDto response = metricsService.compute(Locale.ENGLISH);

        assertThat(response.badgeCompletions())
                .extracting("badgeId", "badgeName", "earnedCount")
                .containsExactly(
                        tuple(earnedBadgeId, "First Flight", 2L),
                        tuple(unearnedBadgeId, "Rare Sighting", 0L)
                );
    }

    @Test
    void groupsLogsIntoCoordinateGridCellsIgnoringFreeTextLocationName() {
        // locationName is whatever the user typed (e.g. "home", "konum1") and is not a
        // reliable region label, so grouping uses latitude/longitude instead: two nearby
        // sightings should land in the same ~11km grid cell even with slightly different
        // coordinates and totally different (or missing) locationName text.
        when(userRepository.findAll()).thenReturn(List.of());
        when(badgeRepository.findAll()).thenReturn(List.of());
        when(userBadgeRepository.findAll()).thenReturn(List.of());
        when(birdLogRepository.findAll()).thenReturn(List.of(
                BirdLog.builder().locationName("home").latitude(40.785091).longitude(-73.968285).build(),
                BirdLog.builder().locationName("konum1").latitude(40.789).longitude(-73.965).build(),
                BirdLog.builder().locationName(null).latitude(37.769421).longitude(-122.486214).build(),
                BirdLog.builder().locationName("nowhere").latitude(null).longitude(null).build()
        ));
        when(userSettingsRepository.findAll()).thenReturn(List.of());

        AdminMetricsResponseDto response = metricsService.compute(Locale.ENGLISH);

        assertThat(response.topRegions())
                .extracting("region", "logCount")
                .containsExactly(
                        tuple("40.8, -74.0", 2L),
                        tuple("37.8, -122.5", 1L)
                );
    }

    @Test
    void bucketsLocaleUsageByLanguageAndFlagsUnsetOnes() {
        when(userRepository.findAll()).thenReturn(List.of());
        when(badgeRepository.findAll()).thenReturn(List.of());
        when(userBadgeRepository.findAll()).thenReturn(List.of());
        when(birdLogRepository.findAll()).thenReturn(List.of());
        when(userSettingsRepository.findAll()).thenReturn(List.of(
                UserSettings.builder().userId(UUID.randomUUID()).locale("en-US").build(),
                UserSettings.builder().userId(UUID.randomUUID()).locale("en").build(),
                UserSettings.builder().userId(UUID.randomUUID()).locale("tr").build(),
                UserSettings.builder().userId(UUID.randomUUID()).locale(null).build()
        ));

        AdminMetricsResponseDto response = metricsService.compute(Locale.ENGLISH);

        assertThat(response.localeUsage())
                .extracting("locale", "userCount")
                .containsExactlyInAnyOrder(
                        tuple("en", 2L),
                        tuple("tr", 1L),
                        tuple("unset", 1L)
                );
    }
}
