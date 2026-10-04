package com.wingmark.backend.service;

import com.wingmark.backend.dto.metrics.AdminMetricsResponseDto;

import java.util.Locale;

/** Admin-only usage metrics, computed on every call. */
public interface AdminMetricsService {

    /** Computes favorite-species, badge-completion, top-region and locale metrics, with names in the given locale. */
    AdminMetricsResponseDto compute(Locale locale);
}
