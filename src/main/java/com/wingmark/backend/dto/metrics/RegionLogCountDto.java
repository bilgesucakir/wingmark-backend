package com.wingmark.backend.dto.metrics;

public record RegionLogCountDto(
        /** A "{lat}, {lng}" coordinate-grid cell (~11km per side), not BirdLog's free-text locationName. */
        String region,
        long logCount
) {
}
