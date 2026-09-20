package com.wingmark.backend.controller;

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

import java.util.HashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMetricsControllerTest {

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

    @Test
    void metricsRequireAdminRole() throws Exception {
        String plainToken = registerAndGetToken("metricsplain");

        // /api/admin/** requires authentication at the filter chain level, so a request
        // with no token at all is rejected as 401 rather than 403.
        mockMvc.perform(get("/api/admin/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/metrics").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanFetchMetrics() throws Exception {
        String adminToken = registerLoginAsAdmin("metricsadmin");

        mockMvc.perform(get("/api/admin/metrics").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.favoriteSpecies").isArray())
                .andExpect(jsonPath("$.badgeCompletions").isArray())
                .andExpect(jsonPath("$.topRegions").isArray())
                .andExpect(jsonPath("$.localeUsage").isArray())
                .andExpect(jsonPath("$.calculatedAt").exists());
    }
}
