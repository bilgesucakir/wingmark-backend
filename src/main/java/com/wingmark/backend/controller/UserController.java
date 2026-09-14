package com.wingmark.backend.controller;

import com.wingmark.backend.dto.user.SettingsResponse;
import com.wingmark.backend.dto.user.UpdateProfileRequest;
import com.wingmark.backend.dto.user.UpdateSettingsRequest;
import com.wingmark.backend.dto.user.UserProfileResponse;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getProfile(principal.getId()));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                               @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(principal.getId(), request));
    }

    @GetMapping("/settings")
    public ResponseEntity<SettingsResponse> getSettings(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getSettings(principal.getId()));
    }

    @PutMapping("/settings")
    public ResponseEntity<SettingsResponse> updateSettings(@AuthenticationPrincipal UserPrincipal principal,
                                                             @Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(userService.updateSettings(principal.getId(), request));
    }
}
