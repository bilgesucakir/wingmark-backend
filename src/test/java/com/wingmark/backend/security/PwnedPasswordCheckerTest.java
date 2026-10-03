package com.wingmark.backend.security;

import com.wingmark.backend.config.PwnedPasswordsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PwnedPasswordCheckerTest {

    private static final PwnedPasswordsProperties ENABLED = new PwnedPasswordsProperties(true, "https://api.pwnedpasswords.com");

    private static String sha1(String s) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static PwnedPasswordChecker checker(String responseBody, AtomicReference<ClientRequest> captured) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.pwnedpasswords.com")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body(responseBody).build());
                })
                .build();
        return new PwnedPasswordChecker(ENABLED, webClient);
    }

    @Test
    void sendsOnlyTheFiveCharacterHashPrefixAndMatchesTheSuffixLocally() throws Exception {
        String hash = sha1("kestrel-hover-77");
        AtomicReference<ClientRequest> request = new AtomicReference<>();
        PwnedPasswordChecker checker = checker("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + hash.substring(5) + ":42\r\n", request);

        assertThat(checker.isBreached("kestrel-hover-77")).isTrue();
        assertThat(request.get().url().getPath()).isEqualTo("/range/" + hash.substring(0, 5));
        assertThat(request.get().url().toString()).doesNotContain(hash.substring(5)).doesNotContain("kestrel");
        assertThat(request.get().headers().getFirst("Add-Padding")).isEqualTo("true");
    }

    @Test
    void paddingEntriesWithCountZeroAreNotMatches() throws Exception {
        String hash = sha1("kestrel-hover-77");
        assertThat(checker(hash.substring(5) + ":0\r\n", new AtomicReference<>()).isBreached("kestrel-hover-77")).isFalse();
    }

    @Test
    void failsOpenWhenTheApiIsUnavailable() {
        WebClient failing = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new RuntimeException("connection refused")))
                .build();
        assertThat(new PwnedPasswordChecker(ENABLED, failing).isBreached("anything-123")).isFalse();
    }

    @Test
    void disabledCheckerNeverCallsOut() {
        AtomicReference<ClientRequest> request = new AtomicReference<>();
        WebClient webClient = WebClient.builder().exchangeFunction(r -> {
            request.set(r);
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("").build());
        }).build();
        assertThat(new PwnedPasswordChecker(new PwnedPasswordsProperties(false, "x"), webClient).isBreached("x1234567890")).isFalse();
        assertThat(request.get()).isNull();
    }
}
