package com.wingmark.backend.dto.legal;

import com.wingmark.backend.enums.ConsentType;

import java.time.Instant;

/** A recorded acceptance of a legal document version. */
public record ConsentResponseDto(
        ConsentType type,
        String version,
        Instant acceptedAt
) {
}
