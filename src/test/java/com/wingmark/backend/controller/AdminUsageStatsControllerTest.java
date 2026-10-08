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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUsageStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;

    private String loginAs(String label, Role role) throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String email = label + "-" + id + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "birdsong2026",
                                "username", label + id, "confirmedAge13", true))))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, email, "birdsong2026");
    }

    @Test
    void theStatisticsRequireAnAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/usage-stats")).andExpect(status().isUnauthorized());

        String userToken = loginAs("statsuser", Role.USER);
        mockMvc.perform(get("/api/admin/usage-stats").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminGetsAggregatesOnlyWithoutAnyPersonalData() throws Exception {
        String adminToken = loginAs("statsadmin", Role.ADMIN);

        String body = mockMvc.perform(get("/api/admin/usage-stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode stats = objectMapper.readTree(body);

        assertThat(stats.get("totalUsers").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("minGroupSize").asInt()).isEqualTo(5);
        assertThat(stats.get("logsPerDay")).hasSize(30);
        assertThat(stats.get("logsPerWeek")).hasSize(12);
        assertThat(stats.get("sightingsPerUser")).hasSize(5);
        // Aggregates only: no address, no user id and no coordinates anywhere in the response.
        assertThat(body).doesNotContain("@example.com").doesNotContain("userId").doesNotContain("latitude").doesNotContain("longitude");
    }
}
