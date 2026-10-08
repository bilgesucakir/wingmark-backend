package com.wingmark.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reads only the few fields the anonymous statistics need, so nothing else is loaded. */
public interface UsageStatsData {

    /** The dates of one user; no identity. */
    record UserRow(Instant createdAt, Instant lastLoginAt) {
    }

    /** The fields of one bird log used for statistics. */
    record LogRow(UUID userId, UUID speciesId, Instant createdAt, Double latitude, Double longitude) {
    }

    /** Returns the dates of every user. */
    List<UserRow> users();

    /** Returns the statistics fields of every bird log. */
    List<LogRow> logs();
}
