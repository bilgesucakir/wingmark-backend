package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.repository.BadgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BirdLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BadgeRepository badgeRepository;

    private String registerAndGetToken(String label) throws Exception {
        String suffix = label + "-" + System.nanoTime();
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", suffix + "@example.com");
            put("password", "password1");
            put("username", "user" + suffix.replace("-", ""));
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
    void creatingAndFetchingOwnLogSucceeds() throws Exception {
        String token = registerAndGetToken("owner");

        String logBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("pet", false);
            put("lifeStage", "ADULT");
            put("gender", "MALE");
            put("note", "Saw a sparrow on the fence");
            put("latitude", 41.01);
            put("longitude", 28.97);
        }});

        String created = mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("Saw a sparrow on the fence"))
                .andReturn().getResponse().getContentAsString();

        String logId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/bird-logs/" + logId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(logId));
    }

    @Test
    void anotherUserCannotSeeSomeoneElsesLog() throws Exception {
        String ownerToken = registerAndGetToken("owner2");
        String intruderToken = registerAndGetToken("intruder");

        String logBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("pet", true);
            put("customName", "Kiwi");
            put("lifeStage", "ADULT");
            put("gender", "FEMALE");
            put("latitude", 10.0);
            put("longitude", 10.0);
        }});

        String created = mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String logId = objectMapper.readTree(created).get("id").asText();

        // The owner can see it...
        mockMvc.perform(get("/api/bird-logs/" + logId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // ...but another authenticated user gets a 404, not the owner's data or a 403
        // that would reveal the log exists.
        mockMvc.perform(get("/api/bird-logs/" + logId).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // The intruder's own log list must not include the owner's log either.
        UUID intruderId = extractUserId(intruderToken);
        mockMvc.perform(get("/api/bird-logs/user/" + intruderId).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // The plain "get all" endpoint is admin-only - a regular user is forbidden.
        mockMvc.perform(get("/api/bird-logs").header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void loggingAPetEarnsThePetBadge() throws Exception {
        badgeRepository.save(Badge.builder()
                .name("Proud Pet Parent")
                .criteriaType(BadgeCriteriaType.PET_LOGS)
                .criteriaValue(1)
                .build());

        String token = registerAndGetToken("petowner");

        String logBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("pet", true);
            put("customName", "Buddy");
            put("lifeStage", "ADULT");
            put("gender", "MALE");
            put("latitude", 5.0);
            put("longitude", 5.0);
        }});

        mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody))
                .andExpect(status().isCreated());

        UUID userId = extractUserId(token);
        mockMvc.perform(get("/api/badges/user/" + userId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.badgeName == 'Proud Pet Parent')].earned").value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test
    void malformedJsonBodyReturnsBadRequestNotServerError() throws Exception {
        String token = registerAndGetToken("malformed");

        mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pet\": false \"lifeStage\": \"ADULT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unmappedPathReturnsNotFoundNotServerError() throws Exception {
        String token = registerAndGetToken("unmapped");

        mockMvc.perform(get("/api/logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidUuidPathVariableReturnsBadRequestNotServerError() throws Exception {
        String token = registerAndGetToken("baduuid");

        mockMvc.perform(get("/api/bird-logs/not-a-real-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredQueryParamReturnsBadRequestNotServerError() throws Exception {
        String token = registerAndGetToken("missingparam");

        mockMvc.perform(get("/api/bird-logs/location")
                        .header("Authorization", "Bearer " + token)
                        .param("minLat", "0").param("maxLat", "1").param("minLng", "0"))
                // maxLng deliberately omitted
                .andExpect(status().isBadRequest());
    }
}
