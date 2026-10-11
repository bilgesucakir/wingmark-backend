package com.wingmark.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonNamesTest {

    private final TaxonNames names = new TaxonNames(new ObjectMapper());

    @Test
    void knownNamesAreResolvedByLanguageIgnoringCaseAndSpaces() {
        assertThat(names.order("Passeriformes", Locale.forLanguageTag("tr"))).isEqualTo("Ötücü Kuşlar");
        assertThat(names.order(" passeriformes ", Locale.ENGLISH)).isEqualTo("Perching Birds");
        assertThat(names.family("Corvidae", Locale.forLanguageTag("tr"))).isEqualTo("Karga ve Alakargalar");
    }

    @Test
    void anotherLanguageFallsBackToEnglishAndAnUnknownNameToItsLatinName() {
        assertThat(names.family("Corvidae", Locale.GERMAN)).isEqualTo("Crows & Jays");
        assertThat(names.order("Mysteriformes", Locale.ENGLISH)).isEqualTo("Mysteriformes");
        assertThat(names.family(" Mysteriidae ", Locale.forLanguageTag("tr"))).isEqualTo("Mysteriidae");
    }
}
