package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.birdlog.BirdLogResponseDto;
import com.wingmark.backend.dto.birdlog.CreateBirdLogRequestDto;
import com.wingmark.backend.dto.birdlog.UpdateBirdLogRequestDto;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.enums.SpeciesStatus;
import com.wingmark.backend.exception.InvalidReferenceException;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.service.BadgeService;
import com.wingmark.backend.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BirdLogServiceImplTest {

    @Mock
    private BirdLogRepository birdLogRepository;
    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private BadgeService badgeService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private UploadedFileCleaner uploadedFileCleaner;

    private BirdLogServiceImpl birdLogService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        birdLogService = new BirdLogServiceImpl(birdLogRepository, speciesRepository, badgeService, fileStorageService, uploadedFileCleaner);
    }

    @Test
    void createRejectsUnknownSpecies() {
        UUID speciesId = UUID.randomUUID();
        when(speciesRepository.existsById(speciesId)).thenReturn(false);

        CreateBirdLogRequestDto request = new CreateBirdLogRequestDto(
                speciesId, SpeciesStatus.CONFIDENT, false, "Tweety",
                LifeStage.ADULT, Gender.MALE, null, null, 40.0, 29.0, null, null);

        assertThatThrownBy(() -> birdLogService.create(userId, request, Locale.ENGLISH))
                .isInstanceOf(InvalidReferenceException.class);
    }

    @Test
    void createWithUnknownSpeciesIsAllowed() {
        when(birdLogRepository.save(any(BirdLog.class))).thenAnswer(inv -> {
            BirdLog log = inv.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });

        CreateBirdLogRequestDto request = new CreateBirdLogRequestDto(
                null, null, false, null,
                LifeStage.UNKNOWN, Gender.UNKNOWN, null, "not sure what this was", 40.0, 29.0, null, null);

        BirdLogResponseDto response = birdLogService.create(userId, request, Locale.ENGLISH);

        assertThat(response.speciesId()).isNull();
        assertThat(response.speciesStatus()).isNull();
        verify(badgeService).evaluateForUser(userId);
    }

    @Test
    void createTriggersBadgeEvaluationForTheOwningUser() {
        when(birdLogRepository.save(any(BirdLog.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateBirdLogRequestDto request = new CreateBirdLogRequestDto(
                null, null, true, "Pico",
                LifeStage.ADULT, Gender.MALE, null, null, 10.0, 20.0, null, null);

        birdLogService.create(userId, request, Locale.ENGLISH);

        verify(badgeService).evaluateForUser(userId);
    }

    @Test
    void getThrowsNotFoundWhenLogBelongsToAnotherUser() {
        UUID logId = UUID.randomUUID();
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> birdLogService.getById(userId, logId, Locale.ENGLISH))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateOnlyAffectsOwnedLog() {
        UUID logId = UUID.randomUUID();
        BirdLog existing = BirdLog.builder()
                .id(logId)
                .userId(userId)
                .lifeStage(LifeStage.UNKNOWN)
                .gender(Gender.UNKNOWN)
                .latitude(1.0)
                .longitude(1.0)
                .build();

        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(existing));
        when(birdLogRepository.save(any(BirdLog.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateBirdLogRequestDto request = new UpdateBirdLogRequestDto(
                null, null, false, "Renamed",
                LifeStage.ADULT, Gender.FEMALE, null, "updated note", 2.0, 2.0, "Some park", null);

        BirdLogResponseDto response = birdLogService.update(userId, logId, request, Locale.ENGLISH);

        assertThat(response.customName()).isEqualTo("Renamed");
        assertThat(response.lifeStage()).isEqualTo(LifeStage.ADULT);
        verify(badgeService).evaluateForUser(userId);
    }

    @Test
    void legacyLogWithoutObservedAtReportsItsCreatedAtInstead() {
        UUID logId = UUID.randomUUID();
        BirdLog legacy = BirdLog.builder().id(logId).userId(userId)
                .lifeStage(LifeStage.ADULT).gender(Gender.UNKNOWN).latitude(1.0).longitude(1.0)
                .build();
        legacy.setCreatedAt(java.time.Instant.parse("2025-11-20T10:00:00Z"));
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(legacy));

        BirdLogResponseDto response = birdLogService.getById(userId, logId, Locale.ENGLISH);

        assertThat(response.observedAt()).isEqualTo(java.time.Instant.parse("2025-11-20T10:00:00Z"));
    }

    @Test
    void deleteReEvaluatesBadgesAfterRemoval() {
        UUID logId = UUID.randomUUID();
        BirdLog existing = BirdLog.builder().id(logId).userId(userId).build();
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(existing));

        birdLogService.delete(userId, logId);

        verify(birdLogRepository).delete(existing);
        verify(badgeService).evaluateForUser(userId);
    }

    private BirdLog logWithPhoto(UUID logId, String photoUrl) {
        return BirdLog.builder().id(logId).userId(userId).lifeStage(LifeStage.UNKNOWN).gender(Gender.UNKNOWN)
                .latitude(1.0).longitude(1.0).photoUrl(photoUrl).build();
    }

    private UpdateBirdLogRequestDto updateWithPhoto(String photoUrl) {
        return new UpdateBirdLogRequestDto(null, null, false, null, LifeStage.UNKNOWN, Gender.UNKNOWN, photoUrl, null, 1.0, 1.0, null, null);
    }

    @Test
    void deleteReleasesThePhotoOfTheDeletedLog() {
        UUID logId = UUID.randomUUID();
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(logWithPhoto(logId, "/uploads/a.jpg")));

        birdLogService.delete(userId, logId);

        verify(uploadedFileCleaner).deleteIfUnreferenced("/uploads/a.jpg");
    }

    @Test
    void updatingToADifferentPhotoReleasesTheOldOne() {
        UUID logId = UUID.randomUUID();
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(logWithPhoto(logId, "/uploads/old.jpg")));
        when(birdLogRepository.save(any(BirdLog.class))).thenAnswer(inv -> inv.getArgument(0));

        birdLogService.update(userId, logId, updateWithPhoto("/uploads/new.jpg"), Locale.ENGLISH);

        verify(uploadedFileCleaner).deleteIfUnreferenced("/uploads/old.jpg");
    }

    @Test
    void removingThePhotoFromALogReleasesIt() {
        UUID logId = UUID.randomUUID();
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(logWithPhoto(logId, "/uploads/old.jpg")));
        when(birdLogRepository.save(any(BirdLog.class))).thenAnswer(inv -> inv.getArgument(0));

        birdLogService.update(userId, logId, updateWithPhoto(null), Locale.ENGLISH);

        verify(uploadedFileCleaner).deleteIfUnreferenced("/uploads/old.jpg");
    }

    @Test
    void keepingTheSamePhotoOrHavingNoneReleasesNothing() {
        UUID logId = UUID.randomUUID();
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(logWithPhoto(logId, "/uploads/same.jpg")));
        when(birdLogRepository.save(any(BirdLog.class))).thenAnswer(inv -> inv.getArgument(0));

        birdLogService.update(userId, logId, updateWithPhoto("/uploads/same.jpg"), Locale.ENGLISH);

        UUID noPhotoLog = UUID.randomUUID();
        when(birdLogRepository.findByIdAndUserId(noPhotoLog, userId)).thenReturn(Optional.of(logWithPhoto(noPhotoLog, null)));
        birdLogService.update(userId, noPhotoLog, updateWithPhoto("/uploads/new.jpg"), Locale.ENGLISH);

        verify(uploadedFileCleaner, never()).deleteIfUnreferenced(any());
    }

    @Test
    void theResponseCarriesTheThumbnailUrlOfTheLogsPhoto() {
        UUID logId = UUID.randomUUID();
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(logWithPhoto(logId, "/uploads/a.jpg")));
        when(fileStorageService.thumbnailUrl("/uploads/a.jpg")).thenReturn(Optional.of("/uploads/a_thumb.jpg"));

        BirdLogResponseDto response = birdLogService.getById(userId, logId, Locale.ENGLISH);

        assertThat(response.photoUrl()).isEqualTo("/uploads/a.jpg");
        assertThat(response.photoThumbnailUrl()).isEqualTo("/uploads/a_thumb.jpg");
    }

    @Test
    void aLogWithoutAnOwnPhotoHasNoThumbnailUrl() {
        UUID logId = UUID.randomUUID();
        when(birdLogRepository.findByIdAndUserId(logId, userId)).thenReturn(Optional.of(logWithPhoto(logId, "https://example.com/x.jpg")));

        assertThat(birdLogService.getById(userId, logId, Locale.ENGLISH).photoThumbnailUrl()).isNull();
    }
}
