package com.wingmark.backend.controller;

import com.wingmark.backend.dto.user.SettingsResponseDto;
import com.wingmark.backend.dto.user.UpdateProfileRequestDto;
import com.wingmark.backend.dto.user.UpdateSettingsRequestDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** Returns the caller's own profile. */
    @Operation(summary = "Get profile", description = "Returns the caller's own profile. userId must match the authenticated caller.")
    @GetMapping
    public ResponseEntity<UserProfileResponseDto> getProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                              @PathVariable UUID userId) {
        requireSelf(principal, userId);
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /** Updates the caller's own profile (name, profile picture, favorite species). */
    @Operation(summary = "Update profile", description = "Updates the caller's own profile (name, profile picture, favorite species).")
    @PutMapping
    public ResponseEntity<UserProfileResponseDto> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @PathVariable UUID userId,
                                                                  @Valid @RequestBody UpdateProfileRequestDto request) {
        requireSelf(principal, userId);
        return ResponseEntity.ok(userService.updateProfile(userId, request));
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
