package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.EmailVerificationToken;
import com.wingmark.backend.entity.PasswordResetToken;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.repository.EmailVerificationTokenRepository;
import com.wingmark.backend.repository.PasswordResetTokenRepository;
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

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void fullRegisterLoginAccessProtectedResourceFlow() throws Exception {
        String email = "flow-" + System.nanoTime() + "@example.com";
        String username = "flowuser" + System.nanoTime();

        String registerBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "birdsong2026");
            put("username", username);
            put("confirmedAge13", true);
            put("firstName", "Flow");
            put("lastName", "User");
        }});

        // Registration creates an unverified account and hands out no tokens at all.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        String loginBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "birdsong2026");
        }});

        // The verification link hasn't been clicked yet, so login is refused.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isForbidden());

        // Verify directly via the repository, since there's no inbox to click a real link from here.
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();
        UUID userId = extractUserId(accessToken);

        // Without a token, the profile endpoint must reject the request.
        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/" + userId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.username").value(username));

        // Another user's id must not be accessible, even with a valid token of one's own.
        mockMvc.perform(get("/api/users/" + UUID.randomUUID()).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unverifyingAnAccountCutsOffItsTokensAndRefresh() throws Exception {
        String email = "unverify-" + System.nanoTime() + "@example.com";
        String registerBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "birdsong2026");
            put("username", "unv" + System.nanoTime() % 1_000_000_000L);
            put("confirmedAge13", true);
        }});
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "birdsong2026"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tokens = objectMapper.readTree(loginResponse);
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        // e.g. an admin un-verifies the account from the panel.
        user.setEmailVerified(false);
        userRepository.save(user);

        // The still-unexpired access token is rejected on the very next request...
        mockMvc.perform(get("/api/users/" + user.getId()).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        // ...and it can't be refreshed into a new one either.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyEmailEndpointMarksAccountVerifiedAndUnblocksLogin() throws Exception {
        String email = "verify-" + System.nanoTime() + "@example.com";
        String username = "verifyuser" + System.nanoTime();

        String registerBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "birdsong2026");
            put("username", username);
            put("confirmedAge13", true);
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
            put("password", "birdsong2026");
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
            put("confirmedAge13", true);
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

    // ---------------------------------------------------------------- helpers for the flows below

    private record Session(UUID userId, String email, String accessToken, String refreshToken) {}

    private Session verifiedSession(String label) throws Exception {
        String email = label + "-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "password", "birdsong2026", "username", label + System.nanoTime() % 1_000_000, "confirmedAge13", true))))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        return login(email, "birdsong2026", user.getId());
    }

    private Session login(String email, String password, UUID userId) throws Exception {
        JsonNode tokens = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return new Session(userId, email, tokens.get("accessToken").asText(), tokens.get("refreshToken").asText());
    }

    private void expectProfile(Session session, int status) throws Exception {
        mockMvc.perform(get("/api/users/" + session.userId()).header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().is(status));
    }

    // ---------------------------------------------------------------- error codes

    @Test
    void errorResponsesCarryMachineReadableCodes() throws Exception {
        String email = "codes-" + System.nanoTime() + "@example.com";
        String register = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "birdsong2026", "username", "codes" + System.nanoTime() % 1_000_000, "confirmedAge13", true));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "birdsong2026"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "wrong-password1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.email").exists());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", "nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));

        mockMvc.perform(get("/api/users/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/species").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    // ---------------------------------------------------------------- reset code

    @Test
    void resetCodeFlowSetsNewPasswordAndKillsExistingSessions() throws Exception {
        Session session = verifiedSession("resetflow");

        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", session.email()))))
                .andExpect(status().isAccepted());
        // The emailed code is random; replace it with a known one (same salted-hash format)
        // since there's no inbox to read it from here.
        PasswordResetToken issued = passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(session.userId()).orElseThrow();
        issued.setTokenHash(TokenHasher.sha256(session.userId() + ":" + "482913"));
        passwordResetTokenRepository.save(issued);

        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", session.email(), "code", "000000", "newPassword", "brandnew123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_CODE"));

        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", session.email(), "code", "482913", "newPassword", "brandnew123"))))
                .andExpect(status().isNoContent());

        // Old access token, old refresh token and old password all stop working at once.
        expectProfile(session, 401);
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", session.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", session.email(), "password", "birdsong2026"))))
                .andExpect(status().isUnauthorized());

        // The code is single-use.
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", session.email(), "code", "482913", "newPassword", "another123"))))
                .andExpect(status().isBadRequest());

        expectProfile(login(session.email(), "brandnew123", session.userId()), 200);
    }

    @Test
    void resetPasswordValidatesCodeFormat() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", "a@example.com", "code", "12ab", "newPassword", "brandnew123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.code").exists());
    }

    // ---------------------------------------------------------------- change password / logout-all

    @Test
    void changePasswordKeepsThisDeviceAndSignsOutTheOthers() throws Exception {
        Session phone = verifiedSession("chpw");
        Session tablet = login(phone.email(), "birdsong2026", phone.userId());

        mockMvc.perform(post("/api/users/" + phone.userId() + "/password")
                        .header("Authorization", "Bearer " + phone.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("currentPassword", "wrong-one1", "newPassword", "brandnew123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        mockMvc.perform(post("/api/users/" + phone.userId() + "/password")
                        .header("Authorization", "Bearer " + phone.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("currentPassword", "birdsong2026", "newPassword", "birdsong2026"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));

        JsonNode fresh = objectMapper.readTree(mockMvc.perform(post("/api/users/" + phone.userId() + "/password")
                        .header("Authorization", "Bearer " + phone.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("currentPassword", "birdsong2026", "newPassword", "brandnew123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString());

        expectProfile(phone, 401);
        expectProfile(tablet, 401);
        expectProfile(new Session(phone.userId(), phone.email(), fresh.get("accessToken").asText(), null), 200);
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", tablet.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", fresh.get("refreshToken").asText()))))
                .andExpect(status().isOk());
    }

    @Test
    void logoutAllInvalidatesAccessTokensImmediately() throws Exception {
        Session phone = verifiedSession("logoutall");
        Session tablet = login(phone.email(), "birdsong2026", phone.userId());

        mockMvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + phone.accessToken()))
                .andExpect(status().isNoContent());

        expectProfile(phone, 401);
        expectProfile(tablet, 401);
        // Logging in again works normally and isn't affected by the cut-off.
        expectProfile(login(phone.email(), "birdsong2026", phone.userId()), 200);
    }
}
