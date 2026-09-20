package com.wingmark.backend.dto.metrics;

public record LocaleUsageCountDto(
        /** "en", "tr", another two-letter language code, or "unset" if the user never chose one. */
        String locale,
        long userCount
) {
}
