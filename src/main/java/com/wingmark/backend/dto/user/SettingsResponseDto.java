package com.wingmark.backend.dto.user;

import com.wingmark.backend.enums.UnitPreference;

/** A user's app settings. */
public record SettingsResponseDto(
        UnitPreference unitPreference,
        String locale
) {
}
