package com.wingmark.backend.controller;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.CreateBadgeRequestDto;
import com.wingmark.backend.dto.badge.UserBadgeResponseDto;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.BadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Badge catalog and per-user badge progress/awards. */
@Tag(name = "Badges", description = "Badge catalog and per-user badge progress")
@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    /** Returns every badge definition in the catalog. Public - lets the app show badge artwork/descriptions before login. */
    @Operation(summary = "Get all badges", description = "Returns every badge definition in the catalog (name, icon, criteria). Public endpoint.")
    @GetMapping("/catalog")
    public ResponseEntity<List<BadgeResponseDto>> getAll() {
        return ResponseEntity.ok(badgeService.getAll());
    }

    /**
     * Returns every badge with the given user's current progress and earned status.
     * This app has no concept of viewing another user's badges, so a userId that isn't
     * the caller's own is treated as not found - consistent with UserController and
     * BirdLogController's ownership scoping - to avoid confirming other ids exist.
     */
    @Operation(summary = "Get badges by user", description = "Returns every badge with this user's progress and earned status. userId must match the authenticated caller.")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserBadgeResponseDto>> getByUserId(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @PathVariable UUID userId) {
        if (!principal.getId().equals(userId)) {
            throw ResourceNotFoundException.of("User", userId);
        }
        return ResponseEntity.ok(badgeService.getByUserId(userId));
    }

    /** Admin-only: adds a new badge definition to the catalog. */
    @Operation(summary = "Create a badge", description = "Admin-only. Adds a new badge definition to the catalog.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BadgeResponseDto> create(@Valid @RequestBody CreateBadgeRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(badgeService.create(request));
    }

    /** Admin-only: removes a badge definition. */
    @Operation(summary = "Delete a badge", description = "Admin-only. Removes a badge definition and any users' progress toward it.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        badgeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
