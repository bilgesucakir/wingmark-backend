package com.wingmark.backend.service;

import com.wingmark.backend.dto.metrics.AdminMetricsResponseDto;

import java.util.Locale;

/**
 * Admin-only aggregate usage metrics. Computed fresh on every call rather than cached,
 * since the admin panel's "reload" action is just re-fetching this endpoint.
 */
public interface AdminMetricsService {

    /** Computes favorite-species, badge-completion, top-region and locale-usage metrics, with names resolved to the given locale. */
    AdminMetricsResponseDto compute(Locale locale);
}
