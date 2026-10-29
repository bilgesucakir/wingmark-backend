package com.wingmark.backend.service;

import com.wingmark.backend.dto.birdlog.BirdLogResponseDto;

/**
 * The result of creating a log.
 *
 * @param log      the new log, or the existing one when the same Idempotency-Key was used before
 * @param replayed true when an earlier call with the same key already created the log
 */
public record CreatedBirdLog(BirdLogResponseDto log, boolean replayed) {
}
