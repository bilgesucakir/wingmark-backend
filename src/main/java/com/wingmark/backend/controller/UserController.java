package com.wingmark.backend.controller;

import com.wingmark.backend.dto.auth.AuthResponseDto;
import com.wingmark.backend.dto.export.UserDataExportDto;
import com.wingmark.backend.dto.legal.AcceptConsentRequestDto;
import com.wingmark.backend.dto.legal.ConsentResponseDto;
import com.wingmark.backend.dto.legal.PendingConsentsResponseDto;
import com.wingmark.backend.dto.user.ChangePasswordRequestDto;
import com.wingmark.backend.dto.user.DeleteAccountRequestDto;
import com.wingmark.backend.dto.user.SettingsResponseDto;
import com.wingmark.backend.dto.user.UpdateProfileRequestDto;
import com.wingmark.backend.dto.user.UpdateSettingsRequestDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.security.ratelimit.AuthRateLimits;
import com.wingmark.backend.service.AuthService;
import com.wingmark.backend.service.ConsentService;
import com.wingmark.backend.service.DataExportService;
import com.wingmark.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The caller's own profile and settings. This app has no concept of viewing another
 * user's profile, so userId in the path must match the authenticated caller's own id
 * (obtainable client-side by decoding the JWT's "sub" claim) - a mismatch is treated
 * as not found rather than forbidden, consistent with BirdLogController's scoping.
 */
@Tag(name = "Users", description = "The caller's own profile and settings")
@RestController
@RequestMapping("/api/users/{userId}")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final AuthRateLimits authRateLimits;
    private final ConsentService consentService;
    private final DataExportService dataExportService;

    /** Returns the caller's own profile. */
    @Operation(summary = "Get profile", description = "Returns the caller's own profile. userId must match the authenticated caller.")
    @GetMapping
    public ResponseEntity<UserProfileResponseDto> getProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                              @PathVariable UUID userId,
                                                              Locale locale) {
        requireSelf(principal, userId);
        return ResponseEntity.ok(userService.getProfile(userId, locale));
    }

    /** Updates the caller's own profile (name, profile picture, favorite species). */
    @Operation(summary = "Update profile", description = "Updates the caller's own profile (name, profile picture, favorite species). " +
            "profilePicture must be null, a preset avatar key from GET /api/avatars, or a /uploads/... URL returned by POST /api/uploads/photo (400 INVALID_PROFILE_PICTURE otherwise). " +
            "Unknown favoriteSpeciesId is 422 INVALID_REFERENCE.")
    @PutMapping
    public ResponseEntity<UserProfileResponseDto> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @PathVariable UUID userId,
                                                                  @Valid @RequestBody UpdateProfileRequestDto request,
                                                                  Locale locale) {
        requireSelf(principal, userId);
        return ResponseEntity.ok(userService.updateProfile(userId, request, locale));
    }

    /** Returns the caller's own app settings (unit preference, locale). */
    @Operation(summary = "Get settings", description = "Returns the caller's own app settings (unit preference, locale).")
    @GetMapping("/settings")
    public ResponseEntity<SettingsResponseDto> getSettings(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable UUID userId) {
        requireSelf(principal, userId);
        return ResponseEntity.ok(userService.getSettings(userId));
    }

    /** Updates the caller's own app settings. */
    @Operation(summary = "Update settings", description = "Updates the caller's own app settings.")
    @PutMapping("/settings")
    public ResponseEntity<SettingsResponseDto> updateSettings(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable UUID userId,
                                                                @Valid @RequestBody UpdateSettingsRequestDto request) {
        requireSelf(principal, userId);
        return ResponseEntity.ok(userService.updateSettings(userId, request));
    }

    /**
     * Changes the caller's password after re-checking the current one. Every other session
     * (refresh tokens and already-issued access tokens) ends immediately; this device gets a
     * fresh token pair in the response so it stays signed in.
     */
    @Operation(summary = "Change password", description = "Changes the caller's password. Body: {currentPassword, newPassword}. " +
            "Returns a fresh token pair for this device; every other session, including already-issued access tokens, is signed out immediately. " +
            "403 WRONG_PASSWORD if currentPassword is wrong, 400 SAME_PASSWORD if newPassword equals the current one, 400 VALIDATION_FAILED for a weak password.")
    @PostMapping("/password")
    public ResponseEntity<AuthResponseDto> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable UUID userId,
                                                          @Valid @RequestBody ChangePasswordRequestDto request) {
        requireSelf(principal, userId);
        authRateLimits.passwordCheck(userId);
        return ResponseEntity.ok(authService.changePassword(userId, request.currentPassword(), request.newPassword()));
    }

    /** Every legal-document acceptance the caller has recorded, oldest first. */
    @Operation(summary = "Get my consents", description = "Every Terms of Service / Privacy Policy acceptance the caller has recorded (type, version, acceptedAt), oldest first.")
    @GetMapping("/consents")
    public ResponseEntity<List<ConsentResponseDto>> getConsents(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable UUID userId) {
        requireSelf(principal, userId);
        return ResponseEntity.ok(consentService.history(userId));
    }

    /** Records acceptance of the current version of a legal document (e.g. after it changed). */
    @Operation(summary = "Accept a legal document", description = "Body: {type: TERMS|PRIVACY, version}. version must equal the current one from GET /api/legal " +
            "(400 CONSENT_VERSION_MISMATCH otherwise). Returns the documents still pending - show the acceptance screen until it's empty. " +
            "Login/refresh responses list pending documents in pendingConsents.")
    @PostMapping("/consents")
    public ResponseEntity<PendingConsentsResponseDto> acceptConsent(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID userId,
                                                                    @Valid @RequestBody AcceptConsentRequestDto request) {
        requireSelf(principal, userId);
        return ResponseEntity.ok(new PendingConsentsResponseDto(consentService.accept(userId, request.type(), request.version())));
    }

    /** Downloads a complete copy of the caller's data (right of access / data portability). */
    @Operation(summary = "Export my data", description = "Returns everything Wingmark holds about the caller as one JSON document: profile, settings, " +
            "every bird log, badge progress and consent history. Sent as a file download (wingmark-data-export.json).")
    @GetMapping("/export")
    public ResponseEntity<UserDataExportDto> export(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID userId,
                                                    Locale locale) {
        requireSelf(principal, userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wingmark-data-export.json\"")
                .body(dataExportService.export(userId, locale));
    }

    /**
     * Permanently deletes the caller's own account and everything tied to it (logs, badges,
     * settings, sessions, uploaded photos). Requires the current password in the body, so a
     * stolen access token alone can't wipe an account. Every existing token stops working
     * immediately.
     */
    @Operation(summary = "Delete own account", description = "Permanently deletes the caller's own account and all of its data: bird logs, badge progress, " +
            "settings, refresh tokens and uploaded photos. Body: {\"password\": \"...\"} - the current password, re-checked (403 if wrong). " +
            "409 if the caller is the only admin. All of the caller's tokens stop working immediately. Cannot be undone.")
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID userId,
                                              @Valid @RequestBody DeleteAccountRequestDto request) {
        requireSelf(principal, userId);
        authRateLimits.passwordCheck(userId);
        userService.deleteOwnAccount(userId, request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * This app has no concept of viewing another user's profile, so a path userId that
     * isn't the caller's own is treated as not found rather than forbidden - consistent
     * with how BirdLogController scopes ownership - to avoid confirming other ids exist.
     */
    private void requireSelf(UserPrincipal principal, UUID userId) {
        if (!principal.getId().equals(userId)) {
            throw ResourceNotFoundException.of("User", userId);
        }
    }
}
