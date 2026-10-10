package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.CreateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UpdateBadgeRequestDto;
import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserBadge;
import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeServiceImplTest {

    @Mock
    private BadgeRepository badgeRepository;
    @Mock
    private UserBadgeRepository userBadgeRepository;
    @Mock
    private BirdLogRepository birdLogRepository;
    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private com.wingmark.backend.repository.UserSettingsRepository userSettingsRepository;
    @Mock
    private com.wingmark.backend.service.EmailService emailService;

    private BadgeServiceImpl badgeService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        badgeService = new BadgeServiceImpl(badgeRepository, userBadgeRepository, birdLogRepository, speciesRepository, userRepository, userSettingsRepository, emailService);
    }

    @Test
    void totalLogsBadgeIsEarnedOnceThresholdIsReached() {
        Badge badge = Badge.builder()
                .id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.TOTAL_LOGS)
                .criteriaValue(10)
                .build();

        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.countByUserId(userId)).thenReturn(10L);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(10);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void badgeIsNotEarnedBelowThreshold() {
        Badge badge = Badge.builder()
                .id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.PET_LOGS)
                .criteriaValue(3)
                .build();

        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.countByUserIdAndPetTrue(userId)).thenReturn(1L);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(1);
        assertThat(captor.getValue().getEarnedAt()).isNull();
    }

    @Test
    void alreadyEarnedBadgeKeepsItsEarnedAtEvenIfProgressWasRecomputed() {
        Badge badge = Badge.builder()
                .id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.BABY_LOGS)
                .criteriaValue(2)
                .build();
        java.time.Instant earlier = java.time.Instant.now().minusSeconds(3600);
        UserBadge existing = UserBadge.builder()
                .userId(userId)
                .badgeId(badge.getId())
                .progress(2)
                .earnedAt(earlier)
                .build();

        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.countByUserIdAndLifeStage(userId, LifeStage.BABY)).thenReturn(5L);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.of(existing));

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(5);
        assertThat(captor.getValue().getEarnedAt()).isEqualTo(earlier);
    }

    @Test
    void speciesInRadiusFindsTheDenserClusterOfSpecies() {
        Badge badge = Badge.builder()
                .id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.SPECIES_IN_RADIUS)
                .criteriaValue(2)
                .criteriaMetadata(java.util.Map.of("radiusMeters", 2000))
                .build();

        UUID speciesA = UUID.randomUUID();
        UUID speciesB = UUID.randomUUID();
        UUID speciesC = UUID.randomUUID();

        // Two logs (A, B) close together near (0,0); one far-away log (C) near (10,10).
        BirdLog logA = BirdLog.builder().userId(userId).speciesId(speciesA).pet(false)
                .latitude(0.0).longitude(0.0).build();
        BirdLog logB = BirdLog.builder().userId(userId).speciesId(speciesB).pet(false)
                .latitude(0.001).longitude(0.001).build();
        BirdLog logC = BirdLog.builder().userId(userId).speciesId(speciesC).pet(false)
                .latitude(10.0).longitude(10.0).build();

        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(userId))
                .thenReturn(List.of(logA, logB, logC));
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(2);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void sightingsInRadiusCountsRawLogsNotDistinctSpecies() {
        Badge badge = Badge.builder()
                .id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.SIGHTINGS_IN_RADIUS)
                .criteriaValue(3)
                .criteriaMetadata(java.util.Map.of("radiusMeters", 2000))
                .build();

        UUID species = UUID.randomUUID();

        // Three logs of the same species close together near (0,0); one far-away log near (10,10).
        BirdLog logA = BirdLog.builder().userId(userId).speciesId(species).pet(false)
                .latitude(0.0).longitude(0.0).build();
        BirdLog logB = BirdLog.builder().userId(userId).speciesId(species).pet(false)
                .latitude(0.001).longitude(0.001).build();
        BirdLog logC = BirdLog.builder().userId(userId).speciesId(species).pet(false)
                .latitude(0.002).longitude(0.0).build();
        BirdLog logFar = BirdLog.builder().userId(userId).speciesId(species).pet(false)
                .latitude(10.0).longitude(10.0).build();

        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.findByUserId(userId))
                .thenReturn(List.of(logA, logB, logC, logFar));
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(3);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void speciesLogsCountsOnlyLogsOfTheConfiguredSpecies() {
        UUID targetSpecies = UUID.randomUUID();
        Badge badge = Badge.builder()
                .id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.SPECIES_LOGS)
                .criteriaValue(3)
                .criteriaMetadata(java.util.Map.of("speciesId", targetSpecies.toString()))
                .build();

        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.countByUserIdAndSpeciesId(userId, targetSpecies)).thenReturn(3L);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(3);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void speciesLogsWithMissingSpeciesIdStaysAtZeroProgressInsteadOfThrowing() {
        Badge badge = Badge.builder()
                .id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.SPECIES_LOGS)
                .criteriaValue(3)
                .criteriaMetadata(null)
                .build();

        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(0);
        assertThat(captor.getValue().getEarnedAt()).isNull();
    }

    @Test
    void updatingAnExistingBadgeOverwritesItsFields() {
        UUID badgeId = UUID.randomUUID();
        Badge existing = Badge.builder()
                .id(badgeId).name(Map.of("en", "Old name")).criteriaType(BadgeCriteriaType.TOTAL_LOGS).criteriaValue(5)
                .build();
        when(badgeRepository.findById(badgeId)).thenReturn(Optional.of(existing));
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBadgeRequestDto request = new UpdateBadgeRequestDto(
                Map.of("en", "New name"), Map.of("en", "New description"), "icon.png", BadgeCriteriaType.UNIQUE_SPECIES, 20, null, BadgeTier.GOLD, 3);

        BadgeResponseDto response = badgeService.update(badgeId, request);

        assertThat(response.name()).containsEntry("en", "New name");
        assertThat(response.criteriaType()).isEqualTo(BadgeCriteriaType.UNIQUE_SPECIES);
        assertThat(response.criteriaValue()).isEqualTo(20);
        assertThat(response.tier()).isEqualTo(BadgeTier.GOLD);
        assertThat(response.displayOrder()).isEqualTo(3);
    }

    @Test
    void updatingAMissingBadgeThrowsNotFound() {
        UUID badgeId = UUID.randomUUID();
        when(badgeRepository.findById(badgeId)).thenReturn(Optional.empty());

        UpdateBadgeRequestDto request = new UpdateBadgeRequestDto(
                Map.of("en", "Name"), null, null, BadgeCriteriaType.TOTAL_LOGS, 5, null, null, null);

        assertThatThrownBy(() -> badgeService.update(badgeId, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static Species species(String scientificName) {
        return Species.builder().id(UUID.randomUUID()).scientificName(scientificName).build();
    }

    private static List<BirdLog> logsOf(Species... species) {
        List<BirdLog> logs = new java.util.ArrayList<>();
        for (Species one : species) {
            BirdLog birdLog = BirdLog.builder().speciesId(one.getId()).build();
            logs.add(birdLog);
        }
        return logs;
    }

    /** Evaluates a SAME_GENUS_SPECIES badge for a user who logged the given species and returns the saved progress row. */
    private UserBadge evaluateSameGenus(int target, Map<String, Object> metadata, List<BirdLog> logs, Species... species) {
        Badge badge = Badge.builder()
                .id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.SAME_GENUS_SPECIES)
                .criteriaValue(target)
                .criteriaMetadata(metadata)
                .build();
        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(userId)).thenReturn(logs);
        if (!logs.isEmpty()) {
            when(speciesRepository.findAllById(any())).thenReturn(List.of(species));
        }
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void sameGenusBadgeHasNoProgressWithoutLogs() {
        UserBadge saved = evaluateSameGenus(2, null, List.of());

        assertThat(saved.getProgress()).isZero();
        assertThat(saved.getEarnedAt()).isNull();
    }

    @Test
    void sameGenusBadgeIsNotEarnedWithOneSpecies() {
        Species house = species("Passer domesticus");

        UserBadge saved = evaluateSameGenus(2, null, logsOf(house), house);

        assertThat(saved.getProgress()).isEqualTo(1);
        assertThat(saved.getEarnedAt()).isNull();
    }

    @Test
    void sameGenusBadgeIsEarnedWhenTwoSpeciesShareAGenus() {
        Species house = species("Passer domesticus");
        Species tree = species("Passer montanus");

        UserBadge saved = evaluateSameGenus(2, null, logsOf(house, tree), house, tree);

        assertThat(saved.getProgress()).isEqualTo(2);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    @Test
    void sameGenusBadgeCountsASpeciesOnceHoweverOftenItWasLogged() {
        Species house = species("Passer domesticus");
        List<BirdLog> logs = logsOf(house, house, house);

        UserBadge saved = evaluateSameGenus(2, null, logs, house);

        assertThat(saved.getProgress()).isEqualTo(1);
        assertThat(saved.getEarnedAt()).isNull();
    }

    @Test
    void sameGenusBadgeIsNotEarnedByTwoSpeciesOfDifferentGenera() {
        Species house = species("Passer domesticus");
        Species robin = species("Erithacus rubecula");

        UserBadge saved = evaluateSameGenus(2, null, logsOf(house, robin), house, robin);

        assertThat(saved.getProgress()).isEqualTo(1);
        assertThat(saved.getEarnedAt()).isNull();
    }

    @Test
    void sameGenusBadgeTakesTheBestGenusWhenNoneIsConfigured() {
        Species house = species("Passer domesticus");
        Species tree = species("Passer montanus");
        Species desert = species("Passer simplex");
        Species robin = species("Erithacus rubecula");

        UserBadge saved = evaluateSameGenus(3, Map.of(), logsOf(house, tree, desert, robin), house, tree, desert, robin);

        assertThat(saved.getProgress()).isEqualTo(3);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    @Test
    void sameGenusBadgeWithAGenusOnlyCountsThatGenus() {
        Species house = species("Passer domesticus");
        Species tree = species("Passer montanus");
        Species blue = species("Cyanistes caeruleus");
        Species great = species("Cyanistes teneriffae");
        Species coal = species("Cyanistes cyanus");

        UserBadge saved = evaluateSameGenus(3, Map.of("genus", " passer "), logsOf(house, tree, blue, great, coal),
                house, tree, blue, great, coal);

        assertThat(saved.getProgress()).isEqualTo(2);
        assertThat(saved.getEarnedAt()).isNull();
    }

    @Test
    void sameGenusBadgeIgnoresCaseFoldsSubspeciesAndIgnoresBlankScientificNames() {
        Species house = species("PASSER domesticus");
        Species indian = species("passer  domesticus indicus");
        Species tree = species("Passer montanus");
        Species blank = species("  ");
        Species none = species(null);

        UserBadge saved = evaluateSameGenus(2, null, logsOf(house, indian, tree, blank, none),
                house, indian, tree, blank, none);

        // The subspecies (trinomial) folds into its species, so only house and tree sparrow count.
        assertThat(saved.getProgress()).isEqualTo(2);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    @Test
    void sameGenusBadgeOnlyLooksAtLogsWithASpecies() {
        Species house = species("Passer domesticus");

        evaluateSameGenus(2, null, logsOf(house), house);

        verify(birdLogRepository).findByUserIdAndSpeciesIdIsNotNull(userId);
    }

    @Test
    void sameGenusBadgeRejectsABlankOrNonTextGenus() {
        for (Object genus : new Object[]{"", "  ", 42}) {
            CreateBadgeRequestDto request = new CreateBadgeRequestDto(Map.of("en", "Twins"), null, null,
                    BadgeCriteriaType.SAME_GENUS_SPECIES, 2, Map.of("genus", genus), BadgeTier.BRONZE, null);

            assertThatThrownBy(() -> badgeService.create(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("genus");
        }
    }

    @Test
    void sameGenusBadgeAcceptsAMissingOrNamedGenus() {
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));

        for (Map<String, Object> metadata : java.util.Arrays.asList(null, Map.<String, Object>of(), Map.<String, Object>of("genus", "Passer"))) {
            CreateBadgeRequestDto request = new CreateBadgeRequestDto(Map.of("en", "Twins"), null, null,
                    BadgeCriteriaType.SAME_GENUS_SPECIES, 2, metadata, BadgeTier.BRONZE, null);

            assertThat(badgeService.create(request).criteriaType()).isEqualTo(BadgeCriteriaType.SAME_GENUS_SPECIES);
        }
    }

    private static Badge badgeWithOrder(String name, Integer displayOrder) {
        return Badge.builder().id(UUID.randomUUID()).name(Map.of("en", name)).criteriaValue(1).displayOrder(displayOrder).build();
    }

    @Test
    void catalogIsSortedByDisplayOrderWithUnorderedBadgesLastInStoredOrder() {
        Badge third = badgeWithOrder("third", 3);
        Badge unorderedA = badgeWithOrder("unorderedA", null);
        Badge first = badgeWithOrder("first", 1);
        Badge unorderedB = badgeWithOrder("unorderedB", null);
        Badge second = badgeWithOrder("second", 2);
        when(badgeRepository.findAll()).thenReturn(List.of(third, unorderedA, first, unorderedB, second));

        List<BadgeResponseDto> catalog = badgeService.getAll();

        assertThat(catalog).extracting(b -> b.name().get("en"))
                .containsExactly("first", "second", "third", "unorderedA", "unorderedB");
        assertThat(catalog.get(0).displayOrder()).isEqualTo(1);
    }

    @Test
    void userBadgesFollowTheSameDisplayOrder() {
        Badge second = badgeWithOrder("second", 2);
        Badge first = badgeWithOrder("first", 1);
        when(badgeRepository.findAll()).thenReturn(List.of(second, first));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(badgeService.getByUserId(userId, java.util.Locale.ENGLISH))
                .extracting(b -> b.badgeName()).containsExactly("first", "second");
    }

    @Test
    void creatingABadgeStoresItsDisplayOrder() {
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateBadgeRequestDto request = new CreateBadgeRequestDto(Map.of("en", "Early"), null, null,
                BadgeCriteriaType.TOTAL_LOGS, 1, null, null, 0);

        assertThat(badgeService.create(request).displayOrder()).isZero();
    }

    private Badge favoriteBadge(int target) {
        return Badge.builder().id(UUID.randomUUID()).name(Map.of("en", "Favorite " + target))
                .criteriaType(BadgeCriteriaType.FAVORITE_SPECIES_LOGS).criteriaValue(target).build();
    }

    @Test
    void favoriteSpeciesBadgeCountsLogsOfTheUsersFavoriteSpecies() {
        UUID favorite = UUID.randomUUID();
        Badge badge = favoriteBadge(5);
        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).favoriteSpeciesId(favorite).build()));
        when(speciesRepository.existsById(favorite)).thenReturn(true);
        when(birdLogRepository.countByUserIdAndSpeciesId(userId, favorite)).thenReturn(5L);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(5);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void favoriteSpeciesBadgeHasNoProgressWithoutAFavoriteSpecies() {
        Badge badge = favoriteBadge(1);
        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isZero();
        assertThat(captor.getValue().getEarnedAt()).isNull();
    }

    @Test
    void favoriteSpeciesBadgesAreHiddenFromUsersWithoutAFavoriteSpecies() {
        Badge favorite = favoriteBadge(1);
        Badge total = badgeWithOrder("Total", null);
        when(badgeRepository.findAll()).thenReturn(List.of(favorite, total));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));

        assertThat(badgeService.getByUserId(userId, java.util.Locale.ENGLISH))
                .extracting(b -> b.badgeId()).containsExactly(total.getId());
    }

    @Test
    void favoriteSpeciesBadgesAreShownToUsersWithAFavoriteSpecies() {
        Badge favorite = favoriteBadge(1);
        Badge total = badgeWithOrder("Total", null);
        when(badgeRepository.findAll()).thenReturn(List.of(favorite, total));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of());
        UUID favoriteSpecies = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).favoriteSpeciesId(favoriteSpecies).build()));
        when(speciesRepository.existsById(favoriteSpecies)).thenReturn(true);

        assertThat(badgeService.getByUserId(userId, java.util.Locale.ENGLISH))
                .extracting(b -> b.badgeId()).containsExactly(favorite.getId(), total.getId());
    }

    @Test
    void aFavoriteSpeciesThatNoLongerExistsCountsAsNoFavorite() {
        UUID deleted = UUID.randomUUID();
        Badge favorite = favoriteBadge(1);
        Badge total = badgeWithOrder("Total", null);
        when(badgeRepository.findAll()).thenReturn(List.of(favorite, total));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).favoriteSpeciesId(deleted).build()));
        when(speciesRepository.existsById(deleted)).thenReturn(false);

        assertThat(badgeService.getByUserId(userId, java.util.Locale.ENGLISH))
                .extracting(b -> b.badgeId()).containsExactly(total.getId());
    }

    @Test
    void favoriteSpeciesProgressIsZeroWhenTheFavoriteSpeciesWasDeleted() {
        UUID deleted = UUID.randomUUID();
        Badge badge = favoriteBadge(1);
        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).favoriteSpeciesId(deleted).build()));
        when(speciesRepository.existsById(deleted)).thenReturn(false);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isZero();
    }

    @Test
    void loweringABadgeTargetAwardsUsersWhoAlreadyReachedTheNewTarget() {
        UUID badgeId = UUID.randomUUID();
        Badge existing = Badge.builder().id(badgeId).name(Map.of("en", "Logger"))
                .criteriaType(BadgeCriteriaType.TOTAL_LOGS).criteriaValue(3).build();
        UserBadge stuck = UserBadge.builder().userId(userId).badgeId(badgeId).progress(2).build();
        when(badgeRepository.findById(badgeId)).thenReturn(Optional.of(existing));
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(badgeRepository.findAll()).thenReturn(List.of(existing));
        when(userRepository.findAll()).thenReturn(List.of(User.builder().id(userId).build()));
        when(birdLogRepository.countByUserId(userId)).thenReturn(2L);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badgeId)).thenReturn(Optional.of(stuck));

        badgeService.update(badgeId, new UpdateBadgeRequestDto(Map.of("en", "Logger"), null, null,
                BadgeCriteriaType.TOTAL_LOGS, 2, null, null, null));

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(2);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void raisingABadgeTargetKeepsAlreadyEarnedBadgesEarned() {
        UUID badgeId = UUID.randomUUID();
        Badge existing = Badge.builder().id(badgeId).name(Map.of("en", "Logger"))
                .criteriaType(BadgeCriteriaType.TOTAL_LOGS).criteriaValue(2).build();
        java.time.Instant earlier = java.time.Instant.now().minusSeconds(3600);
        UserBadge earned = UserBadge.builder().userId(userId).badgeId(badgeId).progress(2).earnedAt(earlier).build();
        when(badgeRepository.findById(badgeId)).thenReturn(Optional.of(existing));
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(badgeRepository.findAll()).thenReturn(List.of(existing));
        when(userRepository.findAll()).thenReturn(List.of(User.builder().id(userId).build()));
        when(birdLogRepository.countByUserId(userId)).thenReturn(2L);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badgeId)).thenReturn(Optional.of(earned));

        badgeService.update(badgeId, new UpdateBadgeRequestDto(Map.of("en", "Logger"), null, null,
                BadgeCriteriaType.TOTAL_LOGS, 5, null, null, null));

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getEarnedAt()).isEqualTo(earlier);
    }

    @Test
    void creatingABadgeComputesProgressForExistingUsers() {
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(badgeRepository.findAll()).thenAnswer(inv -> List.of(Badge.builder().id(UUID.randomUUID())
                .criteriaType(BadgeCriteriaType.TOTAL_LOGS).criteriaValue(3).build()));
        when(userRepository.findAll()).thenReturn(List.of(User.builder().id(userId).build()));
        when(birdLogRepository.countByUserId(userId)).thenReturn(4L);
        when(userBadgeRepository.findByUserIdAndBadgeId(any(), any())).thenReturn(Optional.empty());

        badgeService.create(new CreateBadgeRequestDto(Map.of("en", "Four"), null, null,
                BadgeCriteriaType.TOTAL_LOGS, 3, null, null, null));

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(4);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void speciesInRadiusCountsPetLogsLikeAnyOtherSpecies() {
        Badge badge = Badge.builder().id(UUID.randomUUID()).criteriaType(BadgeCriteriaType.SPECIES_IN_RADIUS)
                .criteriaValue(2).criteriaMetadata(Map.of("radiusMeters", 1000)).build();
        BirdLog petParrotlet = BirdLog.builder().speciesId(UUID.randomUUID()).pet(true)
                .latitude(40.93977).longitude(29.11957).build();
        BirdLog wildSparrow = BirdLog.builder().speciesId(UUID.randomUUID()).pet(false)
                .latitude(40.93974).longitude(29.11961).build();
        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(userId)).thenReturn(List.of(petParrotlet, wildSparrow));
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(2);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void sightingsInRadiusCountsPetSightingsToo() {
        Badge badge = Badge.builder().id(UUID.randomUUID()).criteriaType(BadgeCriteriaType.SIGHTINGS_IN_RADIUS)
                .criteriaValue(2).criteriaMetadata(Map.of("radiusMeters", 1000)).build();
        BirdLog pet = BirdLog.builder().pet(true).latitude(40.93977).longitude(29.11957).build();
        BirdLog wild = BirdLog.builder().pet(false).latitude(40.93974).longitude(29.11961).build();
        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.findByUserId(userId)).thenReturn(List.of(pet, wild));
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(2);
    }

    @Test
    void recomputingAllUsersEvaluatesEveryUserAndSkipsOnesThatFail() {
        UUID failingUser = UUID.randomUUID();
        Badge badge = Badge.builder().id(UUID.randomUUID()).criteriaType(BadgeCriteriaType.TOTAL_LOGS).criteriaValue(1).build();
        when(userRepository.findAll()).thenReturn(List.of(User.builder().id(failingUser).build(), User.builder().id(userId).build()));
        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        when(birdLogRepository.countByUserId(failingUser)).thenThrow(new IllegalStateException("boom"));
        when(birdLogRepository.countByUserId(userId)).thenReturn(1L);
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        assertThat(badgeService.recomputeForAllUsers()).isEqualTo(1);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }
}
