package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminBirdLogControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BirdLogRepository birdLogRepository;

    private User register(String label, Role role) throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String email = label + "-" + id + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "birdsong2026",
                                "username", label + id, "confirmedAge13", true))))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(role);
        return userRepository.save(user);
    }

    private String login(User user) throws Exception {
        return TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, user.getEmail(), "birdsong2026");
    }

    @Test
    void onlyAnAdminMayDeleteSomeoneElsesLog() throws Exception {
        User owner = register("logowner", Role.USER);
        BirdLog log = birdLogRepository.save(BirdLog.builder().userId(owner.getId()).build());
        String userToken = login(register("loguser", Role.USER));

        mockMvc.perform(delete("/api/admin/bird-logs/" + log.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/bird-logs/" + log.getId()).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        assertThat(birdLogRepository.existsById(log.getId())).isTrue();
    }

    @Test
    void anAdminDeletesAnyUsersLogAndAnUnknownIdIsNotFound() throws Exception {
        User owner = register("logowner2", Role.USER);
        BirdLog log = birdLogRepository.save(BirdLog.builder().userId(owner.getId()).build());
        String adminToken = login(register("logadmin", Role.ADMIN));

        mockMvc.perform(delete("/api/admin/bird-logs/" + log.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertThat(birdLogRepository.existsById(log.getId())).isFalse();

        mockMvc.perform(delete("/api/admin/bird-logs/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
