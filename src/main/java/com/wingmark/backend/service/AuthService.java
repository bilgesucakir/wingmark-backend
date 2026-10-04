package com.wingmark.backend.service;

import com.wingmark.backend.dto.auth.AuthResponseDto;
import com.wingmark.backend.dto.auth.ForgotPasswordRequestDto;
import com.wingmark.backend.dto.auth.LoginRequestDto;
import com.wingmark.backend.dto.auth.RegisterRequestDto;
import com.wingmark.backend.dto.auth.RegisterResponseDto;
import com.wingmark.backend.dto.auth.ResendVerificationEmailRequestDto;
import com.wingmark.backend.dto.auth.ResetPasswordRequestDto;

import java.util.UUID;

/** Registration, login, token lifecycle, and password reset. */
public interface AuthService {

    /** Creates a new, unverified account and emails a verification link; issues no tokens. */
    RegisterResponseDto register(RegisterRequestDto request);

    /** Verifies credentials and issues a fresh token pair. */
    AuthResponseDto login(LoginRequestDto request);

    /** Verifies a refresh token, revokes it, and issues a new token pair (rotation). */
    AuthResponseDto refresh(String rawRefreshToken);

    /** Revokes a single refresh token. */
    void logout(String rawRefreshToken);

    /** Revokes every refresh token belonging to a user and invalidates their already-issued access tokens. */
    void logoutAll(UUID userId);

    /** Emails a 6-digit reset code (15 minutes, single use) if the account exists; does nothing otherwise or if one was sent in the last 60 seconds. */
    void forgotPassword(ForgotPasswordRequestDto request);

    /** Sets a new password with a reset code and ends every session. The code is burned after 5 wrong guesses; the current password is rejected. */
    void resetPassword(ResetPasswordRequestDto request);

    /** Changes the password after checking the current one, ends every session and returns a new token pair for this device. */
    AuthResponseDto changePassword(UUID userId, String currentPassword, String newPassword);

    /** Consumes an email-verification token, marking the owning account verified. */
    void verifyEmail(String rawToken);

    /** Emails a new verification link if the account exists and is unverified; does nothing otherwise. */
    void resendVerificationEmail(ResendVerificationEmailRequestDto request);
}
