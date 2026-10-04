package com.wingmark.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenAPI api = new OpenApiConfig().openApi();

    @Test
    void describesTheApiAndNamesTheSupportContact() {
        assertThat(api.getInfo().getTitle()).isEqualTo("Wingmark API");
        assertThat(api.getInfo().getContact().getEmail()).isEqualTo("support.wingmark@gmail.com");
    }

    @Test
    void declaresTheBearerJwtSchemeAndRequiresItByDefault() {
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(api.getSecurity()).singleElement().satisfies(r -> assertThat(r).containsKey("bearerAuth"));
    }
}
