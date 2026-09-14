package com.wingmark.backend.service;

import com.wingmark.backend.dto.auth.AuthResponseDto;
import com.wingmark.backend.dto.auth.ForgotPasswordRequestDto;
import com.wingmark.backend.dto.auth.LoginRequestDto;
import com.wingmark.backend.dto.auth.RegisterRequestDto;
import com.wingmark.backend.dto.auth.ResetPasswordRequestDto;

import java.util.UUID;

/** Registration, login, token lifecycle, and password reset. */
public interface AuthService {

    /** Creates a new account and issues an initial token pair. */
    AuthResponseDto register(RegisterRequestDto request);

    /** Verifies credentials and issues a fresh token pair. */
    AuthResponseDto login(LoginRequestDto request);

    /** Verifies a refresh token, revokes it, and issues a new token pair (rotation). */
    AuthResponseDto refresh(String rawRefreshToken);

    /** Revokes a single refresh token. */
    void logout(String rawRefreshToken);

    /** Revokes every refresh token belonging to a user. */
    void logoutAll(UUID userId);

    /** Issues a password-reset token for the given email, if an account exists (silently no-ops otherwise). */
    void forgotPassword(ForgotPasswordRequestDto request);

    /** Consumes a password-reset token to set a new password; rejects a password identical to the current one. */
    void resetPassword(ResetPasswordRequestDto request);
}
