package com.wingmark.backend.service;

import java.time.Instant;

/** Finds and deletes stored photos that nothing refers to any more. */
public interface OrphanedUploadService {

    /** What one run did. In dry-run mode the counts are what it would have done. */
    record Result(int photos, long bytes, boolean dryRun) {
    }

    /**
     * Deletes (or, in dry-run mode, only reports) photos and thumbnails that no bird log, profile picture or species
     * image uses and that were stored longer ago than the grace period, up to the per-run cap.
     */
    Result cleanup(Instant now);
}
