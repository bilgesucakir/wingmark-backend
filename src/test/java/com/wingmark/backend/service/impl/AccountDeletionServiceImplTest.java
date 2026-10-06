package com.wingmark.backend.service.impl;

import com.wingmark.backend.entity.AccountDeletion;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.DeletionInitiator;
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
import com.wingmark.backend.service.EmailService;
import com.wingmark.backend.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private BirdLogRepository birdLogRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private SpeciesImageRepository speciesImageRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ConsentRepository consentRepository;
    @Mock private AccountDeletionRepository accountDeletionRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private AccountDeletionServiceImpl service;

    @Test
    void deletesConsentsRecordsAPersonalDataFreeAuditEntryAndConfirmsByEmailAfterDeletion() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("gone@example.com").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(birdLogRepository.findByUserId(userId)).thenReturn(List.of());

        service.deleteAccount(userId, DeletionInitiator.SELF);

        verify(consentRepository).deleteByUserId(userId);
        ArgumentCaptor<AccountDeletion> audit = ArgumentCaptor.forClass(AccountDeletion.class);
        verify(accountDeletionRepository).save(audit.capture());
        assertThat(audit.getValue().getUserId()).isEqualTo(userId);
        assertThat(audit.getValue().getInitiatedBy()).isEqualTo(DeletionInitiator.SELF);
        assertThat(audit.getValue().getRequestedAt()).isNotNull();
        assertThat(audit.getValue().getCompletedAt()).isAfterOrEqualTo(audit.getValue().getRequestedAt());

        // The user is gone before the confirmation goes out.
        InOrder order = inOrder(userRepository, emailService);
        order.verify(userRepository).deleteById(userId);
        order.verify(emailService).sendAccountDeletedEmail("gone@example.com");
    }

    @Test
    void anUnverifiedSignupIsDeletedWithoutEmailingTheNeverConfirmedAddress() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).email("maybe-not-theirs@example.com").build()));
        when(birdLogRepository.findByUserId(userId)).thenReturn(List.of());

        service.deleteAccount(userId, DeletionInitiator.UNVERIFIED);

        verify(userRepository).deleteById(userId);
        verify(accountDeletionRepository).save(org.mockito.ArgumentMatchers.any(AccountDeletion.class));
        verify(emailService, org.mockito.Mockito.never()).sendAccountDeletedEmail(org.mockito.ArgumentMatchers.any());
    }
}
