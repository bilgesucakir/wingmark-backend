package com.wingmark.backend.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/** Picks one display string from a locale-to-text map: the requested locale, else English, else any translation. */
public final class LocalizedTextResolver {

/** Fallback locale. */
    public static final String DEFAULT_LOCALE = "en";

    private LocalizedTextResolver() {
    }

/** Returns the text for the locale, else English, else any translation; null if there are none. */
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
