package com.wingmark.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String registerAndGetToken() throws Exception {
        String id = Long.toString(System.nanoTime() % 1_000_000);
        String email = "upload-" + id + "@example.com";
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("email", email);
            put("password", "birdsong2026");
            put("username", "upload" + id);
        }});
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return TestAuth.verifyAndLogin(mockMvc, objectMapper, userRepository, email, "birdsong2026");
    }

    private byte[] jpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void uploadingAPhotoReturnsAUploadsUrl() throws Exception {
        String token = registerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(multipart("/api/uploads/photo")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/uploads/")));
    }

    @Test
    void uploadingWithoutAuthenticationIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(multipart("/api/uploads/photo").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonImageUploadsAreRejectedWith415AndACode() throws Exception {
        String token = registerAndGetToken();
        MockMultipartFile html = new MockMultipartFile("file", "x.html", "text/html", "<script>alert(1)</script>".getBytes());

        mockMvc.perform(multipart("/api/uploads/photo").file(html).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void corruptImageIsABadRequestNotAServerError() throws Exception {
        String token = registerAndGetToken();
        MockMultipartFile fake = new MockMultipartFile("file", "x.jpg", "image/jpeg", "not really a jpeg".getBytes());

        mockMvc.perform(multipart("/api/uploads/photo").file(fake).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    void nonMultipartRequestIsABadRequestNotAServerError() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/uploads/photo").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void uploadedImageIsServedFromTheDatabaseWithCachingAndNosniff() throws Exception {
        String token = registerAndGetToken();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());
        String url = objectMapper.readTree(mockMvc.perform(multipart("/api/uploads/photo").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("url").asText();

        // Public, no token - same as the old static /uploads route.
        byte[] served = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(served))).isNotNull();
    }

    @Test
    void missingUploadIsA404WithCode() throws Exception {
        mockMvc.perform(get("/uploads/" + java.util.UUID.randomUUID() + ".jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
