package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.AppProperties;
import com.wingmark.backend.dto.auth.AuthResponseDto;
import com.wingmark.backend.dto.auth.ForgotPasswordRequestDto;
import com.wingmark.backend.dto.auth.LoginRequestDto;
import com.wingmark.backend.dto.auth.RegisterRequestDto;
import com.wingmark.backend.dto.auth.RegisterResponseDto;
import com.wingmark.backend.dto.auth.ResendVerificationEmailRequestDto;
import com.wingmark.backend.dto.auth.ResetPasswordRequestDto;
import com.wingmark.backend.entity.EmailVerificationToken;
import com.wingmark.backend.entity.PasswordResetToken;
import com.wingmark.backend.entity.RefreshToken;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.exception.DuplicateResourceException;
import com.wingmark.backend.exception.InvalidCredentialsException;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.UnauthorizedActionException;
import com.wingmark.backend.exception.InvalidTokenException;
import com.wingmark.backend.exception.UnverifiedEmailException;
import com.wingmark.backend.repository.EmailVerificationTokenRepository;
import com.wingmark.backend.repository.PasswordResetTokenRepository;
import com.wingmark.backend.repository.RefreshTokenRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.security.JwtTokenProvider;
import com.wingmark.backend.security.PasswordPolicy;
import com.wingmark.backend.service.ConsentService;
import com.wingmark.backend.service.EmailService;
import com.wingmark.backend.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private EmailService emailService;
    @Mock
    private ConsentService consentService;
    @Mock
    private PasswordPolicy passwordPolicy;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AppProperties appProperties = new AppProperties("http://localhost:8080");

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, userSettingsRepository, refreshTokenRepository,
                passwordResetTokenRepository, emailVerificationTokenRepository,
                passwordEncoder, jwtTokenProvider, emailService, appProperties, consentService, passwordPolicy);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        RegisterRequestDto request = new RegisterRequestDto("taken@example.com", "birdsong2026", "newuser", "A", "B", null, null, true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("takenname")).thenReturn(true);

        RegisterRequestDto request = new RegisterRequestDto("fresh@example.com", "birdsong2026", "takenname", "A", "B", null, null, true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void registerSavesUnverifiedUserAndSendsVerificationEmailWithoutIssuingTokens() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegisterRequestDto request = new RegisterRequestDto("fresh@example.com", "birdsong2026", "freshuser", "A", "B", null, null, true);
        RegisterResponseDto response = authService.register(request);

        assertThat(response.userId()).isNotNull();
        assertThat(response.email()).isEqualTo("fresh@example.com");
        assertThat(response.emailVerified()).isFalse();

        verify(userRepository).save(any(User.class));
        verify(userSettingsRepository).save(any());
        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any(RefreshToken.class));
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("fresh@example.com"), any());
        verify(consentService).requireAcceptedAtSignup(null, null, true);
        verify(consentService).recordSignupConsents(response.userId());
    }

    @Test
    void registerFailsBeforeCreatingAnythingWhenTermsAreNotAccepted() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        org.mockito.Mockito.doThrow(new BadRequestException(ErrorCode.TERMS_NOT_ACCEPTED, "accept the terms"))
                .when(consentService).requireAcceptedAtSignup(null, null, true);

        RegisterRequestDto request = new RegisterRequestDto("fresh@example.com", "birdsong2026", "freshuser", "A", "B", null, null, true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TERMS_NOT_ACCEPTED));
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        LoginRequestDto request = new LoginRequestDto("nobody@example.com", "whatever1");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("correct-password"))
                .username("someuser")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        LoginRequestDto request = new LoginRequestDto("user@example.com", "wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("correct-password"))
                .username("someuser")
                .role(Role.USER)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), anyInt())).thenReturn("access-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(2_592_000_000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponseDto response = authService.login(new LoginRequestDto("user@example.com", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void loginRejectsUnverifiedEmail() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("unverified@example.com")
                .passwordHash(passwordEncoder.encode("correct-password"))
                .username("unverifieduser")
                .role(Role.USER)
                .emailVerified(false)
                .build();
        when(userRepository.findByEmailIgnoreCase("unverified@example.com")).thenReturn(Optional.of(user));

        LoginRequestDto request = new LoginRequestDto("unverified@example.com", "correct-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnverifiedEmailException.class);
    }

    @Test
    void refreshRejectsExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash(TokenHasher.sha256("raw-token"))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("raw-token")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshRevokesOldTokenAndIssuesNewOne() {
        UUID userId = UUID.randomUUID();
        RefreshToken active = RefreshToken.builder()
                .userId(userId)
                .tokenHash(TokenHasher.sha256("raw-token"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).emailVerified(true).build();

        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("raw-token")))
                .thenReturn(Optional.of(active));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), anyInt())).thenReturn("new-access-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(2_592_000_000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponseDto response = authService.refresh("raw-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(active.getRevokedAt()).isNotNull();
    }

    @Test
    void refreshRejectsUnverifiedUserWithoutRevokingTheToken() {
        UUID userId = UUID.randomUUID();
        RefreshToken active = RefreshToken.builder()
                .userId(userId)
                .tokenHash(TokenHasher.sha256("raw-token"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).emailVerified(false).build();

        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("raw-token")))
                .thenReturn(Optional.of(active));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(UnverifiedEmailException.class);
        assertThat(active.getRevokedAt()).isNull();
    }

    @Test
    void forgotPasswordSilentlyNoOpsForUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequestDto("nobody@example.com"));

        verify(passwordResetTokenRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void forgotPasswordEmailsASixDigitCodeAndStoresOnlyItsSaltedHash() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequestDto("user@example.com"));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq("user@example.com"), code.capture(), eq(15));
        assertThat(code.getValue()).matches("\\d{6}");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).deleteByUserId(userId);
        verify(passwordResetTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenHasher.sha256(userId + ":" + code.getValue()));
        assertThat(saved.getValue().getExpiresAt()).isBefore(Instant.now().plusSeconds(15 * 60 + 5));
    }

    @Test
    void forgotPasswordIsThrottledWithinSixtySecondsOfTheLastCode() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();
        PasswordResetToken recent = activeCode(userId, "123456");
        recent.setCreatedAt(Instant.now().minusSeconds(10));
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(recent));

        authService.forgotPassword(new ForgotPasswordRequestDto("user@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetCode(any(), any(), anyInt());
    }

    @Test
    void resetPasswordRejectsExpiredCode() {
        UUID userId = UUID.randomUUID();
        User user = userWithPassword(userId, "current-password1");
        PasswordResetToken expired = activeCode(userId, "123456");
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequestDto("user@example.com", "123456", "brand-new-password1")))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_CODE));
    }

    @Test
    void resetPasswordForUnknownEmailLooksExactlyLikeAWrongCode() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequestDto("nobody@example.com", "123456", "brand-new-password1")))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_OR_EXPIRED_CODE));
    }

    @Test
    void wrongCodesCountUpAndTheFifthBurnsTheCode() {
        UUID userId = UUID.randomUUID();
        User user = userWithPassword(userId, "current-password1");
        PasswordResetToken code = activeCode(userId, "123456");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(code));

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequestDto("user@example.com", "000000", "brand-new-password1")))
                    .isInstanceOf(BadRequestException.class);
            assertThat(code.getFailedAttempts()).isEqualTo(i);
        }
        assertThat(code.getUsedAt()).isNotNull();

        // Even the right code is useless now.
        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequestDto("user@example.com", "123456", "brand-new-password1")))
                .isInstanceOf(BadRequestException.class);
        assertThat(passwordEncoder.matches("current-password1", user.getPasswordHash())).isTrue();
    }

    @Test
    void resetPasswordRejectsSameAsCurrentPassword() {
        UUID userId = UUID.randomUUID();
        User user = userWithPassword(userId, "current-password1");
        PasswordResetToken code = activeCode(userId, "123456");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequestDto("user@example.com", "123456", "current-password1")))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SAME_PASSWORD));
        assertThat(code.getUsedAt()).isNull();
    }

    @Test
    void resetPasswordSucceedsAndEndsEveryExistingSession() {
        UUID userId = UUID.randomUUID();
        User user = userWithPassword(userId, "current-password1");
        PasswordResetToken code = activeCode(userId, "123456");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(code));

        authService.resetPassword(new ResetPasswordRequestDto("user@example.com", "123456", "brand-new-password1"));

        assertThat(passwordEncoder.matches("brand-new-password1", user.getPasswordHash())).isTrue();
        assertThat(code.getUsedAt()).isNotNull();
        assertThat(user.currentTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).revokeAllForUser(eq(userId), any());
        verify(passwordPolicy).validate("brand-new-password1", "user@example.com", null);
        verify(emailService).sendPasswordChangedEmail("user@example.com");
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        UUID userId = UUID.randomUUID();
        User user = userWithPassword(userId, "current-password1");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(userId, "not-it-1", "brand-new-password1"))
                .isInstanceOfSatisfying(UnauthorizedActionException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.WRONG_PASSWORD));
        assertThat(passwordEncoder.matches("current-password1", user.getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordRejectsSamePassword() {
        UUID userId = UUID.randomUUID();
        User user = userWithPassword(userId, "current-password1");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(userId, "current-password1", "current-password1"))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SAME_PASSWORD));
    }

    @Test
    void changePasswordEndsOtherSessionsAndReturnsAFreshPair() {
        UUID userId = UUID.randomUUID();
        User user = userWithPassword(userId, "current-password1");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), anyInt())).thenReturn("fresh-access");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(2_592_000_000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponseDto response = authService.changePassword(userId, "current-password1", "brand-new-password1");

        assertThat(response.accessToken()).isEqualTo("fresh-access");
        verify(passwordPolicy).validate("brand-new-password1", "user@example.com", null);
        verify(emailService).sendPasswordChangedEmail("user@example.com");
        assertThat(passwordEncoder.matches("brand-new-password1", user.getPasswordHash())).isTrue();
        assertThat(user.currentTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).revokeAllForUser(eq(userId), any());
    }

    private User userWithPassword(UUID userId, String password) {
        return User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.USER)
                .emailVerified(true)
                .build();
    }

    private static PasswordResetToken activeCode(UUID userId, String code) {
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(TokenHasher.sha256(userId + ":" + code))
                .expiresAt(Instant.now().plusSeconds(900))
                .failedAttempts(0)
                .build();
        token.setCreatedAt(Instant.now().minusSeconds(120));
        return token;
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        EmailVerificationToken expired = EmailVerificationToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash(TokenHasher.sha256("verify-token"))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        when(emailVerificationTokenRepository.findByTokenHash(TokenHasher.sha256("verify-token")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.verifyEmail("verify-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmailMarksUserVerifiedAndConsumesToken() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).emailVerified(false).build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(userId)
                .tokenHash(TokenHasher.sha256("verify-token"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(emailVerificationTokenRepository.findByTokenHash(TokenHasher.sha256("verify-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.verifyEmail("verify-token");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(emailVerificationTokenRepository).save(token);
    }

    @Test
    void resendVerificationEmailNoOpsWhenAlreadyVerified() {
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com").role(Role.USER).emailVerified(true).build();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        authService.resendVerificationEmail(new ResendVerificationEmailRequestDto("user@example.com"));

        verify(emailService, org.mockito.Mockito.never()).sendVerificationEmail(any(), any());
    }

    @Test
    void resendVerificationEmailSilentlyNoOpsForUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        authService.resendVerificationEmail(new ResendVerificationEmailRequestDto("nobody@example.com"));

        verify(emailVerificationTokenRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void resendVerificationEmailIssuesFreshTokenWhenUnverified() {
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com").role(Role.USER).emailVerified(false).build();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        authService.resendVerificationEmail(new ResendVerificationEmailRequestDto("user@example.com"));

        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(org.mockito.ArgumentMatchers.eq("user@example.com"), any());
    }
}
