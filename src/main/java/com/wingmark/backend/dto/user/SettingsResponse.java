package com.wingmark.backend.dto.user;

import com.wingmark.backend.enums.UnitPreference;

public record SettingsResponse(
        UnitPreference unitPreference,
        String locale
) {
}
