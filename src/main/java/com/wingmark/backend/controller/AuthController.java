package com.wingmark.backend.controller;

import com.wingmark.backend.dto.auth.AuthResponseDto;
import com.wingmark.backend.dto.auth.ForgotPasswordRequestDto;
import com.wingmark.backend.dto.auth.LoginRequestDto;
import com.wingmark.backend.dto.auth.RefreshTokenRequestDto;
import com.wingmark.backend.dto.auth.RegisterRequestDto;
import com.wingmark.backend.dto.auth.RegisterResponseDto;
import com.wingmark.backend.dto.auth.ResendVerificationEmailRequestDto;
import com.wingmark.backend.dto.auth.ResetPasswordRequestDto;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.security.ratelimit.AuthRateLimits;
import com.wingmark.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Registration, login, token refresh/logout and password reset. All public. */
@Tag(name = "Auth", description = "Registration, login, token refresh/logout, and password reset")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimits authRateLimits;

    /** Creates an unverified account and emails a verification link; no tokens are issued yet. */
    @Operation(summary = "Register", description = "Creates a new, unverified account and emails a verification link. Returns the new account's id/email/username " +
            "but no tokens - the client must verify the email, then call POST /api/auth/login.")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> register(@Valid @RequestBody RegisterRequestDto request, HttpServletRequest http) {
        authRateLimits.register(http);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Authenticates with email + password and returns a fresh token pair. */
    @Operation(summary = "Login", description = "Authenticates with email + password and returns a fresh access/refresh token pair.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request, HttpServletRequest http) {
        authRateLimits.login(http, request.email());
        return ResponseEntity.ok(authService.login(request));
    }

    /** Exchanges a still-valid refresh token for a new token pair, revoking the old refresh token. */
    @Operation(summary = "Refresh access token", description = "Exchanges a still-valid refresh token for a new token pair; the old refresh token is revoked.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request, HttpServletRequest http) {
        authRateLimits.refresh(http);
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /** Revokes one refresh token (e.g. signing out of the current device only). */
    @Operation(summary = "Logout", description = "Revokes the given refresh token, signing out that device/session only.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDto request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Signs the caller out of every device: revokes all refresh tokens and invalidates already-issued access tokens. */
    @Operation(summary = "Logout everywhere", description = "Revokes every refresh token belonging to the caller and invalidates every access token issued so far " +
            "(including the one used for this call), signing out of all devices/sessions immediately.")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logoutAll(principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** Emails a 6-digit reset code if the account exists; the response is the same either way. */
    @Operation(summary = "Request password reset", description = "Emails a 6-digit reset code (valid 15 minutes, single use) if an account exists. " +
            "Only the newest code is valid; a request within 60 seconds of the last code sends nothing. " +
            "Always responds 202 regardless, to avoid revealing which emails are registered.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request, HttpServletRequest http) {
        authRateLimits.emailSending(http, "forgot-password", request.email());
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    /** Consumes an emailed reset code to set a new password, ending every existing session. */
    @Operation(summary = "Reset password", description = "Body: {email, code, newPassword}. Consumes the emailed 6-digit code to set a new password and signs out every session. " +
            "400 INVALID_OR_EXPIRED_CODE for a wrong/expired/used code (the code is burned after 5 wrong guesses), 400 SAME_PASSWORD, 400 VALIDATION_FAILED.")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request, HttpServletRequest http) {
        authRateLimits.resetConfirm(http, request.email());
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /** Verifies an email from the link in the verification email; responds with HTML. */
    @Operation(summary = "Verify email", description = "Consumes an email-verification token (from the link sent at registration) and marks the account verified.")
    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok("<p>Your email is verified. You can close this page and log in.</p>");
    }

    /** Emails a new verification link if the account exists and is unverified; the response is the same either way. */
    @Operation(summary = "Resend verification email", description = "Issues and emails a fresh verification link for the given email, if an account exists and isn't already verified. " +
            "Always responds 202 regardless, to avoid revealing which emails are registered.")
    @PostMapping("/resend-verification-email")
    public ResponseEntity<Void> resendVerificationEmail(@Valid @RequestBody ResendVerificationEmailRequestDto request, HttpServletRequest http) {
        authRateLimits.emailSending(http, "resend-verification", request.email());
        authService.resendVerificationEmail(request);
        return ResponseEntity.accepted().build();
    }
}
