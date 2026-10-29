package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Oversized emails and passwords are rejected by validation before they reach the database or bcrypt. */
@SpringBootTest
@AutoConfigureMockMvc
class InputSizeLimitsTest {

    // Well-formed addresses (local part up to 64, labels up to 63) so only the length rule can reject them.
    private static final String MAX_EMAIL = "a".repeat(64) + "@" + "b".repeat(63) + "." + "b".repeat(63) + "." + "b".repeat(61); // 254
    private static final String TOO_LONG_EMAIL = MAX_EMAIL + "b"; // 255
    private static final String TOO_LONG_PASSWORD = "a1".repeat(37); // 74
    private static final String MAX_PASSWORD = "a1".repeat(36); // 72

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    private ResultActions postJson(String path, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void loginRejectsAnOversizedEmailOrPasswordAndAcceptsOnesAtTheLimit() throws Exception {
        postJson("/api/auth/login", Map.of("email", TOO_LONG_EMAIL, "password", "x1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.email").value("Email must be at most 254 characters"));
        postJson("/api/auth/login", Map.of("email", "nobody@example.com", "password", TOO_LONG_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.password").value("Password must be at most 72 characters"));

        // At the limit it passes validation and fails as an ordinary wrong login.
        postJson("/api/auth/login", Map.of("email", MAX_EMAIL, "password", MAX_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyEndpointThatTakesAnEmailRejectsOneOverTheLimit() throws Exception {
        postJson("/api/auth/register", Map.of("email", TOO_LONG_EMAIL, "password", "birdsong2026", "username", "longmail", "confirmedAge13", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").value("Email must be at most 254 characters"));
        postJson("/api/auth/forgot-password", Map.of("email", TOO_LONG_EMAIL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists());
        postJson("/api/auth/resend-verification-email", Map.of("email", TOO_LONG_EMAIL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists());
        postJson("/api/auth/reset-password", Map.of("email", TOO_LONG_EMAIL, "code", "123456", "newPassword", "birdsong2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    void passwordsThatAreOnlyCheckedAreCappedAt72LikeNewOnes() throws Exception {
        String email = "limits-" + System.nanoTime() % 1_000_000 + "@example.com";
        postJson("/api/auth/register", Map.of("email", email, "password", "birdsong2026", "username", "limits" + System.nanoTime() % 100_000, "confirmedAge13", true))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String token = TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, email, "birdsong2026");

        mockMvc.perform(post("/api/users/" + user.getId() + "/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", TOO_LONG_PASSWORD, "newPassword", "birdsong2027"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.currentPassword").value("Password must be at most 72 characters"));
        mockMvc.perform(post("/api/users/" + user.getId() + "/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", "birdsong2026", "newPassword", "a1".repeat(37)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.newPassword").exists());
        mockMvc.perform(delete("/api/users/" + user.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", TOO_LONG_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }
}
