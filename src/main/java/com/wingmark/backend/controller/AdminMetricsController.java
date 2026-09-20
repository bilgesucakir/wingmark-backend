package com.wingmark.backend.controller;

import com.wingmark.backend.dto.metrics.AdminMetricsResponseDto;
import com.wingmark.backend.service.AdminMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Admin-only aggregate usage metrics for the admin panel's metrics view: favorite
 * species counts, badge completion counts, most-logged regions, and locale split.
 */
@Tag(name = "Admin - Metrics", description = "Admin-only aggregate usage metrics")
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    /** Computes metrics fresh on every call - there is no server-side caching, so this endpoint doubles as the "recalculate" action. */
    @Operation(summary = "Get admin metrics", description = "Admin-only. Computes aggregate usage metrics fresh on every call: favorite-species counts, " +
            "badge completion counts, the most-logged regions, and the EN/TR (and other) locale split across users.")
    @GetMapping
    public ResponseEntity<AdminMetricsResponseDto> get(Locale locale) {
        return ResponseEntity.ok(adminMetricsService.compute(locale));
    }
}
