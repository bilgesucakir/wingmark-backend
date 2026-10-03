package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.repository.SpeciesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** App Store readiness hardening (docs/appstore/05-backend-tasks.md), with rate limiting switched on. */
@SpringBootTest(properties = {
        "wingmark.rate-limit.enabled=true",
        "wingmark.rate-limit.login-per-account-per15-min=3",
        "wingmark.rate-limit.global-per-ip-per-minute=30"
})
@AutoConfigureMockMvc
class AppStoreHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpeciesRepository speciesRepository;

    /** A fresh client address per test, so rate-limit buckets never leak between tests. */
    private static String newIp() {
        return "198.51.100." + (int) (Math.random() * 250 + 1) + "-" + UUID.randomUUID();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void loginIsRateLimitedPerAccountWith429AndRetryAfter() throws Exception {
        String email = "victim-" + UUID.randomUUID() + "@example.com";
        String body = json(Map.of("email", email, "password", "wrong-guess-123"));

        for (int attempt = 1; attempt <= 3; attempt++) {
            // Different IP every time: rotating addresses must not get around the per-account limit.
            mockMvc.perform(post("/api/auth/login").header("CF-Connecting-IP", newIp())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login").header("CF-Connecting-IP", newIp())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void wholeApiIsCappedPerIp() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/avatars").header("CF-Connecting-IP", ip)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/avatars").header("CF-Connecting-IP", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // Someone else is unaffected.
        mockMvc.perform(get("/api/avatars").header("CF-Connecting-IP", newIp())).andExpect(status().isOk());
    }

    @Test
    void signupEnforcesTheServerSidePasswordPolicy() throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String ip = newIp();
        mockMvc.perform(post("/api/auth/register").header("CF-Connecting-IP", ip).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "kite" + id + "@example.com", "password", "short1x", "username", "kite" + id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/auth/register").header("CF-Connecting-IP", ip).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "kite" + id + "@example.com", "password", "kite" + id + "pass9", "username", "kite" + id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        mockMvc.perform(post("/api/auth/register").header("CF-Connecting-IP", ip).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "kite" + id + "@example.com", "password", "Password123", "username", "kite" + id))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void speciesSearchTreatsRegexMetacharactersAsPlainText() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        speciesRepository.save(Species.builder()
                .commonName(Map.of("en", "Lark (" + marker + ") a+b"))
                .scientificName("Alauda " + marker)
                .build());

        // Literal match on text full of regex syntax.
        mockMvc.perform(get("/api/species").param("search", "(" + marker + ") a+b").header("CF-Connecting-IP", newIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        // ".*" is just two characters now, not "match everything".
        mockMvc.perform(get("/api/species").param("search", ".*" + marker).header("CF-Connecting-IP", newIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        // A catastrophic-backtracking pattern is harmless text, not a 500 or a slow query.
        mockMvc.perform(get("/api/species").param("search", "(a+)+$").header("CF-Connecting-IP", newIp()))
                .andExpect(status().isOk());
    }

    @Test
    void adminPanelIsServedWithAStrictContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/admin/login.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void foreignBrowserOriginsAreRefused() throws Exception {
        mockMvc.perform(options("/api/species")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void oversizedJsonBodiesAreRejectedBeforeParsing() throws Exception {
        String huge = "{\"email\":\"" + "a".repeat(1024 * 1024 + 10) + "\"}";
        mockMvc.perform(post("/api/auth/login").header("CF-Connecting-IP", newIp())
                        .contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }
}
