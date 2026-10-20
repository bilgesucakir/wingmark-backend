package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** An unknown email gets exactly the same answer as a known one, so the API does not reveal who has an account. */
@SpringBootTest
@AutoConfigureMockMvc
class UnknownEmailParityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private MvcResult call(String path, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andReturn();
    }

    private String registered() throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String email = "parity-" + id + "@example.com";
        assertThat(call("/api/auth/register", Map.of("email", email, "password", "birdsong2026",
                "username", "parity" + id, "confirmedAge13", true)).getResponse().getStatus()).isEqualTo(201);
        return email;
    }

    /** Status and body with the one value that legitimately differs (the timestamp) taken out. */
    private static String shape(MvcResult result) throws Exception {
        return result.getResponse().getStatus() + " " + result.getResponse().getContentAsString()
                .replaceAll("\"timestamp\":\"[^\"]*\"", "").replaceAll("\"path\":\"[^\"]*\"", "");
    }

    @Test
    void loginGivesTheSameAnswerForAnUnknownEmailAndAWrongPassword() throws Exception {
        String known = registered();

        MvcResult wrongPassword = call("/api/auth/login", Map.of("email", known, "password", "not-the-password1"));
        MvcResult unknownEmail = call("/api/auth/login", Map.of("email", "nobody-" + System.nanoTime() + "@example.com", "password", "not-the-password1"));

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(shape(unknownEmail)).isEqualTo(shape(wrongPassword));
    }

    @Test
    void forgotPasswordAndResendVerificationGiveTheSameAnswerForKnownAndUnknownEmails() throws Exception {
        String known = registered();
        String unknown = "nobody-" + System.nanoTime() + "@example.com";

        for (String path : new String[]{"/api/auth/forgot-password", "/api/auth/resend-verification-email"}) {
            MvcResult withAccount = call(path, Map.of("email", known));
            MvcResult withoutAccount = call(path, Map.of("email", unknown));

            assertThat(withAccount.getResponse().getStatus()).isEqualTo(202);
            assertThat(shape(withoutAccount)).isEqualTo(shape(withAccount));
        }
    }
}
