package com.wingmark.backend.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SearchPatternTest {

    private static boolean matches(String search, String stored) {
        return Pattern.compile(SearchPattern.accentInsensitive(search), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(stored).find();
    }

    @Test
    void matchesAnywhereIgnoringCase() {
        assertThat(matches("spar", "House Sparrow")).isTrue();
        assertThat(matches("SPARROW", "house sparrow")).isTrue();
        assertThat(matches("finch", "House Sparrow")).isFalse();
    }

    @Test
    void turkishLettersMatchWithAndWithoutAccentsInBothDirections() {
        assertThat(matches("serce", "Serçe")).isTrue();
        assertThat(matches("SERÇE", "serce")).isTrue();
        assertThat(matches("kus", "Kuş")).isTrue();
        assertThat(matches("kuş", "Kus")).isTrue();
        assertThat(matches("guvercin", "Güvercin")).isTrue();
        assertThat(matches("sahin", "Şahin")).isTrue();
        assertThat(matches("karga", "Karğa")).isTrue();
        assertThat(matches("ispinoz", "İspinoz")).isTrue();
        assertThat(matches("ISPINOZ", "ıspınoz")).isTrue();
    }

    @Test
    void latinNamesWithAccentsFoldToo() {
        assertThat(matches("passer", "Passer domesticus")).isTrue();
        assertThat(matches("pica pica", "Pica pica")).isTrue();
        assertThat(matches("garrulus", "Gárrulus")).isTrue();
    }

    @Test
    void regexCharactersInTheSearchTextAreTakenLiterally() {
        assertThat(matches(".*", "House Sparrow")).isFalse();
        assertThat(matches("(a+)+$", "aaaa")).isFalse();
        assertThat(matches("a.b", "a.b")).isTrue();
        assertThat(matches("a.b", "axb")).isFalse();
        assertThat(matches("[x]", "x")).isFalse();
        assertThat(matches("c++", "c++ guide")).isTrue();
    }
}
