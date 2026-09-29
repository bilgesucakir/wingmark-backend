package com.wingmark.backend.dto.birdlog;

import java.util.List;

/** Logs inside a map viewport, plus whether the result was cut off at the requested limit. */
public record BirdLogLocationResultDto(
        List<BirdLogResponseDto> logs,
        boolean truncated
) {
}
