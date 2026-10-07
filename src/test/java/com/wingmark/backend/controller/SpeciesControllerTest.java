package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Promotes a freshly-registered test user to ADMIN directly via the repository (the
 * same way a real operator would via mongosh/Compass) rather than exercising any
 * "become admin" API flow, since this app deliberately doesn't expose one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpeciesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpeciesRepository speciesRepository;

    private String shortId() {
        return Long.toString(System.nanoTime() % 1_000_000);
    }

    private String registerAndGetToken(String label) throws Exception {
        String id = shortId();
        String email = label + "-" + id + "@example.com";
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
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

    @Test
    void guideEndpointsArePubliclyReadable() throws Exception {
        speciesRepository.save(Species.builder()
                .commonName(Map.of("en", "House Sparrow"))
                .scientificName("Passer domesticus " + System.nanoTime())
                .build());

        mockMvc.perform(get("/api/species"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/species?search=Sparrow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].commonName.en").value("House Sparrow"));
    }

    @Test
    void creatingASpeciesRequiresAdminRole() throws Exception {
        String userToken = registerAndGetToken("plainuser");

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("commonName", Map.of("en", "Test Bird"));
            put("scientificName", "Testus birdus " + System.nanoTime());
        }});

        // Only GETs on /api/species/** are public, so a write with no token at all is
        // unauthenticated (401), not merely forbidden.
        mockMvc.perform(post("/api/species").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // Authenticated but not an admin -> forbidden.
        mockMvc.perform(post("/api/species")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanCreateUpdateAndDeleteASpecies() throws Exception {
        String adminToken = registerLoginAsAdmin("speciesadmin");
        String scientificName = "Testus createdus " + System.nanoTime();

        String createBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("commonName", Map.of("en", "Created Bird"));
            put("scientificName", scientificName);
        }});

        String createResponse = mockMvc.perform(post("/api/species")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commonName.en").value("Created Bird"))
                .andReturn().getResponse().getContentAsString();

        String speciesId = objectMapper.readTree(createResponse).get("id").asText();

        String updateBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("commonName", Map.of("en", "Renamed Bird"));
            put("scientificName", scientificName);
        }});
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/species/" + speciesId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonName.en").value("Renamed Bird"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/species/" + speciesId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/species/" + speciesId))
                .andExpect(status().isNotFound());
    }

    @Test
    void probeSortByNestedCommonNameField() throws Exception {
        String marker = "SortProbe" + System.nanoTime();
        speciesRepository.save(Species.builder()
                .commonName(Map.of("en", "Charlie " + marker, "tr", "Alfa " + marker))
                .scientificName("Zzz " + marker).build());
        speciesRepository.save(Species.builder()
                .commonName(Map.of("en", "Alpha " + marker, "tr", "Charlie " + marker))
                .scientificName("Aaa " + marker).build());
        speciesRepository.save(Species.builder()
                .commonName(Map.of("en", "Bravo " + marker, "tr", "Bravo " + marker))
                .scientificName("Mmm " + marker).build());

        mockMvc.perform(get("/api/species").param("search", marker).param("sort", "commonName.en,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].commonName.en").value("Alpha " + marker))
                .andExpect(jsonPath("$.content[1].commonName.en").value("Bravo " + marker))
                .andExpect(jsonPath("$.content[2].commonName.en").value("Charlie " + marker));

        mockMvc.perform(get("/api/species").param("search", marker).param("sort", "scientificName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].scientificName").value("Aaa " + marker))
                .andExpect(jsonPath("$.content[1].scientificName").value("Mmm " + marker))
                .andExpect(jsonPath("$.content[2].scientificName").value("Zzz " + marker));
    }

    @Test
    void photoCandidatesEndpointIsAdminOnly() throws Exception {
        String userToken = registerAndGetToken("nonadmin");

        mockMvc.perform(get("/api/species/22222222-2222-2222-2222-222222222201/photo-candidates")
                        .param("lifeStage", "ADULT")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchMatchesTurkishNamesWithoutAccentsAndScientificNames() throws Exception {
        String marker = Long.toString(System.nanoTime());
        speciesRepository.save(Species.builder()
                .commonName(Map.of("en", "Searchtest Sparrow " + marker, "tr", "Çalı Serçesi " + marker))
                .scientificName("Zzgenus latinus " + marker)
                .build());

        // Turkish name typed without accents.
        mockMvc.perform(get("/api/species").param("search", "cali sercesi " + marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].scientificName").value("Zzgenus latinus " + marker));
        // Typed with accents against a value stored with them, in capitals.
        mockMvc.perform(get("/api/species").param("search", "ÇALI SERÇESİ " + marker))
                .andExpect(jsonPath("$.content.length()").value(1));
        // Scientific name, in another case.
        mockMvc.perform(get("/api/species").param("search", "ZZGENUS LATINUS " + marker))
                .andExpect(jsonPath("$.content[0].commonName.en").value("Searchtest Sparrow " + marker));
        // Regex characters are literal.
        mockMvc.perform(get("/api/species").param("search", ".*" + marker))
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
