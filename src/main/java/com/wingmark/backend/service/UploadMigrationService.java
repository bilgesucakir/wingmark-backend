package com.wingmark.backend.service;

/** Copies photos from the database (GridFS) to R2. It never deletes anything from the database. */
public interface UploadMigrationService {

    /** What one run did. In dry-run mode the counts are what it would have done. */
    record Result(int copied, int alreadyThere, int failed, long bytes, boolean dryRun) {
    }

    /** Copies up to the per-run cap of photos and thumbnails that are not in R2 yet, verifying each copy by size. Safe to rerun. */
    Result migrate();
}
