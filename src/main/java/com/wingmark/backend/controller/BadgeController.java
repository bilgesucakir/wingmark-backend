package com.wingmark.backend.controller;

import com.wingmark.backend.dto.badge.BadgeResponse;
import com.wingmark.backend.dto.badge.CreateBadgeRequest;
import com.wingmark.backend.dto.badge.UserBadgeResponse;
import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.BadgeService;
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

@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    @GetMapping("/catalog")
    public ResponseEntity<List<BadgeResponse>> catalog() {
        return ResponseEntity.ok(badgeService.listCatalog());
    }

    @GetMapping("/me")
    public ResponseEntity<List<UserBadgeResponse>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(badgeService.listForUser(principal.getId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BadgeResponse> create(@Valid @RequestBody CreateBadgeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(badgeService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        badgeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
