package com.wingmark.backend.dto.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Anonymous usage statistics. Only aggregates: no email, name, user id, device or exact location. Species, regions
 * and sightings-per-user buckets are shown only when at least {@code minGroupSize} different users contribute.
 */
public record UsageStatsResponseDto(
        Instant generatedAt,
        int minGroupSize,
        long totalUsers,
        long activeUsersLast7Days,
        long activeUsersLast30Days,
        long newUsersLast30Days,
        long totalLogs,
        long logsLast7Days,
        long logsLast30Days,
        List<DayCount> logsPerDay,
        List<WeekCount> logsPerWeek,
        List<SightingsBucket> sightingsPerUser,
        List<SpeciesCount> topSpecies,
        List<RegionCount> regions
) {

    /** Logs created on one day (UTC). */
    public record DayCount(LocalDate date, long logs) {
    }

    /** Logs created in the week that starts on the given Monday (UTC). */
    public record WeekCount(LocalDate weekStart, long logs) {
    }

    /** How many users have a number of logs within a range; {@code users} is null when too few to show. */
    public record SightingsBucket(String range, Long users) {
    }

    /** A species and how many logs and different users it has; only species with enough users are listed. */
    public record SpeciesCount(String speciesName, long logs, long users) {
    }

    /** A coarse one-degree grid cell (about 110 km) with its logs and users; only cells with enough users are listed. */
    public record RegionCount(String cell, long logs, long users) {
    }
}
