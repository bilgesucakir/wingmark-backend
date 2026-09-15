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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String shortId() {
        return Long.toString(System.nanoTime() % 1_000_000);
    }

    private record Registered(String userId, String email, String password, String username) {}

    private Registered register(String label) throws Exception {
        String id = shortId();
        String email = label + "-" + id + "@example.com";
        String password = "password1";
        String username = label + id;

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", password);
            put("username", username);
        }});
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return new Registered(user.getId().toString(), email, password, username);
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", password);
        }});
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String promoteAndLogin(Registered registered) throws Exception {
        User user = userRepository.findByEmailIgnoreCase(registered.email()).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);
        return tokenFor(registered.email(), registered.password());
    }

    @Test
    void listingUsersRequiresAdminRole() throws Exception {
        Registered plain = register("plain");
        String plainToken = tokenFor(plain.email(), plain.password());

        // /api/admin/** requires authentication at the filter chain level (unlike
        // /api/species/**, which is permitAll and gated purely by @PreAuthorize), so a
        // request with no token at all is rejected as 401 rather than 403.
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUpdateAndDeleteAUser() throws Exception {
        Registered admin = register("useradmin");
        String adminToken = promoteAndLogin(admin);

        Registered target = register("target");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        String updateBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("firstName", "Updated");
            put("lastName", "Name");
            put("role", "ADMIN");
            put("emailVerified", true);
        }});
        mockMvc.perform(put("/api/admin/users/" + target.userId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.emailVerified").value(true));

        mockMvc.perform(delete("/api/admin/users/" + target.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCannotDeleteTheirOwnAccount() throws Exception {
        Registered admin = register("selfdelete");
        String adminToken = promoteAndLogin(admin);

        mockMvc.perform(delete("/api/admin/users/" + admin.userId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }
}
