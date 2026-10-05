package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.InactivityProperties;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.DeletionInitiator;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.service.AccountDeletionService;
import com.wingmark.backend.service.EmailService;
import com.wingmark.backend.service.InactiveAccountService.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InactiveAccountServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private BirdLogRepository birdLogRepository;
    @Mock
    private AccountDeletionService accountDeletionService;
    @Mock
    private EmailService emailService;

    private InactiveAccountServiceImpl service(boolean dryRun, int maxWarnings, int maxDeletions) {
        return new InactiveAccountServiceImpl(userRepository, birdLogRepository, accountDeletionService, emailService,
                new InactivityProperties(true, dryRun, 730, 7, maxWarnings, maxDeletions, "0 30 3 * * *"));
    }

    private InactiveAccountServiceImpl service() {
        return service(false, 50, 20);
    }

    @BeforeEach
    void noBirdLogsByDefault() {
        org.mockito.Mockito.lenient().when(birdLogRepository.findFirstByUserIdOrderByUpdatedAtDesc(any())).thenReturn(Optional.empty());
    }

    private static User user(Instant lastLogin, Instant warnedAt) {
        User user = User.builder().email("quiet@example.com").role(Role.USER).lastLoginAt(lastLogin).inactivityWarningSentAt(warnedAt).build();
        user.setCreatedAt(lastLogin.minus(Duration.ofDays(10)));
        return user;
    }

    private static Instant daysAgo(long days) {
        return NOW.minus(Duration.ofDays(days));
    }

    @Test
    void anActiveAccountIsLeftAlone() {
        when(userRepository.findAll()).thenReturn(List.of(user(daysAgo(100), null)));

        Result result = service().runCleanup(NOW);

        assertThat(result.warned()).isZero();
        assertThat(result.deleted()).isZero();
        verify(emailService, never()).sendInactivityWarningEmail(any(), any());
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void anAccountSevenDaysBeforeTheTwoYearMarkGetsTheWarningWithTheDeletionDate() {
        User user = user(daysAgo(723), null);
        when(userRepository.findAll()).thenReturn(List.of(user));

        Result result = service().runCleanup(NOW);

        assertThat(result.warned()).isEqualTo(1);
        verify(emailService).sendInactivityWarningEmail("quiet@example.com", LocalDate.of(2026, 10, 12));
        assertThat(user.getInactivityWarningSentAt()).isEqualTo(NOW);
        verify(userRepository).save(user);
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void anAccountAlreadyPastTheLimitIsWarnedFirstAndGivenTheFullWarningPeriod() {
        User user = user(daysAgo(900), null);
        when(userRepository.findAll()).thenReturn(List.of(user));

        service().runCleanup(NOW);

        verify(emailService).sendInactivityWarningEmail("quiet@example.com", LocalDate.of(2026, 10, 12));
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void anAccountIsDeletedAtTwoYearsOnceTheWarningWasSentAtLeastSevenDaysAgo() {
        User user = user(daysAgo(731), daysAgo(8));
        UUID id = user.getId();
        when(userRepository.findAll()).thenReturn(List.of(user));

        Result result = service().runCleanup(NOW);

        assertThat(result.deleted()).isEqualTo(1);
        verify(accountDeletionService).deleteAccount(id, DeletionInitiator.INACTIVITY);
    }

    @Test
    void anAccountWarnedLessThanSevenDaysAgoIsNotDeletedYet() {
        when(userRepository.findAll()).thenReturn(List.of(user(daysAgo(731), daysAgo(3))));

        Result result = service().runCleanup(NOW);

        assertThat(result.deleted()).isZero();
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void anAccountThatBecameActiveAfterTheWarningIsNotDeletedAndIsWarnedAgainLater() {
        // Warned 20 days ago, but logged in 5 days ago: the new period of activity starts then.
        User user = user(daysAgo(5), daysAgo(20));
        when(userRepository.findAll()).thenReturn(List.of(user));

        Result result = service().runCleanup(NOW);

        assertThat(result.warned()).isZero();
        assertThat(result.deleted()).isZero();
    }

    @Test
    void creatingOrChangingASightingCountsAsActivity() {
        User user = user(daysAgo(800), daysAgo(30));
        BirdLog recent = BirdLog.builder().build();
        recent.setUpdatedAt(daysAgo(10));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(birdLogRepository.findFirstByUserIdOrderByUpdatedAtDesc(user.getId())).thenReturn(Optional.of(recent));

        Result result = service().runCleanup(NOW);

        assertThat(result.warned()).isZero();
        assertThat(result.deleted()).isZero();
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void adminsAndAccountsWithoutAnyTimestampAreNeverTouched() {
        User admin = user(daysAgo(900), null);
        admin.setRole(Role.ADMIN);
        User unknown = User.builder().email("x@example.com").role(Role.USER).build();
        when(userRepository.findAll()).thenReturn(List.of(admin, unknown));

        Result result = service().runCleanup(NOW);

        assertThat(result.warned()).isZero();
        assertThat(result.deleted()).isZero();
        verify(emailService, never()).sendInactivityWarningEmail(any(), any());
        verify(accountDeletionService, never()).deleteAccount(any(), any());
    }

    @Test
    void dryRunOnlyReportsAndChangesNothing() {
        User toWarn = user(daysAgo(724), null);
        User toDelete = user(daysAgo(740), daysAgo(10));
        when(userRepository.findAll()).thenReturn(List.of(toWarn, toDelete));

        Result result = service(true, 50, 20).runCleanup(NOW);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.warned()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
        verify(emailService, never()).sendInactivityWarningEmail(any(), any());
        verify(accountDeletionService, never()).deleteAccount(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void theNumberOfWarningsAndDeletionsPerRunIsCapped() {
        when(userRepository.findAll()).thenReturn(List.of(user(daysAgo(724), null), user(daysAgo(725), null),
                user(daysAgo(740), daysAgo(10)), user(daysAgo(741), daysAgo(11))));

        Result result = service(false, 1, 1).runCleanup(NOW);

        assertThat(result.warned()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
        verify(accountDeletionService).deleteAccount(any(), eq(DeletionInitiator.INACTIVITY));
    }
}
