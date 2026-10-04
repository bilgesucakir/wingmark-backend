package com.wingmark.backend.dto.metrics;

/** Number of bird logs in a region grid cell. */
public record RegionLogCountDto(
        /** A "{lat}, {lng}" coordinate-grid cell (~11km per side), not BirdLog's free-text locationName. */
        String region,
        long logCount
) {
}
