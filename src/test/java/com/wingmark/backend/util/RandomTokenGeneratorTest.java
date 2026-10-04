package com.wingmark.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RandomTokenGeneratorTest {

    @Test
    void numericCodeHasTheRequestedLengthAndOnlyDigits() {
        for (int i = 0; i < 200; i++) {
            assertThat(RandomTokenGenerator.numericCode(6)).matches("\\d{6}");
        }
    }

    @Test
    void generatedTokensAreUrlSafeAndDifferEachTime() {
        String first = RandomTokenGenerator.generate();
        assertThat(first).matches("[A-Za-z0-9_-]{64}");
        assertThat(RandomTokenGenerator.generate()).isNotEqualTo(first);
    }
}
