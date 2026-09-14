package com.wingmark.backend.dto.user;

import com.wingmark.backend.enums.UnitPreference;
import jakarta.validation.constraints.NotNull;

public record UpdateSettingsRequestDto(
        @NotNull UnitPreference unitPreference,
        String locale
) {
}
