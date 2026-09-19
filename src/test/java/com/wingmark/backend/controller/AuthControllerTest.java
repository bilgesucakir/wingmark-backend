package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.EmailVerificationToken;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.repository.EmailVerificationTokenRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.util.TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    void fullRegisterLoginAccessProtectedResourceFlow() throws Exception {
        String email = "flow-" + System.nanoTime() + "@example.com";
        String username = "flowuser" + System.nanoTime();

        String registerBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "password1");
            put("username", username);
            put("firstName", "Flow");
            put("lastName", "User");
        }});

        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();
        UUID userId = extractUserId(accessToken);

        // Without a token, the profile endpoint must reject the request.
        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isUnauthorized());

        // With the token from registration, it must succeed and reflect the new user.
        mockMvc.perform(get("/api/users/" + userId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.username").value(username));

        // Another user's id must not be accessible, even with a valid token of one's own.
        mockMvc.perform(get("/api/users/" + UUID.randomUUID()).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        String loginBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "password1");
        }});

        // A freshly-registered account hasn't clicked the verification link yet, so
        // logging in again (e.g. reopening the app on a new device) must be rejected.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isForbidden());

        // Verify the account the same way an admin would confirm a role change - directly
        // via the repository - since there's no inbox to click a real email link from here.
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        // Logging in again now succeeds.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void verifyEmailEndpointMarksAccountVerifiedAndUnblocksLogin() throws Exception {
        String email = "verify-" + System.nanoTime() + "@example.com";
        String username = "verifyuser" + System.nanoTime();

        String registerBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "password1");
            put("username", username);
        }});
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        // register() already issued a real verification token/email in the background;
        // this test plants its own known token instead of trying to intercept that one.
        emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.sha256("known-verification-token"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        mockMvc.perform(get("/api/auth/verify-email").param("token", "known-verification-token"))
                .andExpect(status().isOk());

        String loginBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "password1");
        }});
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void registerRejectsWeakPassword() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", "weak-" + System.nanoTime() + "@example.com");
            put("password", "short");
            put("username", "weakuser" + System.nanoTime());
        }});

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorizedWithConsistentErrorBody() throws Exception {
        String email = "nouser-" + System.nanoTime() + "@example.com";
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "whatever1");
        }});

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    private UUID extractUserId(String accessToken) throws Exception {
        String payloadSegment = accessToken.split("\\.")[1];
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadSegment);
        JsonNode payload = objectMapper.readTree(payloadBytes);
        return UUID.fromString(payload.get("sub").asText());
    }
}
