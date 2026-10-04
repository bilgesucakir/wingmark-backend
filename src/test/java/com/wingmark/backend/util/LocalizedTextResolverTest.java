package com.wingmark.backend.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedTextResolverTest {

    private static final Map<String, String> TEXTS = Map.of("en", "Sparrow", "tr", "Serçe");

    @Test
    void returnsTheRequestedLocale() {
        assertThat(LocalizedTextResolver.resolve(TEXTS, Locale.forLanguageTag("tr"))).isEqualTo("Serçe");
    }

    @Test
    void fallsBackToEnglishWhenTheLocaleIsMissingOrNull() {
        assertThat(LocalizedTextResolver.resolve(TEXTS, Locale.GERMAN)).isEqualTo("Sparrow");
        assertThat(LocalizedTextResolver.resolve(TEXTS, null)).isEqualTo("Sparrow");
    }

    @Test
    void fallsBackToAnyTranslationWhenThereIsNoEnglishOne() {
        assertThat(LocalizedTextResolver.resolve(Map.of("tr", "Serçe"), Locale.GERMAN)).isEqualTo("Serçe");
    }

    @Test
    void ignoresBlankTextsAndReturnsNullWhenNothingIsUsable() {
        assertThat(LocalizedTextResolver.resolve(Map.of("en", "Sparrow", "tr", " "), Locale.forLanguageTag("tr")))
                .isEqualTo("Sparrow");
        assertThat(LocalizedTextResolver.resolve(Map.of("en", " "), Locale.ENGLISH)).isNull();
        assertThat(LocalizedTextResolver.resolve(Map.of(), Locale.ENGLISH)).isNull();
        assertThat(LocalizedTextResolver.resolve(null, Locale.ENGLISH)).isNull();
    }
}
