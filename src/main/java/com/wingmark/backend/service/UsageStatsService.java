package com.wingmark.backend.service;

import com.wingmark.backend.dto.stats.UsageStatsResponseDto;

import java.time.Instant;

/** Computes anonymous usage statistics from data already stored. Nothing is saved and no personal data is returned. */
public interface UsageStatsService {

    /** Computes the statistics as of the given moment. */
    UsageStatsResponseDto compute(Instant now);
}
