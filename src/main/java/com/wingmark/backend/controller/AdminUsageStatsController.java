package com.wingmark.backend.controller;

import com.wingmark.backend.dto.stats.UsageStatsResponseDto;
import com.wingmark.backend.service.UsageStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Admin-only anonymous usage statistics: aggregates only, no personal data. */
@Tag(name = "Admin - Usage statistics", description = "Admin-only anonymous usage statistics")
@RestController
@RequestMapping("/api/admin/usage-stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsageStatsController {

    private final UsageStatsService usageStatsService;

    /** Computes the statistics fresh on every call; nothing is stored. */
    @Operation(summary = "Get anonymous usage statistics", description = "Admin-only. Computes aggregates from existing data on every call and stores nothing: " +
            "user and activity counts, logs per day (30 days) and per week (12 weeks), how many users have how many logs, the most-logged species and coarse " +
            "one-degree regions. Never includes email, name, user id, device or exact location. Species, regions and buckets appear only when at least " +
            "minGroupSize different users contribute (STATS_MIN_GROUP_SIZE, default 5), so nobody can be singled out.")
    @GetMapping
    public ResponseEntity<UsageStatsResponseDto> get() {
        return ResponseEntity.ok(usageStatsService.compute(Instant.now()));
    }
}
