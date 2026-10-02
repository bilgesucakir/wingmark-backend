package com.wingmark.backend.dto.legal;

import com.wingmark.backend.enums.ConsentType;

import java.util.List;

/** Documents whose current version the user still has to accept (empty when up to date). */
public record PendingConsentsResponseDto(
        List<ConsentType> pendingConsents
) {
}
