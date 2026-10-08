package com.wingmark.backend.service.impl;

import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesImageRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadedFileCleanerTest {

    @Mock private FileStorageService fileStorageService;
    @Mock private BirdLogRepository birdLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private SpeciesImageRepository speciesImageRepository;

    @InjectMocks
    private UploadedFileCleaner cleaner;

    @Test
    void anUnreferencedPhotoIsDeleted() {
        when(fileStorageService.storedFilename("/uploads/a.jpg")).thenReturn(Optional.of("a.jpg"));

        cleaner.deleteIfUnreferenced("/uploads/a.jpg");

        verify(fileStorageService).delete("a.jpg");
    }

    @Test
    void aPhotoStillUsedByAnotherLogIsKept() {
        when(fileStorageService.storedFilename("/uploads/a.jpg")).thenReturn(Optional.of("a.jpg"));
        when(birdLogRepository.existsByPhotoUrlEndingWith("/uploads/a.jpg")).thenReturn(true);

        cleaner.deleteIfUnreferenced("/uploads/a.jpg");

        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void aPhotoUsedAsAProfilePictureIsKept() {
        when(fileStorageService.storedFilename("/uploads/a.jpg")).thenReturn(Optional.of("a.jpg"));
        when(userRepository.existsByProfilePictureEndingWith("/uploads/a.jpg")).thenReturn(true);

        cleaner.deleteIfUnreferenced("/uploads/a.jpg");

        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void aPhotoUsedByASpeciesImageIsKept() {
        when(fileStorageService.storedFilename("/uploads/a.jpg")).thenReturn(Optional.of("a.jpg"));
        when(speciesImageRepository.existsByImageUrlEndingWith("/uploads/a.jpg")).thenReturn(true);

        cleaner.deleteIfUnreferenced("/uploads/a.jpg");

        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void externalUrlsAndMissingUrlsAreIgnored() {
        when(fileStorageService.storedFilename("https://example.com/x.jpg")).thenReturn(Optional.empty());
        when(fileStorageService.storedFilename(null)).thenReturn(Optional.empty());

        cleaner.deleteIfUnreferenced("https://example.com/x.jpg");
        cleaner.deleteIfUnreferenced(null);

        verify(fileStorageService, never()).delete(any());
    }
}
