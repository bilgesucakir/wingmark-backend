package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.UnverifiedAccountProperties;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.DeletionInitiator;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.service.AccountDeletionService;
import com.wingmark.backend.service.UnverifiedAccountService.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnverifiedAccountServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountDeletionService accountDeletionService;

    private UnverifiedAccountServiceImpl service(boolean dryRun, int maxPerRun) {
        return new UnverifiedAccountServiceImpl(userRepository, accountDeletionService,
                new UnverifiedAccountProperties(true, dryRun, 7, maxPerRun, "0 15 4 * * *"));
    }

    private static User unverified(Role role, Instant lastLoginAt) {
        return User.builder().email("x@example.com").role(role).emailVerified(false).lastLoginAt(lastLoginAt).build();
    }

    @Test
    void onlyAccountsOlderThanTheConfiguredAgeAreAskedFor() {
        when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(any())).thenReturn(List.of());

        service(false, 100).purge(NOW);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).findByEmailVerifiedFalseAndCreatedAtBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void abandonedSignupsAreDeletedThroughTheNormalDeletionWithTheUnverifiedInitiator() {
        User user = unverified(Role.USER, null);
        when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(any())).thenReturn(List.of(user));

        Result result = service(false, 100).purge(NOW);

        assertThat(result.deleted()).isEqualTo(1);
        verify(accountDeletionService).deleteAccount(user.getId(), DeletionInitiator.UNVERIFIED);
    }

    @Test
    void adminsAndAccountsThatEverLoggedInAreKept() {
        when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(any())).thenReturn(List.of(
                unverified(Role.ADMIN, null), unverified(Role.USER, NOW.minusSeconds(3600))));

        Result result = service(false, 100).purge(NOW);

        assertThat(result.deleted()).isZero();
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void dryRunOnlyCountsAndDeletesNothing() {
        when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(any())).thenReturn(List.of(unverified(Role.USER, null)));

        Result result = service(true, 100).purge(NOW);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.deleted()).isEqualTo(1);
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void theNumberOfDeletionsPerRunIsCapped() {
        when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(any())).thenReturn(List.of(
                unverified(Role.USER, null), unverified(Role.USER, null), unverified(Role.USER, null)));

        Result result = service(false, 2).purge(NOW);

        assertThat(result.deleted()).isEqualTo(2);
        verify(accountDeletionService, org.mockito.Mockito.times(2)).deleteAccount(any(UUID.class), any());
    }

    @Test
    void aFailedDeletionDoesNotStopTheRunOrCountAsDeleted() {
        User broken = unverified(Role.USER, null);
        User fine = unverified(Role.USER, null);
        when(userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(any())).thenReturn(List.of(broken, fine));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(accountDeletionService)
                .deleteAccount(broken.getId(), DeletionInitiator.UNVERIFIED);

        Result result = service(false, 100).purge(NOW);

        assertThat(result.deleted()).isEqualTo(1);
        verify(accountDeletionService).deleteAccount(fine.getId(), DeletionInitiator.UNVERIFIED);
    }
}
