package com.wingmark.backend.controller;

import com.wingmark.backend.dto.auth.AuthResponseDto;
import com.wingmark.backend.dto.auth.ForgotPasswordRequestDto;
import com.wingmark.backend.dto.auth.LoginRequestDto;
import com.wingmark.backend.dto.auth.RefreshTokenRequestDto;
import com.wingmark.backend.dto.auth.RegisterRequestDto;
import com.wingmark.backend.dto.auth.ResetPasswordRequestDto;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registration, login, token refresh/logout, and password reset. All public - no token required. */
@Tag(name = "Auth", description = "Registration, login, token refresh/logout, and password reset")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Creates a new account and immediately returns a token pair, same as logging in. */
    @Operation(summary = "Register", description = "Creates a new account and returns an access/refresh token pair.")
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Authenticates with email + password and returns a fresh token pair. */
    @Operation(summary = "Login", description = "Authenticates with email + password and returns a fresh access/refresh token pair.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Exchanges a still-valid refresh token for a new token pair, revoking the old refresh token. */
    @Operation(summary = "Refresh access token", description = "Exchanges a still-valid refresh token for a new token pair; the old refresh token is revoked.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /** Revokes one refresh token (e.g. signing out of the current device only). */
    @Operation(summary = "Logout", description = "Revokes the given refresh token, signing out that device/session only.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDto request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Revokes every refresh token belonging to the caller (signs out of all devices). */
    @Operation(summary = "Logout everywhere", description = "Revokes every refresh token belonging to the caller, signing out of all devices/sessions.")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logoutAll(principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** Issues a password-reset token for the given email, if an account with that email exists. Always responds the same way either way to avoid leaking which emails are registered. */
    @Operation(summary = "Request password reset", description = "Issues a password-reset token for the given email if an account exists. " +
            "Always responds 202 regardless, to avoid revealing which emails are registered.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    /** Consumes a password-reset token to set a new password. Rejects the new password if it's identical to the current one. */
    @Operation(summary = "Reset password", description = "Consumes a password-reset token to set a new password. Rejects a new password identical to the current one.")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
