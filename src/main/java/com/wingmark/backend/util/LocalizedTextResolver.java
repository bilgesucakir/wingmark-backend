package com.wingmark.backend.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves a {@code {"en": "...", "tr": "..."}}-style translation map down to a single
 * display string for convenience fields embedded in other resources (e.g. a bird log's
 * species name). Falls back from the requested locale to English, then to whatever
 * translation exists, since content is not guaranteed to be translated into every locale.
 */
public final class LocalizedTextResolver {

    public static final String DEFAULT_LOCALE = "en";

    private LocalizedTextResolver() {
    }

    public static String resolve(Map<String, String> translations, Locale locale) {
        if (translations == null || translations.isEmpty()) {
            return null;
        }

        if (locale != null) {
            String requested = translations.get(locale.getLanguage());
            if (StringUtils.hasText(requested)) {
                return requested;
            }
        }

        String fallback = translations.get(DEFAULT_LOCALE);
        if (StringUtils.hasText(fallback)) {
            return fallback;
        }

        return translations.values().stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }
}
