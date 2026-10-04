package com.wingmark.backend.service.impl;

import com.wingmark.backend.entity.AccountDeletion;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.DeletionInitiator;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.AccountDeletionRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.ConsentRepository;
import com.wingmark.backend.repository.EmailVerificationTokenRepository;
import com.wingmark.backend.repository.PasswordResetTokenRepository;
import com.wingmark.backend.repository.RefreshTokenRepository;
import com.wingmark.backend.repository.SpeciesImageRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.service.AccountDeletionService;
import com.wingmark.backend.service.EmailService;
import com.wingmark.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Deletes a user and everything that belongs to them. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private final UserRepository userRepository;
    private final BirdLogRepository birdLogRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final SpeciesImageRepository speciesImageRepository;
    private final FileStorageService fileStorageService;
    private final ConsentRepository consentRepository;
    private final AccountDeletionRepository accountDeletionRepository;
    private final EmailService emailService;

    @Override
    public void deleteAccount(UUID userId, DeletionInitiator initiatedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        Instant requestedAt = Instant.now();
        // Kept only in memory, to send the confirmation after the account is gone.
        String email = user.getEmail();

        // Collected before anything is deleted, since the logs are about to go.
        Set<String> uploadedFiles = new LinkedHashSet<>();
        fileStorageService.storedFilename(user.getProfilePicture()).ifPresent(uploadedFiles::add);
        for (BirdLog log : birdLogRepository.findByUserId(userId)) {
            fileStorageService.storedFilename(log.getPhotoUrl()).ifPresent(uploadedFiles::add);
        }

        // The user document goes first: the JWT filter rejects tokens for a missing user,
        // so access is cut off immediately even if a later step fails. Mongo here runs
        // without multi-document transactions, so this ordering is the safety net.
        userRepository.deleteById(userId);
        refreshTokenRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        emailVerificationTokenRepository.deleteByUserId(userId);
        userSettingsRepository.deleteByUserId(userId);
        userBadgeRepository.deleteByUserId(userId);
        birdLogRepository.deleteByUserId(userId);
        consentRepository.deleteByUserId(userId);

        for (String filename : uploadedFiles) {
            if (isStillReferenced(filename)) {
                // Upload URLs aren't owner-tagged, so another account (or a species image) may
                // point at the same file - never delete someone else's photo along with this user.
                log.info("Keeping uploaded file {} - still referenced elsewhere", filename);
                continue;
            }
            fileStorageService.delete(filename);
        }

        accountDeletionRepository.save(AccountDeletion.builder()
                .userId(userId)
                .initiatedBy(initiatedBy)
                .requestedAt(requestedAt)
                .completedAt(Instant.now())
                .build());
        emailService.sendAccountDeletedEmail(email);

        log.info("Deleted user {} ({}) and all associated data ({} uploaded file(s) considered)",
                userId, initiatedBy, uploadedFiles.size());
    }

    private boolean isStillReferenced(String filename) {
        String suffix = "/uploads/" + filename;
        return birdLogRepository.existsByPhotoUrlEndingWith(suffix)
                || userRepository.existsByProfilePictureEndingWith(suffix)
                || speciesImageRepository.existsByImageUrlEndingWith(suffix);
    }
}
