package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.auth.AuthResponseDto;
import com.wingmark.backend.dto.auth.ForgotPasswordRequestDto;
import com.wingmark.backend.dto.auth.LoginRequestDto;
import com.wingmark.backend.dto.auth.RegisterRequestDto;
import com.wingmark.backend.dto.auth.ResetPasswordRequestDto;
import com.wingmark.backend.entity.PasswordResetToken;
import com.wingmark.backend.entity.RefreshToken;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.exception.DuplicateResourceException;
import com.wingmark.backend.exception.InvalidCredentialsException;
import com.wingmark.backend.exception.InvalidTokenException;
import com.wingmark.backend.repository.PasswordResetTokenRepository;
import com.wingmark.backend.repository.RefreshTokenRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.security.JwtTokenProvider;
import com.wingmark.backend.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private JwtTokenProvider jwtTokenProvider;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository, userSettingsRepository, refreshTokenRepository,
                passwordResetTokenRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        RegisterRequestDto request = new RegisterRequestDto("taken@example.com", "password1", "newuser", "A", "B");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("takenname")).thenReturn(true);

        RegisterRequestDto request = new RegisterRequestDto("fresh@example.com", "password1", "takenname", "A", "B");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void registerSavesUserWithHashedPasswordAndIssuesTokens() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(2_592_000_000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);

        RegisterRequestDto request = new RegisterRequestDto("fresh@example.com", "password1", "freshuser", "A", "B");
        AuthResponseDto response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();

        verify(userRepository).save(any(User.class));
        verify(userSettingsRepository).save(any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
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
                .build();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(2_592_000_000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponseDto response = authService.login(new LoginRequestDto("user@example.com", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
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
        User user = User.builder().id(userId).email("user@example.com").role(Role.USER).build();

        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("raw-token")))
                .thenReturn(Optional.of(active));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("new-access-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(2_592_000_000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponseDto response = authService.refresh("raw-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(active.getRevokedAt()).isNotNull();
    }

    @Test
    void forgotPasswordSilentlyNoOpsForUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequestDto("nobody@example.com"));

        verify(passwordResetTokenRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void forgotPasswordIssuesTokenForKnownEmail() {
        User user = User.builder().id(UUID.randomUUID()).email("user@example.com").role(Role.USER).build();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequestDto("user@example.com"));

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken expired = PasswordResetToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash(TokenHasher.sha256("reset-token"))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256("reset-token")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequestDto("reset-token", "newpassword1")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void resetPasswordRejectsSameAsCurrentPassword() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("current-password1"))
                .role(Role.USER)
                .build();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(TokenHasher.sha256("reset-token"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256("reset-token")))
                .thenReturn(Optional.of(resetToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequestDto("reset-token", "current-password1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPasswordSucceedsAndRevokesExistingSessions() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash(passwordEncoder.encode("current-password1"))
                .role(Role.USER)
                .build();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(userId)
                .tokenHash(TokenHasher.sha256("reset-token"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256("reset-token")))
                .thenReturn(Optional.of(resetToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.resetPassword(new ResetPasswordRequestDto("reset-token", "brand-new-password1"));

        assertThat(passwordEncoder.matches("brand-new-password1", user.getPasswordHash())).isTrue();
        assertThat(resetToken.getUsedAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllForUser(org.mockito.ArgumentMatchers.eq(userId), any());
    }
}
