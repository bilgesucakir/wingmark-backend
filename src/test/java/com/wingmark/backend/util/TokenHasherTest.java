package com.wingmark.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void sameInputProducesSameHash() {
        assertThat(TokenHasher.sha256("raw-token")).isEqualTo(TokenHasher.sha256("raw-token"));
    }

    @Test
    void differentInputProducesDifferentHash() {
        assertThat(TokenHasher.sha256("token-a")).isNotEqualTo(TokenHasher.sha256("token-b"));
    }

    @Test
    void hashDoesNotContainRawToken() {
        String raw = "super-secret-refresh-token";
        assertThat(TokenHasher.sha256(raw)).doesNotContain(raw);
    }
}
