package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Registered(String userId, String accessToken) {}

    private Registered register(String label) throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String email = label + "-" + id + "@example.com";
        String username = label + id;

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", "password1");
            put("username", username);
        }});
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(response).get("accessToken").asText();
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
}
