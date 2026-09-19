package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.AppProperties;
import com.wingmark.backend.dto.auth.AuthResponseDto;
import com.wingmark.backend.dto.auth.ForgotPasswordRequestDto;
import com.wingmark.backend.dto.auth.LoginRequestDto;
import com.wingmark.backend.dto.auth.RegisterRequestDto;
import com.wingmark.backend.dto.auth.ResetPasswordRequestDto;
import com.wingmark.backend.entity.EmailVerificationToken;
import com.wingmark.backend.entity.PasswordResetToken;
import com.wingmark.backend.entity.RefreshToken;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.entity.UserSettings;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.exception.DuplicateResourceException;
import com.wingmark.backend.exception.InvalidCredentialsException;
import com.wingmark.backend.exception.InvalidTokenException;
import com.wingmark.backend.exception.UnverifiedEmailException;
import com.wingmark.backend.repository.EmailVerificationTokenRepository;
import com.wingmark.backend.repository.PasswordResetTokenRepository;
import com.wingmark.backend.repository.RefreshTokenRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.security.JwtTokenProvider;
import com.wingmark.backend.service.AuthService;
import com.wingmark.backend.service.EmailService;
import com.wingmark.backend.util.RandomTokenGenerator;
import com.wingmark.backend.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final AppProperties appProperties;

    @Override
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("This username is already taken");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .username(request.username())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.USER)
                .emailVerified(false)
                .build();
        user = userRepository.save(user);

        userSettingsRepository.save(UserSettings.builder()
                .userId(user.getId())
                .build());

        issueVerificationEmail(user);

        return issueTokens(user);
    }

    @Override
    public AuthResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            throw new UnverifiedEmailException("Please verify your email before logging in - check your inbox, " +
                    "or request a new link via POST /api/auth/resend-verification-email");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    @Override
    public AuthResponseDto refresh(String rawRefreshToken) {
        String hash = TokenHasher.sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (!stored.isActive()) {
            throw new InvalidTokenException("Refresh token is expired or has been revoked");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        return issueTokens(user);
    }

    @Override
    public void logout(String rawRefreshToken) {
        String hash = TokenHasher.sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Override
    public void logoutAll(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDto request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            String rawToken = RandomTokenGenerator.generate();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(TokenHasher.sha256(rawToken))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            // TODO: wire up an email provider; for now the raw token is logged so the
            // reset flow can be exercised end-to-end in development.
            log.info("Password reset requested for user {}. Reset token: {}", user.getId(), rawToken);
        });
        // Always return silently regardless of whether the email exists, to avoid
        // leaking which addresses have accounts.
    }

    @Override
    public void resetPassword(ResetPasswordRequestDto request) {
        String hash = TokenHasher.sha256(request.token());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired password reset token"));

        if (!resetToken.isActive()) {
            throw new InvalidTokenException("Invalid or expired password reset token");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired password reset token"));

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    @Override
    public void verifyEmail(String rawToken) {
        String hash = TokenHasher.sha256(rawToken);
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired verification token"));

        if (!verificationToken.isActive()) {
            throw new InvalidTokenException("Invalid or expired verification token");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired verification token"));

        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(verificationToken);
    }

    @Override
    public void resendVerificationEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired verification token"));

        if (user.isEmailVerified()) {
            return;
        }

        issueVerificationEmail(user);
    }

    private void issueVerificationEmail(User user) {
        String rawToken = RandomTokenGenerator.generate();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.sha256(rawToken))
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();
        emailVerificationTokenRepository.save(verificationToken);

        String verifyUrl = appProperties.baseUrl() + "/api/auth/verify-email?token=" + rawToken;
        emailService.sendVerificationEmail(user.getEmail(), verifyUrl);
    }

    private AuthResponseDto issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        String rawRefreshToken = RandomTokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.sha256(rawRefreshToken))
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponseDto(accessToken, rawRefreshToken, jwtTokenProvider.getAccessTokenExpirationMs());
    }
}
