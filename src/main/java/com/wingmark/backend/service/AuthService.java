package com.wingmark.backend.service;

import com.wingmark.backend.dto.auth.AuthResponse;
import com.wingmark.backend.dto.auth.ForgotPasswordRequest;
import com.wingmark.backend.dto.auth.LoginRequest;
import com.wingmark.backend.dto.auth.RegisterRequest;
import com.wingmark.backend.dto.auth.ResetPasswordRequest;

import java.util.UUID;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    void logoutAll(UUID userId);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
