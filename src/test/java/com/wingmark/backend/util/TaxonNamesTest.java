package com.wingmark.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonNamesTest {

    private final TaxonNames names = new TaxonNames(new ObjectMapper());

    @Test
    void knownNamesAreResolvedByLanguageIgnoringCaseAndSpaces() {
        // Orders keep their Latin name in both languages; families have a Turkish -giller name only where it is well known.
        assertThat(names.order("Passeriformes", Locale.forLanguageTag("tr"))).isEqualTo("Passeriformes");
        assertThat(names.order(" passeriformes ", Locale.ENGLISH)).isEqualTo("Passeriformes");
        assertThat(names.family("Corvidae", Locale.forLanguageTag("tr"))).isEqualTo("Kargagiller (Corvidae)");
        assertThat(names.family("Anatidae", Locale.forLanguageTag("tr"))).isEqualTo("Ördekgiller (Anatidae)");
        assertThat(names.family("Turdidae", Locale.forLanguageTag("tr"))).isEqualTo("Ardıçkuşugiller (Turdidae)");
        assertThat(names.family("Troglodytidae", Locale.forLanguageTag("tr"))).isEqualTo("Çıtkuşugiller (Troglodytidae)");
        assertThat(names.family("Accipitridae", Locale.forLanguageTag("tr"))).isEqualTo("Atmacagiller (Accipitridae)");
        // Families the book does not list keep the Latin name only, also the ones that had a guessed Turkish name before.
        assertThat(names.family("Psittacidae", Locale.forLanguageTag("tr"))).isEqualTo("Psittacidae");
        assertThat(names.family("Trochilidae", Locale.forLanguageTag("tr"))).isEqualTo("Trochilidae");
        assertThat(names.family("Alcidae", Locale.forLanguageTag("tr"))).isEqualTo("Alcidae");
    }

    @Test
    void anotherLanguageFallsBackToEnglishAndAnUnknownNameToItsLatinName() {
        assertThat(names.family("Corvidae", Locale.GERMAN)).isEqualTo("Corvidae");
        assertThat(names.family("Corvidae", Locale.ENGLISH)).isEqualTo("Corvidae");
        assertThat(names.order("Mysteriformes", Locale.ENGLISH)).isEqualTo("Mysteriformes");
        assertThat(names.family(" Mysteriidae ", Locale.forLanguageTag("tr"))).isEqualTo("Mysteriidae");
    }
}
