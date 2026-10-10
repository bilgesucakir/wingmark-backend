package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.ConsentType;
import com.wingmark.backend.repository.ConsentRepository;
import com.wingmark.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Terms/privacy acceptance end to end, with both documents "published" via configuration. */
@SpringBootTest(properties = {
        "wingmark.legal.terms-version=2026-10-01",
        "wingmark.legal.terms-url=https://wingmarkapp.com/terms",
        "wingmark.legal.privacy-version=2026-09-15",
        "wingmark.legal.privacy-url=https://wingmarkapp.com/privacy"
})
@AutoConfigureMockMvc
class LegalConsentFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsentRepository consentRepository;

    private Map<String, Object> registerBody(String label) {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        Map<String, Object> body = new HashMap<>();
        body.put("email", label + id + "@example.com");
        body.put("password", "birdsong2026");
        body.put("username", label + id);
        body.put("confirmedAge13", true);
        return body;
    }

    @Test
    void legalInfoIsPublicAndListsTheCurrentVersions() throws Exception {
        mockMvc.perform(get("/api/legal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsVersion").value("2026-10-01"))
                .andExpect(jsonPath("$.termsUrl").value("https://wingmarkapp.com/terms"))
                .andExpect(jsonPath("$.privacyVersion").value("2026-09-15"))
                .andExpect(jsonPath("$.minimumAge").value(13));
    }

    @Test
    void signupWithoutAcceptingTheCurrentVersionsIsRejectedAndCreatesNothing() throws Exception {
        Map<String, Object> body = registerBody("noterms");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));

        body.put("acceptedTermsVersion", "2026-10-01");
        body.put("acceptedPrivacyVersion", "2020-01-01"); // stale version
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIVACY_NOT_ACCEPTED"));

        assertThat(userRepository.findByEmailIgnoreCase((String) body.get("email"))).isEmpty();
    }

    @Test
    void acceptedSignupIsRecordedAndReacceptanceFlowsThroughLoginAndTheConsentEndpoint() throws Exception {
        Map<String, Object> body = registerBody("terms");
        body.put("acceptedTermsVersion", "2026-10-01");
        body.put("acceptedPrivacyVersion", "2026-09-15");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        String email = (String) body.get("email");
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        UUID userId = user.getId();
        assertThat(consentRepository.findByUserIdOrderByCreatedAtAsc(userId))
                .extracting(c -> c.getType() + "@" + c.getVersion())
                .containsExactly("TERMS@2026-10-01", "PRIVACY@2026-09-15", "AGE@13");

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", "birdsong2026"));
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConsents").isEmpty())
                .andReturn().getResponse().getContentAsString());
        String token = login.get("accessToken").asText();

        // Simulate the user having accepted only an older Terms version.
        consentRepository.deleteAll(consentRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(c -> c.getType() == ConsentType.TERMS).toList());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(jsonPath("$.pendingConsents[0]").value("TERMS"))
                .andExpect(jsonPath("$.pendingConsents.length()").value(1));

        mockMvc.perform(post("/api/users/" + userId + "/consents").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "TERMS", "version", "2025-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_MISMATCH"));

        mockMvc.perform(post("/api/users/" + userId + "/consents").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "TERMS", "version", "2026-10-01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConsents").isEmpty());

        mockMvc.perform(get("/api/users/" + userId + "/consents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].acceptedAt").exists());
    }

    private void acceptLegalVersions(Map<String, Object> body) {
        body.put("acceptedTermsVersion", "2026-10-01");
        body.put("acceptedPrivacyVersion", "2026-09-15");
    }

    @Test
    void signupWithoutConfirmingTheMinimumAgeIsRejectedAndCreatesNothing() throws Exception {
        for (Object confirmation : new Object[]{null, false}) {
            Map<String, Object> body = registerBody("young");
            acceptLegalVersions(body);
            body.put("confirmedAge13", confirmation);

            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AGE_NOT_CONFIRMED"));

            assertThat(userRepository.findByEmailIgnoreCase((String) body.get("email"))).isEmpty();
        }
    }

    @Test
    void anExistingUserWithoutAnAgeConfirmationIsAskedOnLoginAndTheConfirmationClearsIt() throws Exception {
        Map<String, Object> body = registerBody("old");
        acceptLegalVersions(body);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
        String email = (String) body.get("email");
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        // An account from before the age check has no AGE consent.
        consentRepository.deleteAll(consentRepository.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .filter(c -> c.getType() == ConsentType.AGE).toList());

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", "birdsong2026"));
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConsents[0]").value("AGE"))
                .andExpect(jsonPath("$.pendingConsents.length()").value(1))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/users/" + user.getId() + "/consents").header("Authorization", "Bearer " + login.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "AGE", "version", "13"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConsents").isEmpty());
    }
}
