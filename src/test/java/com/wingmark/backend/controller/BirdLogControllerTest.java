package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BirdLogRepository birdLogRepository;

    @Autowired
    private SpeciesRepository speciesRepository;

    private String registerLoginAsAdmin(String label) throws Exception {
        String suffix = label + "-" + System.nanoTime();
        String email = suffix + "@example.com";
        String password = "password1";
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", password);
            put("username", "user" + suffix.replace("-", ""));
        }});
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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

        // A regular (non-admin) user still can't list the owner's logs by userId either.
        UUID ownerId = extractUserId(ownerToken);
        mockMvc.perform(get("/api/bird-logs/user/" + ownerId).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanViewAnotherUsersBirdLogsByUserId() throws Exception {
        String ownerToken = registerAndGetToken("logowner");
        UUID ownerId = extractUserId(ownerToken);

        String logBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("pet", false);
            put("lifeStage", "ADULT");
            put("gender", "MALE");
            put("note", "Admin-visible log");
            put("latitude", 1.0);
            put("longitude", 1.0);
        }});
        mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody))
                .andExpect(status().isCreated());

        String adminToken = registerLoginAsAdmin("logsadmin");

        // Admins are exempt from the ownership check - used by the admin panel's
        // per-user detail view.
        mockMvc.perform(get("/api/bird-logs/user/" + ownerId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.note == 'Admin-visible log')]").exists());
    }

    @Test
    void loggingAPetEarnsThePetBadge() throws Exception {
        badgeRepository.save(Badge.builder()
                .name(Map.of("en", "Proud Pet Parent"))
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

    @Test
    void getByUserIdSortsByObservedAtInEitherDirection() throws Exception {
        String token = registerAndGetToken("sortuser");
        UUID userId = extractUserId(token);

        String oldestId = createMinimalLog(token, "ADULT", "MALE", null);
        String middleId = createMinimalLog(token, "ADULT", "MALE", null);
        String newestId = createMinimalLog(token, "ADULT", "MALE", null);

        // Force a known, distinct observedAt ordering (all three would otherwise share
        // ~the same real-clock instant, since they're created back-to-back in the test).
        setObservedAt(oldestId, Instant.parse("2026-01-01T00:00:00Z"));
        setObservedAt(middleId, Instant.parse("2026-01-02T00:00:00Z"));
        setObservedAt(newestId, Instant.parse("2026-01-03T00:00:00Z"));

        // Sort.Direction binds like every other enum query param in this app: exact-case
        // only ("ASC"/"DESC"), same as gender/lifeStage below - see
        // getByUserIdFiltersByHasSpeciesGenderAndLifeStage for that same constraint.
        mockMvc.perform(get("/api/bird-logs/user/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(oldestId))
                .andExpect(jsonPath("$[1].id").value(middleId))
                .andExpect(jsonPath("$[2].id").value(newestId));

        mockMvc.perform(get("/api/bird-logs/user/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newestId))
                .andExpect(jsonPath("$[1].id").value(middleId))
                .andExpect(jsonPath("$[2].id").value(oldestId));

        // No sortDirection at all still defaults to DESC, unchanged from before this feature.
        mockMvc.perform(get("/api/bird-logs/user/" + userId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newestId));
    }

    @Test
    void getByUserIdFiltersByHasSpeciesGenderAndLifeStage() throws Exception {
        String token = registerAndGetToken("filteruser");
        UUID userId = extractUserId(token);

        Species species = speciesRepository.save(Species.builder()
                .commonName(Map.of("en", "Filter Test Bird"))
                .scientificName("Testus filterus " + System.nanoTime())
                .build());

        String withSpeciesAdultMale = createMinimalLog(token, "ADULT", "MALE", species.getId());
        String noSpeciesBabyFemale = createMinimalLog(token, "BABY", "FEMALE", null);

        mockMvc.perform(get("/api/bird-logs/user/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .param("hasSpecies", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(noSpeciesBabyFemale));

        mockMvc.perform(get("/api/bird-logs/user/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .param("hasSpecies", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(withSpeciesAdultMale));

        mockMvc.perform(get("/api/bird-logs/user/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .param("gender", "FEMALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(noSpeciesBabyFemale));

        mockMvc.perform(get("/api/bird-logs/user/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .param("lifeStage", "ADULT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(withSpeciesAdultMale));

        mockMvc.perform(get("/api/bird-logs/user/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    private String createMinimalLog(String token, String lifeStage, String gender, UUID speciesId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pet", false);
        body.put("lifeStage", lifeStage);
        body.put("gender", gender);
        body.put("latitude", 1.0);
        body.put("longitude", 1.0);
        if (speciesId != null) {
            body.put("speciesId", speciesId.toString());
        }

        String created = mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(created).get("id").asText();
    }

    private void setObservedAt(String logId, Instant observedAt) {
        BirdLog log = birdLogRepository.findById(UUID.fromString(logId)).orElseThrow();
        log.setObservedAt(observedAt);
        birdLogRepository.save(log);
    }
}
