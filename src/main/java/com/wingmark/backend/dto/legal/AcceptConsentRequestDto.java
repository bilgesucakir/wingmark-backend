package com.wingmark.backend.dto.legal;

import com.wingmark.backend.enums.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcceptConsentRequestDto(
        @NotNull ConsentType type,
        @NotBlank String version
) {
}
