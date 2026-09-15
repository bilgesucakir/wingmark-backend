package com.wingmark.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin panel itself is static HTML/CSS/JS served from src/main/resources/static/admin,
 * so it must be reachable without a token (the page's own JS handles the actual admin
 * login/authorization against the API); these just confirm the security filter chain
 * exposes it, not that the panel's client-side logic works.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPanelStaticResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsPubliclyServable() throws Exception {
        mockMvc.perform(get("/admin/login.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void dashboardPageIsPubliclyServable() throws Exception {
        mockMvc.perform(get("/admin/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void staticAssetsArePubliclyServable() throws Exception {
        mockMvc.perform(get("/admin/api.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/app.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/style.css"))
                .andExpect(status().isOk());
    }
}
