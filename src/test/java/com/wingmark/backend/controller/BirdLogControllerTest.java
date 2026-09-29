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
import com.wingmark.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

        String registeredEmail = objectMapper.readTree(response).get("email").asText();
        return TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, registeredEmail, "password1");
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
        String token = registerAndGetToken("missing");

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

    @Test
    void createStoresGivenObservedAtOrDefaultsToUploadTime() throws Exception {
        String token = registerAndGetToken("obscreate");

        Map<String, Object> backdated = sightingBody();
        backdated.put("observedAt", "2026-09-18T09:45:00+03:00");
        mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backdated)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observedAt").value("2026-09-18T06:45:00Z"));

        Instant before = Instant.now();
        String created = mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sightingBody())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Instant defaulted = Instant.parse(objectMapper.readTree(created).get("observedAt").asText());
        assertThat(defaulted).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
    }

    @Test
    void unknownSpeciesIdIsUnprocessableNotNotFound() throws Exception {
        String token = registerAndGetToken("badspecies");

        Map<String, Object> body = sightingBody();
        body.put("speciesId", UUID.randomUUID().toString());
        mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void futureObservedAtIsRejected() throws Exception {
        String token = registerAndGetToken("obsfuture");

        Map<String, Object> body = sightingBody();
        body.put("observedAt", Instant.now().plus(Duration.ofDays(1)).toString());
        mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OBSERVED_AT_IN_FUTURE"))
                .andExpect(jsonPath("$.message").value("observedAt cannot be in the future"));
    }

    @Test
    void updateKeepsObservedAtWhenOmittedAndChangesItWhenGiven() throws Exception {
        String token = registerAndGetToken("obsupdate");

        Map<String, Object> body = sightingBody();
        body.put("observedAt", "2026-09-01T08:00:00Z");
        String created = mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String logId = objectMapper.readTree(created).get("id").asText();

        Map<String, Object> edit = sightingBody();
        edit.put("note", "Edited note");
        mockMvc.perform(put("/api/bird-logs/" + logId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("Edited note"))
                .andExpect(jsonPath("$.observedAt").value("2026-09-01T08:00:00Z"));

        edit.put("observedAt", "2026-08-15T17:30:00Z");
        mockMvc.perform(put("/api/bird-logs/" + logId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observedAt").value("2026-08-15T17:30:00Z"));
    }

    // ---------------------------------------------------------------- map viewport

    private void logAt(String token, double lat, double lng, String gender) throws Exception {
        Map<String, Object> body = sightingBody();
        body.put("latitude", lat);
        body.put("longitude", lng);
        body.put("gender", gender);
        mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void locationQueryHandlesTheAntimeridianAndFilters() throws Exception {
        String token = registerAndGetToken("mapdl");
        logAt(token, -17.7, 178.0, "MALE");    // Fiji, east of the date line
        logAt(token, -14.3, -170.7, "FEMALE"); // American Samoa, west of it
        logAt(token, 41.0, 29.0, "MALE");      // Istanbul, far outside the box

        // minLng > maxLng: a box from 170E across the date line to 170W.
        mockMvc.perform(get("/api/bird-logs/location")
                        .param("minLat", "-30").param("maxLat", "0")
                        .param("minLng", "170").param("maxLng", "-170")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/bird-logs/location")
                        .param("minLat", "-30").param("maxLat", "0")
                        .param("minLng", "170").param("maxLng", "-170")
                        .param("gender", "FEMALE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gender").value("FEMALE"));
    }

    @Test
    void locationQueryCapsResultsAndReportsTruncation() throws Exception {
        String token = registerAndGetToken("mapcap");
        for (int i = 0; i < 3; i++) {
            logAt(token, 10.0 + i * 0.01, 20.0, "UNKNOWN");
        }

        mockMvc.perform(get("/api/bird-logs/location")
                        .param("minLat", "9").param("maxLat", "11").param("minLng", "19").param("maxLng", "21")
                        .param("limit", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Truncated", "true"))
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/bird-logs/location")
                        .param("minLat", "9").param("maxLat", "11").param("minLng", "19").param("maxLng", "21")
                        .param("limit", "3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void locationQueryRejectsNonsenseBoundsAndLimits() throws Exception {
        String token = registerAndGetToken("mapbad");
        String[][] badBoxes = {
                {"-91", "0", "0", "10"},   // latitude out of range
                {"0", "10", "0", "181"},   // longitude out of range
                {"20", "10", "0", "10"},   // minLat > maxLat
        };
        for (String[] box : badBoxes) {
            mockMvc.perform(get("/api/bird-logs/location")
                            .param("minLat", box[0]).param("maxLat", box[1]).param("minLng", box[2]).param("maxLng", box[3])
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_BOUNDS"));
        }
        mockMvc.perform(get("/api/bird-logs/location")
                        .param("minLat", "0").param("maxLat", "1").param("minLng", "0").param("maxLng", "1")
                        .param("limit", "5000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private Map<String, Object> sightingBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("pet", false);
        body.put("lifeStage", "ADULT");
        body.put("gender", "MALE");
        body.put("latitude", 41.01);
        body.put("longitude", 28.97);
        return body;
    }

    private void setObservedAt(String logId, Instant observedAt) {
        BirdLog log = birdLogRepository.findById(UUID.fromString(logId)).orElseThrow();
        log.setObservedAt(observedAt);
        birdLogRepository.save(log);
    }
}
