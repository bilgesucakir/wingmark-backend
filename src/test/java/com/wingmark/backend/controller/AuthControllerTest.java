package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullRegisterLoginAccessProtectedResourceFlow() throws Exception {
        String email = "flow-" + System.nanoTime() + "@example.com";
        String username = "flowuser" + System.nanoTime();

        String registerBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "password1");
            put("username", username);
            put("firstName", "Flow");
            put("lastName", "User");
        }});

        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

        // Without a token, the profile endpoint must reject the request.
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        // With the token from registration, it must succeed and reflect the new user.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.username").value(username));

        // Logging in again with the same credentials must also succeed.
        String loginBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "password1");
        }});
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void registerRejectsWeakPassword() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", "weak-" + System.nanoTime() + "@example.com");
            put("password", "short");
            put("username", "weakuser" + System.nanoTime());
        }});

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorizedWithConsistentErrorBody() throws Exception {
        String email = "nouser-" + System.nanoTime() + "@example.com";
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("email", email);
            put("password", "whatever1");
        }});

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
}
