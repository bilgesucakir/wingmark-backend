package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class BirdLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

        String created = mockMvc.perform(post("/api/logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("Saw a sparrow on the fence"))
                .andReturn().getResponse().getContentAsString();

        String logId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/logs/" + logId).header("Authorization", "Bearer " + token))
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

        String created = mockMvc.perform(post("/api/logs")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String logId = objectMapper.readTree(created).get("id").asText();

        // The owner can see it...
        mockMvc.perform(get("/api/logs/" + logId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // ...but another authenticated user gets a 404, not the owner's data or a 403
        // that would reveal the log exists.
        mockMvc.perform(get("/api/logs/" + logId).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // The intruder's own log list must not include the owner's log either.
        mockMvc.perform(get("/api/logs").header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void loggingAPetEarnsThePetBadge() throws Exception {
        String token = registerAndGetToken("petowner");

        String logBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("pet", true);
            put("customName", "Buddy");
            put("lifeStage", "ADULT");
            put("gender", "MALE");
            put("latitude", 5.0);
            put("longitude", 5.0);
        }});

        mockMvc.perform(post("/api/logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/badges/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.badgeName == 'Proud Pet Parent')].earned").value(org.hamcrest.Matchers.hasItem(true)));
    }
}
