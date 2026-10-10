package com.wingmark.backend.controller;

import com.wingmark.backend.dto.badge.BadgeResponseDto;
import com.wingmark.backend.dto.badge.UserBadgeResponseDto;
import com.wingmark.backend.service.BadgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Admin-only badge views with the full details of secret badges. */
@Tag(name = "Admin - Badges", description = "Admin-only badge views including secret badges")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBadgeController {

    private final BadgeService badgeService;

    /** Admin-only: every badge definition, secret ones included. */
    @Operation(summary = "Get all badges including secret ones", description = "Admin-only. Returns every badge definition sorted by displayOrder, " +
            "including secret badges, which the public GET /api/badges/catalog leaves out.")
    @GetMapping("/badges")
    public ResponseEntity<List<BadgeResponseDto>> getAll() {
        return ResponseEntity.ok(badgeService.getAllForAdmin());
    }

    /** Admin-only: one user's badges with full details, including secret badges that user has not earned. */
    @Operation(summary = "Get a user's badges with secret details", description = "Admin-only. Like GET /api/badges/user/{userId}, but a secret badge " +
            "the user has not earned is shown with its name, description, icon, progress and target instead of being masked.")
    @GetMapping("/users/{id}/badges")
    public ResponseEntity<List<UserBadgeResponseDto>> getUserBadges(@PathVariable UUID id, Locale locale) {
        return ResponseEntity.ok(badgeService.getByUserIdForAdmin(id, locale));
    }
}
