package com.wingmark.backend.dto.legal;

import com.wingmark.backend.enums.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request to accept a version of a legal document. */
public record AcceptConsentRequestDto(
        @NotNull ConsentType type,
        @NotBlank String version
) {
}
