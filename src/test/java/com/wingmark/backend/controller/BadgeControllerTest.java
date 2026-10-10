package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.support.TestAuth;
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
        String password = "birdsong2026";
        String username = label + id;

        String registerBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", password);
            put("username", username);
            put("confirmedAge13", true);
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
            put("password", "birdsong2026");
            put("username", label + id);
            put("confirmedAge13", true);
        }});

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String registeredEmail = objectMapper.readTree(response).get("email").asText();
        return TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, registeredEmail, "birdsong2026");
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

    private String createSecretBadge(String adminToken, String name, int target) throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("name", Map.of("en", name));
            put("description", Map.of("en", "Secret description"));
            put("icon", "secret-icon");
            put("criteriaType", "TOTAL_LOGS");
            put("criteriaValue", target);
            put("tier", "GOLD");
            put("secret", true);
        }});
        String response = mockMvc.perform(post("/api/badges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private JsonNode findById(String arrayJson, String idField, String id) throws Exception {
        for (JsonNode node : objectMapper.readTree(arrayJson)) {
            if (id.equals(node.get(idField).asText())) {
                return node;
            }
        }
        return null;
    }

    @Test
    void aSecretBadgeIsMissingFromThePublicCatalogButListedForAdmins() throws Exception {
        String adminToken = registerLoginAsAdmin("secretcatalog");
        String badgeId = createSecretBadge(adminToken, "Hidden Gem", 1000);

        String publicCatalog = mockMvc.perform(get("/api/badges/catalog")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String adminCatalog = mockMvc.perform(get("/api/admin/badges").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(publicCatalog).doesNotContain("Hidden Gem").doesNotContain(badgeId);
        JsonNode adminEntry = findById(adminCatalog, "id", badgeId);
        org.assertj.core.api.Assertions.assertThat(adminEntry).isNotNull();
        org.assertj.core.api.Assertions.assertThat(adminEntry.get("secret").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(adminEntry.get("name").get("en").asText()).isEqualTo("Hidden Gem");
    }

    @Test
    void aLockedSecretBadgeIsMaskedForTheUserAndFullyShownToAnAdmin() throws Exception {
        String adminToken = registerLoginAsAdmin("secretadmin");
        String badgeId = createSecretBadge(adminToken, "Locked Gem", 1000);
        String userToken = registerAndGetToken("secretuser");
        UUID userId = extractUserId(userToken);

        String own = mockMvc.perform(get("/api/badges/user/" + userId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode masked = findById(own, "badgeId", badgeId);
        org.assertj.core.api.Assertions.assertThat(masked).isNotNull();
        org.assertj.core.api.Assertions.assertThat(masked.get("secret").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(masked.get("earned").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(masked.get("tier").asText()).isEqualTo("GOLD");
        org.assertj.core.api.Assertions.assertThat(own).doesNotContain("Locked Gem").doesNotContain("Secret description").doesNotContain("secret-icon");
        for (String hidden : new String[] {"badgeName", "badgeIcon", "badgeDescription", "progress", "targetValue"}) {
            org.assertj.core.api.Assertions.assertThat(masked.get(hidden).isNull()).as(hidden).isTrue();
        }

        // An admin account asking through the user endpoint is masked too; the admin endpoint shows everything.
        String viaUserEndpoint = mockMvc.perform(get("/api/badges/user/" + userId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(viaUserEndpoint).doesNotContain("Locked Gem");
        String full = mockMvc.perform(get("/api/admin/users/" + userId + "/badges").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode revealed = findById(full, "badgeId", badgeId);
        org.assertj.core.api.Assertions.assertThat(revealed.get("badgeName").asText()).isEqualTo("Locked Gem");
        org.assertj.core.api.Assertions.assertThat(revealed.get("targetValue").asInt()).isEqualTo(1000);
    }

    @Test
    void theAdminBadgeEndpointsRequireAnAdmin() throws Exception {
        String userToken = registerAndGetToken("notanadmin");
        UUID userId = extractUserId(userToken);

        mockMvc.perform(get("/api/admin/badges")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/badges").header("Authorization", "Bearer " + userToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users/" + userId + "/badges").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEarnedSecretBadgeIsShownInFullAndKeepsItsId() throws Exception {
        String adminToken = registerLoginAsAdmin("secretearn");
        String badgeId = createSecretBadge(adminToken, "Earned Gem", 1);
        String userToken = registerAndGetToken("secretearner");
        UUID userId = extractUserId(userToken);

        String logBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("lifeStage", "ADULT");
            put("gender", "UNKNOWN");
            put("latitude", 41.0);
            put("longitude", 29.0);
        }});
        mockMvc.perform(post("/api/bird-logs").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(logBody))
                .andExpect(status().isCreated());

        String own = mockMvc.perform(get("/api/badges/user/" + userId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode earned = findById(own, "badgeId", badgeId);
        org.assertj.core.api.Assertions.assertThat(earned.get("earned").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(earned.get("secret").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(earned.get("badgeName").asText()).isEqualTo("Earned Gem");
        org.assertj.core.api.Assertions.assertThat(earned.get("badgeDescription").asText()).isEqualTo("Secret description");
        org.assertj.core.api.Assertions.assertThat(earned.get("badgeIcon").asText()).isEqualTo("secret-icon");
    }

    @Test
    void theDataExportDoesNotRevealALockedSecretBadge() throws Exception {
        String adminToken = registerLoginAsAdmin("secretexport");
        createSecretBadge(adminToken, "Export Gem", 1000);
        String userToken = registerAndGetToken("secretexporter");
        UUID userId = extractUserId(userToken);

        String export = mockMvc.perform(get("/api/users/" + userId + "/export").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(export).doesNotContain("Export Gem").doesNotContain("Secret description");
    }
}
