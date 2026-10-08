package com.wingmark.backend.service.impl;

import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesImageRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Deletes an uploaded photo (and its thumbnail) once nothing refers to it any more. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadedFileCleaner {

    private final FileStorageService fileStorageService;
    private final BirdLogRepository birdLogRepository;
    private final UserRepository userRepository;
    private final SpeciesImageRepository speciesImageRepository;

    /**
     * Deletes the stored file behind a photo URL unless a bird log, a profile picture or a species image still uses it.
     * URLs that are not our own uploads are ignored. Call it after the reference has been removed.
     */
    public void deleteIfUnreferenced(String photoUrl) {
        fileStorageService.storedFilename(photoUrl).ifPresent(filename -> {
            if (isStillReferenced(filename)) {
                log.info("Keeping uploaded file {} - still referenced elsewhere", filename);
                return;
            }
            fileStorageService.delete(filename);
        });
    }

    private boolean isStillReferenced(String filename) {
        String suffix = "/uploads/" + filename;
        return birdLogRepository.existsByPhotoUrlEndingWith(suffix)
                || userRepository.existsByProfilePictureEndingWith(suffix)
                || speciesImageRepository.existsByImageUrlEndingWith(suffix);
    }
}
