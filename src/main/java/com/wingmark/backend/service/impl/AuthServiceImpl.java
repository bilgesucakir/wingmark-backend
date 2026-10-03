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
import com.wingmark.backend.entity.UserSettings;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.exception.UnauthorizedActionException;
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
import com.wingmark.backend.security.PasswordPolicy;
import com.wingmark.backend.service.AuthService;
import com.wingmark.backend.service.ConsentService;
import com.wingmark.backend.service.EmailService;
import com.wingmark.backend.util.RandomTokenGenerator;
import com.wingmark.backend.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Duration RESET_CODE_TTL = Duration.ofMinutes(15);
    private static final Duration RESET_CODE_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int RESET_CODE_MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final ConsentService consentService;
    private final PasswordPolicy passwordPolicy;

    @Override
    public RegisterResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException(ErrorCode.USERNAME_TAKEN, "This username is already taken");
        }
        consentService.requireAcceptedAtSignup(request.acceptedTermsVersion(), request.acceptedPrivacyVersion());
        passwordPolicy.validate(request.password(), request.email(), request.username());

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
        consentService.recordSignupConsents(user.getId());

        issueVerificationEmail(user);

        // No tokens until the email is verified - login and refresh both enforce that too.
        return new RegisterResponseDto(user.getId(), user.getEmail(), user.getUsername(), false,
                "Account created. Check your inbox for a verification link, then log in.");
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

        if (!user.isEmailVerified()) {
            throw new UnverifiedEmailException("Please verify your email before continuing");
        }

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
        userRepository.findById(userId).ifPresent(user -> {
            user.invalidateIssuedTokens();
            userRepository.save(user);
        });
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDto request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            boolean recentlyIssued = passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                    .map(PasswordResetToken::getCreatedAt)
                    .filter(createdAt -> createdAt.isAfter(Instant.now().minus(RESET_CODE_RESEND_COOLDOWN)))
                    .isPresent();
            if (recentlyIssued) {
                // Throttled silently - same 202 either way, so this doesn't leak anything,
                // and it stops the endpoint being used to spam someone's inbox.
                return;
            }

            // Only the newest code is ever valid.
            passwordResetTokenRepository.deleteByUserId(user.getId());

            String code = RandomTokenGenerator.numericCode(6);
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(resetCodeHash(user.getId(), code))
                    .expiresAt(Instant.now().plus(RESET_CODE_TTL))
                    .failedAttempts(0)
                    .build());

            emailService.sendPasswordResetCode(user.getEmail(), code, (int) RESET_CODE_TTL.toMinutes());
        });
        // Always return silently regardless of whether the email exists, to avoid
        // leaking which addresses have accounts.
    }

    @Override
    public void resetPassword(ResetPasswordRequestDto request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(AuthServiceImpl::invalidResetCode);
        PasswordResetToken resetToken = passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .filter(PasswordResetToken::isActive)
                .orElseThrow(AuthServiceImpl::invalidResetCode);

        if (!resetToken.getTokenHash().equals(resetCodeHash(user.getId(), request.code()))) {
            int attempts = (resetToken.getFailedAttempts() == null ? 0 : resetToken.getFailedAttempts()) + 1;
            resetToken.setFailedAttempts(attempts);
            if (attempts >= RESET_CODE_MAX_ATTEMPTS) {
                // A 6-digit code is only safe with a hard guess limit; burn it.
                resetToken.setUsedAt(Instant.now());
            }
            passwordResetTokenRepository.save(resetToken);
            throw invalidResetCode();
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException(ErrorCode.SAME_PASSWORD, "New password must be different from the current password");
        }
        passwordPolicy.validate(request.newPassword(), user.getEmail(), user.getUsername());

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        setPasswordAndEndSessions(user, request.newPassword());
    }

    @Override
    public AuthResponseDto changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedActionException(ErrorCode.WRONG_PASSWORD, "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException(ErrorCode.SAME_PASSWORD, "New password must be different from the current password");
        }
        passwordPolicy.validate(newPassword, user.getEmail(), user.getUsername());

        setPasswordAndEndSessions(user, newPassword);
        // Every other device is signed out; hand this one a fresh pair so it stays in.
        return issueTokens(user);
    }

    /** Sets the new password and ends every existing session - refresh tokens and already-issued access tokens alike. */
    private void setPasswordAndEndSessions(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.invalidateIssuedTokens();
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        emailService.sendPasswordChangedEmail(user.getEmail());
    }

    /** Salted with the user id: codes are only 6 digits, so two users can draw the same one and tokenHash is unique. */
    private static String resetCodeHash(UUID userId, String code) {
        return TokenHasher.sha256(userId + ":" + code);
    }

    private static BadRequestException invalidResetCode() {
        return new BadRequestException(ErrorCode.INVALID_OR_EXPIRED_CODE, "Invalid or expired reset code");
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
    public void resendVerificationEmail(ResendVerificationEmailRequestDto request) {
        userRepository.findByEmailIgnoreCase(request.email())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueVerificationEmail);
        // Always return silently regardless of whether the email exists or is already
        // verified, to avoid leaking which addresses have accounts (same as forgotPassword).
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
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name(),
                user.currentTokenVersion());

        String rawRefreshToken = RandomTokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.sha256(rawRefreshToken))
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponseDto(accessToken, rawRefreshToken, jwtTokenProvider.getAccessTokenExpirationMs(),
                consentService.pendingConsents(user.getId()));
    }
}
