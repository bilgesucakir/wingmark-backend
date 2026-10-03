package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.RefreshTokenRepository;
import com.wingmark.backend.repository.UserBadgeRepository;
import com.wingmark.backend.repository.UserSettingsRepository;
import com.wingmark.backend.support.TestAuth;
import com.wingmark.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BirdLogRepository birdLogRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private UserBadgeRepository userBadgeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private com.wingmark.backend.repository.AccountDeletionRepository accountDeletionRepository;

    private record Registered(String userId, String accessToken) {}

    private Registered register(String label) throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String email = label + "-" + id + "@example.com";
        String username = label + id;

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", "birdsong2026");
            put("username", username);
        }});
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String registeredEmail = objectMapper.readTree(response).get("email").asText();
        String accessToken = TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, registeredEmail, "birdsong2026");
        String payload = accessToken.split("\\.")[1];
        String subject = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(payload)).get("sub").asText();
        return new Registered(subject, accessToken);
    }

    @Test
    void updateProfileUpdatesTheCallersOwnName() throws Exception {
        Registered user = register("profile");

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("firstName", "Jane");
            put("lastName", "Doe");
        }});

        mockMvc.perform(put("/api/users/" + user.userId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void updateProfileForAnotherUsersIdIsTreatedAsNotFound() throws Exception {
        Registered user = register("otherprofile");

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("firstName", "Nope");
            put("lastName", "Nope");
        }});

        mockMvc.perform(put("/api/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSettingsReturnsTheCallersOwnSettings() throws Exception {
        Registered user = register("settings");

        mockMvc.perform(get("/api/users/" + user.userId() + "/settings")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void getSettingsForAnotherUsersIdIsTreatedAsNotFound() throws Exception {
        Registered user = register("othersettings");

        mockMvc.perform(get("/api/users/" + UUID.randomUUID() + "/settings")
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSettingsUpdatesTheCallersOwnUnitPreference() throws Exception {
        Registered user = register("updatesettings");

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("unitPreference", "METRIC");
        }});

        mockMvc.perform(put("/api/users/" + user.userId() + "/settings")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPreference").value("METRIC"));
    }

    @Test
    void settingsEndpointsRejectRequestsWithNoToken() throws Exception {
        Registered user = register("notoken");

        mockMvc.perform(get("/api/users/" + user.userId() + "/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownFavoriteSpeciesIsUnprocessableNotNotFound() throws Exception {
        Registered user = register("favref");

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("favoriteSpeciesId", UUID.randomUUID().toString());
        }});
        mockMvc.perform(put("/api/users/" + user.userId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void deleteOwnAccountRemovesAllDataAndRevokesEveryToken() throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String email = "gone-" + id + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "birdsong2026", "username", "gone" + id))))
                .andExpect(status().isCreated());
        TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, email, "birdsong2026");
        JsonNode tokens = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "birdsong2026"))))
                .andReturn().getResponse().getContentAsString());
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();
        UUID userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();

        String logPhoto = uploadPhoto(accessToken);
        String avatar = uploadPhoto(accessToken);
        createLog(accessToken, logPhoto);
        mockMvc.perform(put("/api/users/" + userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("profilePicture", avatar))))
                .andExpect(status().isOk());
        assertThat(storedInDb(logPhoto)).isTrue();
        assertThat(storedInDb(avatar)).isTrue();

        // Wrong password: refused, nothing touched.
        mockMvc.perform(delete("/api/users/" + userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "wrong-password1"))))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findById(userId)).isPresent();

        mockMvc.perform(delete("/api/users/" + userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "birdsong2026"))))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(birdLogRepository.findByUserId(userId)).isEmpty();
        assertThat(userSettingsRepository.findByUserId(userId)).isEmpty();
        assertThat(userBadgeRepository.findByUserId(userId)).isEmpty();
        assertThat(refreshTokenRepository.findAll()).noneMatch(t -> userId.equals(t.getUserId()));
        assertThat(storedInDb(logPhoto)).isFalse();
        assertThat(storedInDb(avatar)).isFalse();

        // Both the unexpired access token and the refresh token are dead immediately.
        mockMvc.perform(get("/api/users/" + userId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAnAccountKeepsAPhotoAnotherUserStillReferences() throws Exception {
        Registered owner = register("photoown");
        Registered other = register("photoref");

        String sharedPhoto = uploadPhoto(owner.accessToken());
        createLog(owner.accessToken(), sharedPhoto);
        createLog(other.accessToken(), sharedPhoto);

        mockMvc.perform(delete("/api/users/" + owner.userId())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "birdsong2026"))))
                .andExpect(status().isNoContent());

        assertThat(storedInDb(sharedPhoto)).isTrue();
    }

    @Test
    void cannotDeleteSomeoneElsesAccount() throws Exception {
        Registered caller = register("delcaller");
        Registered victim = register("delvictim");

        mockMvc.perform(delete("/api/users/" + victim.userId())
                        .header("Authorization", "Bearer " + caller.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "birdsong2026"))))
                .andExpect(status().isNotFound());
        assertThat(userRepository.findById(UUID.fromString(victim.userId()))).isPresent();
    }

    private String uploadPhoto(String accessToken) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        MockMultipartFile file = new MockMultipartFile("file", "bird.jpg", "image/jpeg", out.toByteArray());
        String response = mockMvc.perform(multipart("/api/uploads/photo").file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("url").asText();
    }

    private void createLog(String accessToken, String photoUrl) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("pet", false);
        body.put("lifeStage", "ADULT");
        body.put("gender", "MALE");
        body.put("latitude", 41.01);
        body.put("longitude", 28.97);
        body.put("photoUrl", photoUrl);
        mockMvc.perform(post("/api/bird-logs")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private boolean storedInDb(String url) {
        return fileStorageService.exists(url.substring(url.lastIndexOf('/') + 1));
    }

    @Test
    void profilePictureMustBeAPresetAnUploadOrNull() throws Exception {
        Registered user = register("avatarpick");

        mockMvc.perform(put("/api/users/" + user.userId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("profilePicture", "avatar-7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePicture").value("avatar-7"));

        String upload = uploadPhoto(user.accessToken());
        mockMvc.perform(put("/api/users/" + user.userId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("profilePicture", upload))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePicture").value(upload));

        for (String bad : new String[]{"avatar-99", "https://example.com/me.png", "/uploads/does-not-exist.jpg"}) {
            mockMvc.perform(put("/api/users/" + user.userId())
                            .header("Authorization", "Bearer " + user.accessToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("profilePicture", bad))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PROFILE_PICTURE"));
        }
    }

    @Test
    void exportReturnsAllOfTheCallersDataAsADownload() throws Exception {
        Registered user = register("exporter");
        createLog(user.accessToken(), null);
        createLog(user.accessToken(), null);

        mockMvc.perform(get("/api/users/" + user.userId() + "/export").header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.exportedAt").exists())
                .andExpect(jsonPath("$.profile.id").value(user.userId()))
                .andExpect(jsonPath("$.settings").exists())
                .andExpect(jsonPath("$.birdLogs.length()").value(2))
                .andExpect(jsonPath("$.badges").isArray())
                .andExpect(jsonPath("$.consents").isArray());
    }

    @Test
    void cannotExportSomeoneElsesData() throws Exception {
        Registered caller = register("expcaller");
        Registered other = register("expother");

        mockMvc.perform(get("/api/users/" + other.userId() + "/export").header("Authorization", "Bearer " + caller.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void selfDeletionLeavesOnlyAPersonalDataFreeAuditRecord() throws Exception {
        Registered user = register("audited");

        mockMvc.perform(delete("/api/users/" + user.userId())
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "birdsong2026"))))
                .andExpect(status().isNoContent());

        var audit = accountDeletionRepository.findAll().stream()
                .filter(a -> a.getUserId().toString().equals(user.userId()))
                .findFirst().orElseThrow();
        assertThat(audit.getInitiatedBy()).isEqualTo(com.wingmark.backend.enums.DeletionInitiator.SELF);
        assertThat(audit.getCompletedAt()).isNotNull();
    }
}
