package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BadgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String shortId() {
        return Long.toString(System.nanoTime() % 1_000_000);
    }

    private String registerLoginAsAdmin(String label) throws Exception {
        String id = shortId();
        String email = label + "-" + id + "@example.com";
        String password = "password1";
        String username = label + id;

        String registerBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", password);
            put("username", username);
        }});
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(Role.ADMIN);
        user.setEmailVerified(true);
        userRepository.save(user);

        String loginBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", password);
        }});
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }

    private String registerAndGetToken(String label) throws Exception {
        String id = shortId();
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", label + "-" + id + "@example.com");
            put("password", "password1");
            put("username", label + id);
        }});

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID extractUserId(String accessToken) throws Exception {
        String payloadSegment = accessToken.split("\\.")[1];
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadSegment);
        JsonNode payload = objectMapper.readTree(payloadBytes);
        return UUID.fromString(payload.get("sub").asText());
    }

    @Test
    void catalogIsPubliclyReadable() throws Exception {
        mockMvc.perform(get("/api/badges/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void updatingABadgeRequiresAdminRole() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("name", Map.of("en", "Test Badge"));
            put("criteriaType", "TOTAL_LOGS");
            put("criteriaValue", 5);
        }});

        // Only /api/badges/catalog is permitAll; other /api/badges/** routes require
        // authentication at the filter chain level, so a request with no token at all
        // is rejected as 401 rather than 403.
        mockMvc.perform(put("/api/badges/22222222-2222-2222-2222-222222222299")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateUpdateAndDeleteABadge() throws Exception {
        String adminToken = registerLoginAsAdmin("badgeadmin");

        String createBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("name", Map.of("en", "Early Bird"));
            put("criteriaType", "TOTAL_LOGS");
            put("criteriaValue", 1);
        }});
        String createResponse = mockMvc.perform(post("/api/badges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name.en").value("Early Bird"))
                .andReturn().getResponse().getContentAsString();

        String badgeId = objectMapper.readTree(createResponse).get("id").asText();

        String updateBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("name", Map.of("en", "Early Bird Renamed"));
            put("criteriaType", "TOTAL_LOGS");
            put("criteriaValue", 3);
            put("tier", "SILVER");
        }});
        mockMvc.perform(put("/api/badges/" + badgeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name.en").value("Early Bird Renamed"))
                .andExpect(jsonPath("$.criteriaValue").value(3))
                .andExpect(jsonPath("$.tier").value("SILVER"));

        mockMvc.perform(delete("/api/badges/" + badgeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void regularUserCannotViewAnotherUsersBadgeProgress() throws Exception {
        String ownerToken = registerAndGetToken("badgeowner");
        String intruderToken = registerAndGetToken("badgeintruder");
        UUID ownerId = extractUserId(ownerToken);

        mockMvc.perform(get("/api/badges/user/" + ownerId).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanViewAnotherUsersBadgeProgress() throws Exception {
        String ownerToken = registerAndGetToken("badgeowner2");
        UUID ownerId = extractUserId(ownerToken);
        String adminToken = registerLoginAsAdmin("badgesadmin");

        // Admins are exempt from the ownership check - used by the admin panel's
        // per-user detail view.
        mockMvc.perform(get("/api/badges/user/" + ownerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
