package com.wingmark.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Fails when the generated Swagger document is missing documentation, so new endpoints and fields cannot slip by. */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerCompletenessTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");

    @Autowired
    private MockMvc mockMvc;

    private JsonNode api;

    @BeforeEach
    void loadDocument() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        api = new ObjectMapper().readTree(json);
    }

    private List<String> operations() {
        List<String> keys = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> paths = api.get("paths").fields(); paths.hasNext(); ) {
            Map.Entry<String, JsonNode> path = paths.next();
            for (Iterator<String> methods = path.getValue().fieldNames(); methods.hasNext(); ) {
                String method = methods.next();
                if (HTTP_METHODS.contains(method)) {
                    keys.add(method.toUpperCase() + " " + path.getKey());
                }
            }
        }
        return keys;
    }

    private JsonNode operation(String key) {
        String[] parts = key.split(" ", 2);
        return api.get("paths").get(parts[1]).get(parts[0].toLowerCase());
    }

    @Test
    void everyOperationHasASummaryAndADescription() {
        List<String> missing = operations().stream()
                .filter(key -> !operation(key).hasNonNull("summary") || !operation(key).hasNonNull("description")).toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void publicEndpointsRequireNoTokenAndTheOthersInheritTheBearerRequirement() {
        for (String key : operations()) {
            boolean documentedPublic = operation(key).has("security") && operation(key).get("security").isEmpty();
            String path = key.split(" ", 2)[1];
            boolean expectedPublic = OpenApiDetailsCustomizer.PUBLIC_OPERATIONS.contains(key);
            assertThat(documentedPublic).as(key + " (" + path + ")").isEqualTo(expectedPublic);
        }
        assertThat(api.get("security").toString()).contains("bearerAuth");
    }

    @Test
    void everyOperationDocumentsItsRealSuccessStatusAndNeverTheDefault200ForCreatesAndDeletes() {
        for (String key : operations()) {
            JsonNode responses = operation(key).get("responses");
            boolean success = responses.has("200") || responses.has("201") || responses.has("202") || responses.has("204");
            assertThat(success).as(key + " has a success status").isTrue();
            if (key.startsWith("DELETE ")) {
                assertThat(responses.has("204")).as(key).isTrue();
            }
        }
        assertThat(operation("POST /api/bird-logs").get("responses").has("201")).isTrue();
        assertThat(operation("POST /api/auth/forgot-password").get("responses").has("202")).isTrue();
        assertThat(operation("POST /api/auth/login").get("responses").has("200")).isTrue();
    }

    @Test
    void errorResponsesUseTheSharedErrorBody() {
        assertThat(api.get("components").get("schemas").has("ErrorResponse")).isTrue();
        for (String key : operations()) {
            JsonNode responses = operation(key).get("responses");
            for (Iterator<Map.Entry<String, JsonNode>> it = responses.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> response = it.next();
                if (Integer.parseInt(response.getKey()) >= 400) {
                    assertThat(response.getValue().toString()).as(key + " " + response.getKey())
                            .contains("#/components/schemas/ErrorResponse");
                }
            }
        }
        assertThat(operation("POST /api/auth/login").get("responses").has("429")).isTrue();
        assertThat(operation("DELETE /api/badges/{id}").get("responses").has("403")).isTrue();
        assertThat(operation("GET /api/badges/catalog").get("responses").has("401")).isFalse();
    }

    @Test
    void everyParameterHasADescription() {
        List<String> missing = new ArrayList<>();
        for (String key : operations()) {
            JsonNode parameters = operation(key).get("parameters");
            if (parameters == null) {
                continue;
            }
            parameters.forEach(parameter -> {
                if (!parameter.hasNonNull("description")) {
                    missing.add(key + " " + parameter.get("name").asText());
                }
            });
        }

        assertThat(missing).isEmpty();
    }

    @Test
    void everySchemaFieldHasADescription() {
        List<String> missing = new ArrayList<>();
        api.get("components").get("schemas").fields().forEachRemaining(schema -> {
            JsonNode properties = schema.getValue().get("properties");
            if (properties == null) {
                return;
            }
            properties.fields().forEachRemaining(property -> {
                if (!property.getValue().hasNonNull("description")) {
                    missing.add(schema.getKey() + "." + property.getKey());
                }
            });
        });

        assertThat(missing).isEmpty();
    }
}
