package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.R2MigrationProperties;
import com.wingmark.backend.entity.UploadedFile;
import com.wingmark.backend.repository.UploadedFileRepository;
import com.wingmark.backend.service.FileStorageService.StoredFile;
import com.wingmark.backend.service.FileStorageService.StoredFileInfo;
import com.wingmark.backend.service.UploadMigrationService.Result;
import com.wingmark.backend.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadMigrationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Mock
    private GridFsFileStorageServiceImpl gridFs;
    @Mock
    private ObjectStore objectStore;
    @Mock
    private UploadedFileRepository uploadedFiles;

    private UploadMigrationServiceImpl service(boolean dryRun, int maxPerRun) {
        return new UploadMigrationServiceImpl(gridFs, objectStore, uploadedFiles, new R2MigrationProperties(true, dryRun, maxPerRun, "0 20 * * * *"));
    }

    private static StoredFileInfo info(String name, long size) {
        return new StoredFileInfo(name, NOW, size);
    }

    private static StoredFile file(int... bytes) {
        byte[] content = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            content[i] = (byte) bytes[i];
        }
        return new StoredFile(content, "image/jpeg");
    }

    @Test
    void aPhotoAndItsThumbnailAreCopiedVerifiedBySizeAndOnlyThenIndexedWithTheOwner() {
        UUID owner = UUID.randomUUID();
        when(uploadedFiles.findAll()).thenReturn(List.of());
        when(gridFs.listFiles()).thenReturn(List.of(info("a.jpg", 3), info("a_thumb.jpg", 2)));
        when(objectStore.size("photos/a.jpg")).thenReturn(Optional.empty(), Optional.of(3L));
        when(objectStore.size("photos/a_thumb.jpg")).thenReturn(Optional.empty(), Optional.of(2L));
        when(gridFs.load("a.jpg")).thenReturn(Optional.of(file(1, 2, 3)));
        when(gridFs.load("a_thumb.jpg")).thenReturn(Optional.of(file(1, 2)));
        when(uploadedFiles.findByFilename("a.jpg")).thenReturn(Optional.empty());
        when(gridFs.ownerOf("a.jpg")).thenReturn(Optional.of(owner));

        Result result = service(false, 50).migrate();

        assertThat(result.copied()).isEqualTo(1);
        assertThat(result.bytes()).isEqualTo(5);
        verify(objectStore).put(eq("photos/a.jpg"), any(), eq("image/jpeg"));
        verify(objectStore).put(eq("photos/a_thumb.jpg"), any(), eq("image/jpeg"));
        ArgumentCaptor<UploadedFile> indexed = ArgumentCaptor.forClass(UploadedFile.class);
        verify(uploadedFiles, times(1)).save(indexed.capture());
        assertThat(indexed.getValue().getFilename()).isEqualTo("a.jpg");
        assertThat(indexed.getValue().getOwnerId()).isEqualTo(owner);
    }

    @Test
    void aPhotoAlreadyInTheIndexCostsNoRequestToR2() {
        when(uploadedFiles.findAll()).thenReturn(List.of(UploadedFile.builder().filename("a.jpg").build()));
        when(gridFs.listFiles()).thenReturn(List.of(info("a.jpg", 3), info("a_thumb.jpg", 2)));

        Result result = service(false, 50).migrate();

        assertThat(result.copied()).isZero();
        verify(objectStore, never()).size(anyString());
        verify(objectStore, never()).put(anyString(), any(), anyString());
    }

    @Test
    void aPhotoAlreadyFullyInR2ButNotIndexedIsOnlyIndexed() {
        when(uploadedFiles.findAll()).thenReturn(List.of());
        when(gridFs.listFiles()).thenReturn(List.of(info("a.jpg", 3)));
        when(objectStore.size("photos/a.jpg")).thenReturn(Optional.of(3L));
        when(uploadedFiles.findByFilename("a.jpg")).thenReturn(Optional.empty());
        when(gridFs.ownerOf("a.jpg")).thenReturn(Optional.empty());

        Result result = service(false, 50).migrate();

        assertThat(result.alreadyThere()).isEqualTo(1);
        assertThat(result.copied()).isZero();
        verify(objectStore, never()).put(anyString(), any(), anyString());
        verify(uploadedFiles).save(any(UploadedFile.class));
    }

    @Test
    void aCopyThatDoesNotVerifyBySizeFailsTheWholePhotoAndIsNotIndexed() {
        when(uploadedFiles.findAll()).thenReturn(List.of());
        when(gridFs.listFiles()).thenReturn(List.of(info("a.jpg", 3)));
        when(objectStore.size("photos/a.jpg")).thenReturn(Optional.empty(), Optional.of(2L));
        when(gridFs.load("a.jpg")).thenReturn(Optional.of(file(1, 2, 3)));

        Result result = service(false, 50).migrate();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.copied()).isZero();
        verify(uploadedFiles, never()).save(any());
    }

    @Test
    void aFailingPhotoDoesNotStopTheRun() {
        when(uploadedFiles.findAll()).thenReturn(List.of());
        when(gridFs.listFiles()).thenReturn(List.of(info("a.jpg", 1), info("b.jpg", 1)));
        when(objectStore.size("photos/a.jpg")).thenReturn(Optional.empty());
        when(objectStore.size("photos/b.jpg")).thenReturn(Optional.empty(), Optional.of(1L));
        when(gridFs.load("a.jpg")).thenThrow(new IllegalStateException("boom"));
        when(gridFs.load("b.jpg")).thenReturn(Optional.of(file(1)));
        when(uploadedFiles.findByFilename("b.jpg")).thenReturn(Optional.empty());
        when(gridFs.ownerOf("b.jpg")).thenReturn(Optional.empty());

        Result result = service(false, 50).migrate();

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.copied()).isEqualTo(1);
    }

    @Test
    void theDryRunOnlyCountsAndWritesNothing() {
        when(uploadedFiles.findAll()).thenReturn(List.of());
        when(gridFs.listFiles()).thenReturn(List.of(info("a.jpg", 10), info("b.jpg", 20)));
        when(objectStore.size(anyString())).thenReturn(Optional.empty());

        Result result = service(true, 50).migrate();

        assertThat(result.dryRun()).isTrue();
        assertThat(result.copied()).isEqualTo(2);
        assertThat(result.bytes()).isEqualTo(30);
        verify(objectStore, never()).put(anyString(), any(), anyString());
        verify(gridFs, never()).load(anyString());
        verify(uploadedFiles, never()).save(any());
    }

    @Test
    void theNumberOfPhotosLookedAtPerRunIsCappedWhichBoundsTheRequests() {
        when(uploadedFiles.findAll()).thenReturn(List.of());
        when(gridFs.listFiles()).thenReturn(List.of(info("a.jpg", 1), info("b.jpg", 1), info("c.jpg", 1)));
        when(objectStore.size(anyString())).thenReturn(Optional.empty());

        Result result = service(true, 2).migrate();

        assertThat(result.copied()).isEqualTo(2);
        verify(objectStore, times(2)).size(anyString());
    }

    @Test
    void theMigrationNeverDeletesFromTheDatabase() {
        when(uploadedFiles.findAll()).thenReturn(List.of());
        when(gridFs.listFiles()).thenReturn(List.of(info("a.jpg", 1)));
        when(objectStore.size("photos/a.jpg")).thenReturn(Optional.empty(), Optional.of(1L));
        when(gridFs.load("a.jpg")).thenReturn(Optional.of(file(1)));
        when(uploadedFiles.findByFilename("a.jpg")).thenReturn(Optional.empty());
        when(gridFs.ownerOf("a.jpg")).thenReturn(Optional.empty());

        service(false, 50).migrate();

        verify(gridFs, never()).delete(anyString());
    }
}
