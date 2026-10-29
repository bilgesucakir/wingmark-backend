package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.security.BcryptGate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** When every password-check slot is busy, a login is answered with 429 and Retry-After, not 503 or a hang. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"wingmark.bcrypt.max-concurrent=1", "wingmark.bcrypt.wait-millis=30", "wingmark.bcrypt.retry-after-seconds=3"})
class LoginLoadCapTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BcryptGate gate;

    @Test
    void loginIsTurnedAwayWith429WhileTheOnlySlotIsBusyAndWorksAgainAfter() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread busy = new Thread(() -> gate.run(() -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }));
        busy.setDaemon(true);
        busy.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        String body = objectMapper.writeValueAsString(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com", "password", "whatever1"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "3"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        release.countDown();
        busy.join(5000);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
