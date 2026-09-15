package com.wingmark.backend.service;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.UpdateBadgeRequestDto;
import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.UserBadge;
import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import com.wingmark.backend.service.impl.BadgeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    private BadgeServiceImpl badgeService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        badgeService = new BadgeServiceImpl(badgeRepository, userBadgeRepository, birdLogRepository);
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
        when(birdLogRepository.findByUserIdAndSpeciesIdIsNotNullAndPetFalse(userId))
                .thenReturn(List.of(logA, logB, logC));
        when(userBadgeRepository.findByUserIdAndBadgeId(userId, badge.getId())).thenReturn(Optional.empty());

        badgeService.evaluateForUser(userId);

        ArgumentCaptor<UserBadge> captor = ArgumentCaptor.forClass(UserBadge.class);
        verify(userBadgeRepository).save(captor.capture());
        assertThat(captor.getValue().getProgress()).isEqualTo(2);
        assertThat(captor.getValue().getEarnedAt()).isNotNull();
    }

    @Test
    void updatingAnExistingBadgeOverwritesItsFields() {
        UUID badgeId = UUID.randomUUID();
        Badge existing = Badge.builder()
                .id(badgeId).name("Old name").criteriaType(BadgeCriteriaType.TOTAL_LOGS).criteriaValue(5)
                .build();
        when(badgeRepository.findById(badgeId)).thenReturn(Optional.of(existing));
        when(badgeRepository.save(any(Badge.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBadgeRequestDto request = new UpdateBadgeRequestDto(
                "New name", "New description", "icon.png", BadgeCriteriaType.UNIQUE_SPECIES, 20, null, BadgeTier.GOLD);

        BadgeResponseDto response = badgeService.update(badgeId, request);

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.criteriaType()).isEqualTo(BadgeCriteriaType.UNIQUE_SPECIES);
        assertThat(response.criteriaValue()).isEqualTo(20);
        assertThat(response.tier()).isEqualTo(BadgeTier.GOLD);
    }

    @Test
    void updatingAMissingBadgeThrowsNotFound() {
        UUID badgeId = UUID.randomUUID();
        when(badgeRepository.findById(badgeId)).thenReturn(Optional.empty());

        UpdateBadgeRequestDto request = new UpdateBadgeRequestDto(
                "Name", null, null, BadgeCriteriaType.TOTAL_LOGS, 5, null, null);

        assertThatThrownBy(() -> badgeService.update(badgeId, request)).isInstanceOf(ResourceNotFoundException.class);
    }
}
