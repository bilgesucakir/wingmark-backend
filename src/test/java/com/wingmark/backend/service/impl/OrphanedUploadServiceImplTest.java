package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.UploadCleanupProperties;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.FileStorageService.StoredFileInfo;
import com.wingmark.backend.service.OrphanedUploadService.Result;
import com.wingmark.backend.service.UploadReferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanedUploadServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private UploadReferences uploadReferences;

    private OrphanedUploadServiceImpl service(boolean dryRun, int maxPerRun) {
        return new OrphanedUploadServiceImpl(fileStorageService, uploadReferences,
                new UploadCleanupProperties(true, dryRun, 7, maxPerRun, "0 45 4 * * *"));
    }

    private static StoredFileInfo file(String name, long daysOld, long size) {
        return new StoredFileInfo(name, NOW.minus(Duration.ofDays(daysOld)), size);
    }

    @Test
    void anOldUnreferencedPhotoIsDeletedThroughItsPhotoNameWhichAlsoRemovesTheThumbnail() {
        when(fileStorageService.listFiles()).thenReturn(List.of(file("a.jpg", 30, 1000), file("a_thumb.jpg", 30, 100)));
        when(uploadReferences.referencedFilenames()).thenReturn(Set.of());

        Result result = service(false, 100).cleanup(NOW);

        assertThat(result.photos()).isEqualTo(1);
        assertThat(result.bytes()).isEqualTo(1100);
        verify(fileStorageService).delete("a.jpg");
        verify(fileStorageService, never()).delete("a_thumb.jpg");
    }

    @Test
    void aPhotoStillUsedKeepsItsThumbnailToo() {
        when(fileStorageService.listFiles()).thenReturn(List.of(file("a.png", 30, 1000), file("a_thumb.jpg", 30, 100)));
        when(uploadReferences.referencedFilenames()).thenReturn(Set.of("a.png"));

        Result result = service(false, 100).cleanup(NOW);

        assertThat(result.photos()).isZero();
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void aPhotoYoungerThanTheGracePeriodIsKeptBecauseItsLogMayNotBeSavedYet() {
        when(fileStorageService.listFiles()).thenReturn(List.of(file("new.jpg", 2, 1000), file("new_thumb.jpg", 2, 100)));
        when(uploadReferences.referencedFilenames()).thenReturn(Set.of());

        Result result = service(false, 100).cleanup(NOW);

        assertThat(result.photos()).isZero();
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void aStrayThumbnailWithoutItsPhotoIsDeletedByItsOwnName() {
        when(fileStorageService.listFiles()).thenReturn(List.of(file("gone_thumb.jpg", 30, 100)));
        when(uploadReferences.referencedFilenames()).thenReturn(Set.of());

        service(false, 100).cleanup(NOW);

        verify(fileStorageService).delete("gone_thumb.jpg");
    }

    @Test
    void dryRunOnlyReportsAndDeletesNothing() {
        when(fileStorageService.listFiles()).thenReturn(List.of(file("a.jpg", 30, 1000), file("a_thumb.jpg", 30, 100)));
        when(uploadReferences.referencedFilenames()).thenReturn(Set.of());

        Result result = service(true, 100).cleanup(NOW);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.photos()).isEqualTo(1);
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void theNumberOfPhotosDeletedPerRunIsCapped() {
        when(fileStorageService.listFiles()).thenReturn(List.of(file("a.jpg", 30, 10), file("b.jpg", 30, 10), file("c.jpg", 30, 10)));
        when(uploadReferences.referencedFilenames()).thenReturn(Set.of());

        Result result = service(false, 2).cleanup(NOW);

        assertThat(result.photos()).isEqualTo(2);
        verify(fileStorageService).delete("a.jpg");
        verify(fileStorageService).delete("b.jpg");
        verify(fileStorageService, never()).delete("c.jpg");
    }

    @Test
    void theBaseNameIgnoresTheExtensionAndTheThumbnailSuffix() {
        assertThat(OrphanedUploadServiceImpl.baseOf("abc.jpg")).isEqualTo("abc");
        assertThat(OrphanedUploadServiceImpl.baseOf("abc.png")).isEqualTo("abc");
        assertThat(OrphanedUploadServiceImpl.baseOf("abc_thumb.jpg")).isEqualTo("abc");
    }
}
