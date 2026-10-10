package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.CreateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UserBadgeResponseDto;
import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserBadge;
import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import com.wingmark.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Secret badges and the early bird, family portrait, any-genus, rare species and complete-all criteria. */
@ExtendWith(MockitoExtension.class)
class SecretBadgesAndNewCriteriaTest {

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

    private static Badge badge(BadgeCriteriaType type, int target) {
        return Badge.builder().id(UUID.randomUUID()).name(Map.of("en", "B", "tr", "T")).description(Map.of("en", "D"))
                .icon("icon").tier(BadgeTier.SILVER).criteriaType(type).criteriaValue(target).build();
    }

    private static Species species(String scientificName, String status) {
        return Species.builder().id(UUID.randomUUID()).scientificName(scientificName)
                .conservationStatus(status == null ? null : Map.of("en", status, "tr", "x")).build();
    }

    private static BirdLog log(Species species, Gender gender, LifeStage stage, boolean pet) {
        return BirdLog.builder().speciesId(species.getId()).gender(gender).lifeStage(stage).pet(pet).build();
    }

    /** Evaluates one badge with the given logs and returns the progress row that was saved. */
    @SuppressWarnings("unchecked")
    private UserBadge evaluateOne(Badge badge, List<BirdLog> logs, Species... species) {
        when(badgeRepository.findAll()).thenReturn(List.of(badge));
        lenient().when(birdLogRepository.findByUserId(userId)).thenReturn(logs);
        lenient().when(birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(userId))
                .thenReturn(logs.stream().filter(l -> l.getSpeciesId() != null).toList());
        // Like the real repository, return only the species that were asked for.
        lenient().when(speciesRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.Set<UUID> wanted = new java.util.HashSet<>();
            ((Iterable<UUID>) inv.getArgument(0)).forEach(wanted::add);
            return List.of(species).stream().filter(s -> wanted.contains(s.getId())).toList();
        });
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());
        badgeService.evaluateForUser(userId);
        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---- early bird ----

    @Test
    void earlyBirdCoversFourToSixLocalTimeWithTheFirstSecondIncludedAndSixExcluded() {
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-09T01:00:00Z"), 180)).isTrue();   // 04:00
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-09T00:59:59Z"), 180)).isFalse();  // 03:59:59
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-09T02:59:59Z"), 180)).isTrue();   // 05:59:59
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-09T03:00:00Z"), 180)).isFalse();  // 06:00
    }

    @Test
    void earlyBirdUsesTheOffsetAcrossMidnightAndWestOfGreenwich() {
        // 09:30 UTC at UTC-05:00 is 04:30 local; 23:30 UTC at UTC+05:30 is 05:00 the next day.
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-09T09:30:00Z"), -300)).isTrue();
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-08T23:30:00Z"), 330)).isTrue();
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-09T04:30:00Z"), 0)).isTrue();
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-09T09:30:00Z"), 0)).isFalse();
    }

    @Test
    void aLogWithoutAnOffsetNeverCountsAsEarlyBird() {
        assertThat(BadgeServiceImpl.isEarlyBird(Instant.parse("2026-10-09T04:30:00Z"), null)).isFalse();
        assertThat(BadgeServiceImpl.isEarlyBird(null, 0)).isFalse();
    }

    @Test
    void earlyBirdBadgeCountsOnlyNonPetLogsWithAnEarlyLocalTime() {
        Instant early = Instant.parse("2026-10-09T01:30:00Z");
        List<BirdLog> logs = List.of(
                BirdLog.builder().observedAt(early).utcOffsetMinutes(180).build(),
                BirdLog.builder().observedAt(early).utcOffsetMinutes(180).pet(true).build(),
                BirdLog.builder().observedAt(early).build(),
                BirdLog.builder().observedAt(Instant.parse("2026-10-09T12:00:00Z")).utcOffsetMinutes(180).build());

        UserBadge saved = evaluateOne(badge(BadgeCriteriaType.EARLY_BIRD_LOGS, 1), logs);

        assertThat(saved.getProgress()).isEqualTo(1);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    // ---- family portrait ----

    @Test
    void familyPortraitNeedsAMaleAFemaleAndABabyInThreeDifferentLogs() {
        int[] separate = new int[8];
        separate[1] = 1; // male
        separate[2] = 1; // female
        separate[4] = 1; // baby
        assertThat(BadgeServiceImpl.coveredParts(separate)).isEqualTo(3);

        int[] maleBabyAndFemale = new int[8];
        maleBabyAndFemale[5] = 1; // one log that is a male baby
        maleBabyAndFemale[2] = 1;
        assertThat(BadgeServiceImpl.coveredParts(maleBabyAndFemale)).isEqualTo(2);

        int[] onlyMaleBaby = new int[8];
        onlyMaleBaby[5] = 1;
        assertThat(BadgeServiceImpl.coveredParts(onlyMaleBaby)).isEqualTo(1);

        int[] twoMaleBabiesAndFemale = new int[8];
        twoMaleBabiesAndFemale[5] = 2;
        twoMaleBabiesAndFemale[2] = 1;
        assertThat(BadgeServiceImpl.coveredParts(twoMaleBabiesAndFemale)).isEqualTo(3);
        assertThat(BadgeServiceImpl.coveredParts(new int[8])).isZero();
    }

    @Test
    void familyPortraitBadgeIsEarnedWithAllThreePartsOfOneSpeciesAndIgnoresPetLogs() {
        Species sparrow = species("Passer domesticus", null);
        Species robin = species("Erithacus rubecula", null);
        List<BirdLog> logs = List.of(
                log(sparrow, Gender.MALE, LifeStage.ADULT, false),
                log(sparrow, Gender.FEMALE, LifeStage.ADULT, false),
                log(sparrow, Gender.UNKNOWN, LifeStage.BABY, false),
                log(robin, Gender.MALE, LifeStage.ADULT, false),
                log(robin, Gender.FEMALE, LifeStage.ADULT, true));

        UserBadge saved = evaluateOne(badge(BadgeCriteriaType.FAMILY_PORTRAIT, 3), logs, sparrow, robin);

        assertThat(saved.getProgress()).isEqualTo(3);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    @Test
    void familyPortraitDoesNotMixPartsFromDifferentSpecies() {
        Species sparrow = species("Passer domesticus", null);
        Species robin = species("Erithacus rubecula", null);
        List<BirdLog> logs = List.of(
                log(sparrow, Gender.MALE, LifeStage.ADULT, false),
                log(robin, Gender.FEMALE, LifeStage.ADULT, false));

        UserBadge saved = evaluateOne(badge(BadgeCriteriaType.FAMILY_PORTRAIT, 3), logs, sparrow, robin);

        assertThat(saved.getProgress()).isEqualTo(1);
        assertThat(saved.getEarnedAt()).isNull();
    }

    @Test
    void aFamilyPortraitTargetAboveThreeIsRejected() {
        CreateBadgeRequestDto request = new CreateBadgeRequestDto(Map.of("en", "F"), null, null,
                BadgeCriteriaType.FAMILY_PORTRAIT, 4, null, BadgeTier.GOLD, null, true);

        assertThatThrownBy(() -> badgeService.create(request)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- genus (any) ----

    @Test
    void anyGenusBadgeCountsPetLogsAndFoldsSubspeciesIntoTheirSpecies() {
        Species mallard = species("Anas platyrhynchos", null);
        Species domesticDuck = species("Anas platyrhynchos domesticus", null);
        Species teal = species("Anas crecca", null);
        Species petTeal = species("Anas acuta", null);
        List<BirdLog> logs = List.of(
                log(mallard, Gender.UNKNOWN, LifeStage.ADULT, false),
                log(domesticDuck, Gender.UNKNOWN, LifeStage.ADULT, false),
                log(teal, Gender.UNKNOWN, LifeStage.ADULT, false),
                log(petTeal, Gender.UNKNOWN, LifeStage.ADULT, true));

        UserBadge saved = evaluateOne(badge(BadgeCriteriaType.SAME_GENUS_ANY, 3), logs, mallard, domesticDuck, teal, petTeal);

        // Mallard and its domestic form count once; the teal, and the pet log's species, count too: 3 of 3.
        assertThat(saved.getProgress()).isEqualTo(3);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    @Test
    void anyGenusBadgeIsEarnedWithThreeDifferentSpeciesOfOneGenus() {
        Species a = species("Anser anser", null);
        Species b = species("Anser albifrons", null);
        Species c = species("Anser fabalis", null);
        List<BirdLog> logs = List.of(log(a, Gender.UNKNOWN, LifeStage.ADULT, false),
                log(b, Gender.UNKNOWN, LifeStage.ADULT, false), log(c, Gender.UNKNOWN, LifeStage.ADULT, false));

        UserBadge saved = evaluateOne(badge(BadgeCriteriaType.SAME_GENUS_ANY, 3), logs, a, b, c);

        assertThat(saved.getProgress()).isEqualTo(3);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    @Test
    void theNamedGenusBadgeSharesTheSameRulesAndFoldsSubspeciesToo() {
        Species a = species("Anser anser", null);
        Species domestic = species("Anser anser domesticus", null);
        Species other = species("Anas crecca", null);
        List<BirdLog> logs = List.of(log(a, Gender.UNKNOWN, LifeStage.ADULT, false),
                log(domestic, Gender.UNKNOWN, LifeStage.ADULT, false), log(other, Gender.UNKNOWN, LifeStage.ADULT, false));
        Badge named = badge(BadgeCriteriaType.SAME_GENUS_SPECIES, 2);
        named.setCriteriaMetadata(Map.of("genus", "anser"));

        UserBadge saved = evaluateOne(named, logs, a, domestic, other);

        assertThat(saved.getProgress()).isEqualTo(1);
    }

    // ---- rare species ----

    @Test
    void rareMatchesTheEnglishStatusExactlyIgnoringCaseAndSpaces() {
        assertThat(BadgeServiceImpl.isRare(species("A b", "Endangered"))).isTrue();
        assertThat(BadgeServiceImpl.isRare(species("A b", "  critically ENDANGERED "))).isTrue();
        assertThat(BadgeServiceImpl.isRare(species("A b", "Vulnerable"))).isFalse();
        assertThat(BadgeServiceImpl.isRare(species("A b", "Near Threatened"))).isFalse();
        assertThat(BadgeServiceImpl.isRare(species("A b", "Least Concern"))).isFalse();
        assertThat(BadgeServiceImpl.isRare(species("A b", null))).isFalse();
        Species onlyTurkish = Species.builder().conservationStatus(Map.of("tr", "Tehlikede")).build();
        assertThat(BadgeServiceImpl.isRare(onlyTurkish)).isFalse();
    }

    @Test
    void rareBadgeCountsNonPetLogsOfEndangeredSpecies() {
        Species kakapo = species("Strigops habroptila", "Critically Endangered");
        Species stork = species("Geronticus eremita", "Endangered");
        Species sparrow = species("Passer domesticus", "Least Concern");
        List<BirdLog> logs = List.of(
                log(kakapo, Gender.UNKNOWN, LifeStage.ADULT, false),
                log(stork, Gender.UNKNOWN, LifeStage.ADULT, false),
                log(stork, Gender.UNKNOWN, LifeStage.ADULT, true),
                log(sparrow, Gender.UNKNOWN, LifeStage.ADULT, false));

        UserBadge saved = evaluateOne(badge(BadgeCriteriaType.RARE_SPECIES_LOGS, 2), logs, kakapo, stork, sparrow);

        assertThat(saved.getProgress()).isEqualTo(2);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    // ---- complete all ----

    @Test
    void completeAllIsCalculatedAfterTheOtherBadgesAndCountsThemAsEarned() {
        Badge first = badge(BadgeCriteriaType.TOTAL_LOGS, 1);
        Badge second = badge(BadgeCriteriaType.TOTAL_LOGS, 5);
        Badge all = badge(BadgeCriteriaType.ALL_OTHER_BADGES, 1);
        // The collection badge comes first in storage, to show the order does not depend on it.
        when(badgeRepository.findAll()).thenReturn(List.of(all, first, second));
        when(birdLogRepository.countByUserId(userId)).thenReturn(2L);
        when(userBadgeRepository.findByUserIdAndBadgeId(any(), any())).thenReturn(Optional.empty());
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(
                UserBadge.builder().userId(userId).badgeId(first.getId()).progress(2).earnedAt(Instant.now()).build()));

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        UserBadge last = captor.getAllValues().get(2);
        assertThat(last.getBadgeId()).isEqualTo(all.getId());
        assertThat(last.getProgress()).isEqualTo(1);
        assertThat(last.getEarnedAt()).isNull();
    }

    @Test
    void completeAllIsEarnedWhenEveryOtherBadgeIsEarned() {
        Badge first = badge(BadgeCriteriaType.TOTAL_LOGS, 1);
        Badge secretOne = badge(BadgeCriteriaType.PET_LOGS, 1);
        secretOne.setSecret(true);
        Badge all = badge(BadgeCriteriaType.ALL_OTHER_BADGES, 1);
        when(badgeRepository.findAll()).thenReturn(List.of(first, secretOne, all));
        when(birdLogRepository.countByUserId(userId)).thenReturn(2L);
        when(birdLogRepository.countByUserIdAndPetTrue(userId)).thenReturn(1L);
        when(userBadgeRepository.findByUserIdAndBadgeId(any(), any())).thenReturn(Optional.empty());
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(
                UserBadge.builder().userId(userId).badgeId(first.getId()).progress(2).earnedAt(Instant.now()).build(),
                UserBadge.builder().userId(userId).badgeId(secretOne.getId()).progress(1).earnedAt(Instant.now()).build()));

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        UserBadge last = captor.getAllValues().get(2);
        assertThat(last.getBadgeId()).isEqualTo(all.getId());
        assertThat(last.getProgress()).isEqualTo(2);
        assertThat(last.getEarnedAt()).isNotNull();
    }

    @Test
    void completeAllDoesNotAskForFavoriteSpeciesBadgesTheUserCannotEarn() {
        Badge first = badge(BadgeCriteriaType.TOTAL_LOGS, 1);
        Badge favorite = badge(BadgeCriteriaType.FAVORITE_SPECIES_LOGS, 1);
        Badge all = badge(BadgeCriteriaType.ALL_OTHER_BADGES, 1);
        when(badgeRepository.findAll()).thenReturn(List.of(first, favorite, all));
        when(birdLogRepository.countByUserId(userId)).thenReturn(1L);
        when(userBadgeRepository.findByUserIdAndBadgeId(any(), any())).thenReturn(Optional.empty());
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(
                UserBadge.builder().userId(userId).badgeId(first.getId()).progress(1).earnedAt(Instant.now()).build()));

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        UserBadge last = captor.getAllValues().get(2);
        assertThat(last.getProgress()).isEqualTo(1);
        assertThat(last.getEarnedAt()).isNotNull();
    }

    @Test
    void addingOrRemovingABadgeEvaluatesEveryUserAgain() {
        UUID removedId = UUID.randomUUID();
        when(badgeRepository.existsById(removedId)).thenReturn(true);
        when(userRepository.findAll()).thenReturn(List.of(User.builder().id(userId).build()));
        when(badgeRepository.findAll()).thenReturn(List.of());

        badgeService.delete(removedId);

        verify(badgeRepository).deleteById(removedId);
        verify(userBadgeRepository).deleteByBadgeId(removedId);
        verify(badgeRepository).findAll();
    }

    // ---- secret badges ----

    @Test
    void aLockedSecretBadgeShowsNothingButItsIdFlagsAndTier() {
        Badge secret = badge(BadgeCriteriaType.EARLY_BIRD_LOGS, 1);
        secret.setSecret(true);
        when(badgeRepository.findAll()).thenReturn(List.of(secret));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(
                UserBadge.builder().userId(userId).badgeId(secret.getId()).progress(0).build()));

        UserBadgeResponseDto item = badgeService.getByUserId(userId, Locale.ENGLISH).get(0);

        assertThat(item.badgeId()).isEqualTo(secret.getId());
        assertThat(item.secret()).isTrue();
        assertThat(item.earned()).isFalse();
        assertThat(item.tier()).isEqualTo(BadgeTier.SILVER);
        assertThat(item.badgeName()).isNull();
        assertThat(item.badgeIcon()).isNull();
        assertThat(item.badgeDescription()).isNull();
        assertThat(item.progress()).isNull();
        assertThat(item.targetValue()).isNull();
        assertThat(item.earnedAt()).isNull();
    }

    @Test
    void anEarnedSecretBadgeShowsEverythingUnderTheSameId() {
        Badge secret = badge(BadgeCriteriaType.EARLY_BIRD_LOGS, 1);
        secret.setSecret(true);
        Instant earnedAt = Instant.now();
        when(badgeRepository.findAll()).thenReturn(List.of(secret));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(
                UserBadge.builder().userId(userId).badgeId(secret.getId()).progress(1).earnedAt(earnedAt).build()));

        UserBadgeResponseDto item = badgeService.getByUserId(userId, Locale.forLanguageTag("tr")).get(0);

        assertThat(item.badgeId()).isEqualTo(secret.getId());
        assertThat(item.secret()).isTrue();
        assertThat(item.earned()).isTrue();
        assertThat(item.earnedAt()).isEqualTo(earnedAt);
        assertThat(item.badgeName()).isEqualTo("T");
        assertThat(item.badgeDescription()).isEqualTo("D");
        assertThat(item.badgeIcon()).isEqualTo("icon");
        assertThat(item.progress()).isEqualTo(1);
        assertThat(item.targetValue()).isEqualTo(1);
    }

    @Test
    void anAdminSeesTheFullDetailsOfALockedSecretBadge() {
        Badge secret = badge(BadgeCriteriaType.FAMILY_PORTRAIT, 3);
        secret.setSecret(true);
        when(badgeRepository.findAll()).thenReturn(List.of(secret));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(
                UserBadge.builder().userId(userId).badgeId(secret.getId()).progress(2).build()));

        UserBadgeResponseDto item = badgeService.getByUserIdForAdmin(userId, Locale.ENGLISH).get(0);

        assertThat(item.earned()).isFalse();
        assertThat(item.badgeName()).isEqualTo("B");
        assertThat(item.progress()).isEqualTo(2);
        assertThat(item.targetValue()).isEqualTo(3);
    }

    @Test
    void thePublicCatalogLeavesSecretBadgesOutButTheAdminCatalogKeepsThem() {
        Badge normal = badge(BadgeCriteriaType.TOTAL_LOGS, 1);
        Badge secret = badge(BadgeCriteriaType.EARLY_BIRD_LOGS, 1);
        secret.setSecret(true);
        when(badgeRepository.findAll()).thenReturn(List.of(normal, secret));

        List<BadgeResponseDto> publicCatalog = badgeService.getAll();
        List<BadgeResponseDto> adminCatalog = badgeService.getAllForAdmin();

        assertThat(publicCatalog).extracting(BadgeResponseDto::id).containsExactly(normal.getId());
        assertThat(adminCatalog).extracting(BadgeResponseDto::id).containsExactlyInAnyOrder(normal.getId(), secret.getId());
        assertThat(adminCatalog).filteredOn(BadgeResponseDto::secret).hasSize(1);
    }

    @Test
    void theCompleteAllTargetInTheListIsTheNumberOfOtherBadgesTheUserCanEarn() {
        Badge first = badge(BadgeCriteriaType.TOTAL_LOGS, 1);
        Badge secret = badge(BadgeCriteriaType.PET_LOGS, 1);
        secret.setSecret(true);
        Badge favorite = badge(BadgeCriteriaType.FAVORITE_SPECIES_LOGS, 1);
        Badge all = badge(BadgeCriteriaType.ALL_OTHER_BADGES, 1);
        when(badgeRepository.findAll()).thenReturn(List.of(first, secret, favorite, all));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of());

        List<UserBadgeResponseDto> list = badgeService.getByUserId(userId, Locale.ENGLISH);

        // The favorite-species badge is not in this user's list, and the secret one still counts.
        assertThat(list).hasSize(3);
        UserBadgeResponseDto collection = list.stream().filter(i -> i.badgeId().equals(all.getId())).findFirst().orElseThrow();
        assertThat(collection.targetValue()).isEqualTo(2);
    }

    @Test
    void creatingASecretBadgeStoresTheFlagAndAnOmittedFlagMeansNotSecret() {
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAll()).thenReturn(List.of());

        BadgeResponseDto secret = badgeService.create(new CreateBadgeRequestDto(Map.of("en", "S"), null, null,
                BadgeCriteriaType.EARLY_BIRD_LOGS, 1, null, BadgeTier.BRONZE, null, true));
        BadgeResponseDto plain = badgeService.create(new CreateBadgeRequestDto(Map.of("en", "P"), null, null,
                BadgeCriteriaType.TOTAL_LOGS, 1, null, BadgeTier.DIAMOND, null));

        assertThat(secret.secret()).isTrue();
        assertThat(plain.secret()).isFalse();
        assertThat(plain.tier()).isEqualTo(BadgeTier.DIAMOND);
    }

    // ---- the one-time completion email ----

    private Badge earnableCollection(Badge first) {
        Badge all = badge(BadgeCriteriaType.ALL_OTHER_BADGES, 1);
        when(badgeRepository.findAll()).thenReturn(List.of(first, all));
        when(birdLogRepository.countByUserId(userId)).thenReturn(1L);
        when(userBadgeRepository.findByUserIdAndBadgeId(any(), any())).thenReturn(Optional.empty());
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(
                UserBadge.builder().userId(userId).badgeId(first.getId()).progress(1).earnedAt(Instant.now()).build()));
        return all;
    }

    @Test
    void earningCompleteAllThroughALogChangeSendsTheEmailOnceInTheUsersLanguage() {
        earnableCollection(badge(BadgeCriteriaType.TOTAL_LOGS, 1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).email("bird@example.com").build()));
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.of(
                com.wingmark.backend.entity.UserSettings.builder().userId(userId).locale("tr").build()));

        badgeService.evaluateForUser(userId);

        verify(emailService).sendAllBadgesEarnedEmail("bird@example.com", "tr");
    }

    @Test
    void theEmailFallsBackToEnglishWithoutSettings() {
        earnableCollection(badge(BadgeCriteriaType.TOTAL_LOGS, 1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).email("bird@example.com").build()));
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        verify(emailService).sendAllBadgesEarnedEmail("bird@example.com", "en");
    }

    @Test
    void noSecondEmailWhenTheBadgeWasAlreadyEarned() {
        Badge first = badge(BadgeCriteriaType.TOTAL_LOGS, 1);
        Badge all = badge(BadgeCriteriaType.ALL_OTHER_BADGES, 1);
        when(badgeRepository.findAll()).thenReturn(List.of(first, all));
        when(birdLogRepository.countByUserId(userId)).thenReturn(1L);
        UserBadge alreadyEarned = UserBadge.builder().userId(userId).badgeId(all.getId()).progress(1).earnedAt(Instant.now()).build();
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, first.getId())).thenReturn(Optional.empty());
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, all.getId())).thenReturn(Optional.of(alreadyEarned));
        when(userBadgeRepository.findByUserId(userId)).thenReturn(List.of(
                UserBadge.builder().userId(userId).badgeId(first.getId()).progress(1).earnedAt(Instant.now()).build(), alreadyEarned));

        badgeService.evaluateForUser(userId);

        org.mockito.Mockito.verifyNoInteractions(emailService);
    }

    @Test
    void backFillingAfterABadgeChangeNeverSendsTheEmail() {
        Badge first = badge(BadgeCriteriaType.TOTAL_LOGS, 1);
        earnableCollection(first);
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAll()).thenReturn(List.of(User.builder().id(userId).build()));

        badgeService.create(new CreateBadgeRequestDto(Map.of("en", "Another"), null, null,
                BadgeCriteriaType.TOTAL_LOGS, 1, null, BadgeTier.BRONZE, null));

        org.mockito.Mockito.verifyNoInteractions(emailService);
    }

    @Test
    void aFailingEmailNeverBlocksTheBadge() {
        Badge all = earnableCollection(badge(BadgeCriteriaType.TOTAL_LOGS, 1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).email("bird@example.com").build()));
        when(userSettingsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new IllegalStateException("mail down")).when(emailService).sendAllBadgesEarnedEmail(any(), any());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.getBadgeId()).isEqualTo(all.getId());
            assertThat(saved.getEarnedAt()).isNotNull();
        });
    }
}
