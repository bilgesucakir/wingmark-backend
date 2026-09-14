package com.wingmark.backend.dto.user;

import com.wingmark.backend.enums.UnitPreference;

public record SettingsResponseDto(
        UnitPreference unitPreference,
        String locale
) {
}
