package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.AppIconRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppIconControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AppIconRepository appIconRepository;

    @BeforeEach
    void emptyList() {
        appIconRepository.deleteAll();
    }

    private String token(Role role) throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String email = "icon-" + role.name().toLowerCase() + "-" + id + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "birdsong2026",
                                "username", "icon" + id, "confirmedAge13", true))))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, email, "birdsong2026");
    }

    private MockHttpServletRequestBuilder add(String iconId, String token) throws Exception {
        return post("/api/admin/app-icons").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("iconId", iconId, "nameEn", "Halloween", "nameTr", "Cadılar Bayramı")));
    }

    @Test
    void theAppListNeedsLoginIsEmptyByDefaultAndCacheable() throws Exception {
        mockMvc.perform(get("/api/app-icons")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/app-icons").header("Authorization", "Bearer " + token(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anAddedIconShowsInTheAppListAndDisappearsWhenDeleted() throws Exception {
        String admin = token(Role.ADMIN);

        mockMvc.perform(add("halloween", admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iconId").value("halloween"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(get("/api/app-icons").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].iconId").value("halloween"))
                .andExpect(jsonPath("$[0].nameEn").value("Halloween"))
                .andExpect(jsonPath("$[0].nameTr").value("Cadılar Bayramı"))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
        mockMvc.perform(get("/api/admin/app-icons").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].createdAt").exists());

        mockMvc.perform(delete("/api/admin/app-icons/halloween").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/app-icons").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(delete("/api/admin/app-icons/halloween").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingTheSameIconIdTwiceIsAConflict() throws Exception {
        String admin = token(Role.ADMIN);
        mockMvc.perform(add("valentines", admin)).andExpect(status().isCreated());
        mockMvc.perform(add("valentines", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void anInvalidIconIdOrMissingNameIsRejected() throws Exception {
        String admin = token(Role.ADMIN);
        for (String bad : new String[]{"Halloween", "has space", "../x", "", "a".repeat(41)}) {
            mockMvc.perform(add(bad, admin)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/admin/app-icons").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iconId", "newyear", "nameEn", "New Year"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/app-icons").header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void theAdminEndpointsNeedTheAdminRole() throws Exception {
        String user = token(Role.USER);
        mockMvc.perform(get("/api/admin/app-icons")).andExpect(status().isUnauthorized());
        mockMvc.perform(add("christmas", "")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/app-icons").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        mockMvc.perform(add("christmas", user)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/app-icons/christmas").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/app-icons").header("Authorization", "Bearer " + user)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anAdminEditsTheNamesButNotTheIdAndAnUnknownIconIsNotFound() throws Exception {
        String admin = token(Role.ADMIN);
        mockMvc.perform(add("christmas", admin)).andExpect(status().isCreated());

        String body = objectMapper.writeValueAsString(Map.of("nameEn", "Xmas", "nameTr", "Yılbaşı", "iconId", "other"));
        mockMvc.perform(put("/api/admin/app-icons/christmas").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iconId").value("christmas"))
                .andExpect(jsonPath("$.nameEn").value("Xmas"))
                .andExpect(jsonPath("$.nameTr").value("Yılbaşı"));
        mockMvc.perform(get("/api/app-icons").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nameEn").value("Xmas"));

        mockMvc.perform(put("/api/admin/app-icons/nope").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/app-icons/christmas").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nameEn\":\"\",\"nameTr\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/app-icons/christmas").header("Authorization", "Bearer " + token(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
