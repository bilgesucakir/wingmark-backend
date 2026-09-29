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

    /**
     * Emails a 6-digit reset code (15 min, single use) if an account exists; silently no-ops
     * otherwise, and when a code was already sent in the last 60 seconds.
     */
    void forgotPassword(ForgotPasswordRequestDto request);

    /**
     * Consumes a reset code to set a new password and ends every existing session. A code is
     * burned after 5 wrong guesses; a password identical to the current one is rejected.
     */
    void resetPassword(ResetPasswordRequestDto request);

    /**
     * Changes the password after re-checking the current one, ends every existing session,
     * and returns a fresh token pair so the calling device stays signed in.
     */
    AuthResponseDto changePassword(UUID userId, String currentPassword, String newPassword);

    /** Consumes an email-verification token, marking the owning account verified. */
    void verifyEmail(String rawToken);

    /**
     * Issues and emails a fresh verification link for the given email, if an account exists
     * and isn't already verified (silently no-ops otherwise, like forgotPassword - this stays
     * unauthenticated on purpose, since an unverified account can't log in to prove ownership
     * any other way, e.g. after losing its session before the original link was used).
     */
    void resendVerificationEmail(ResendVerificationEmailRequestDto request);
}
