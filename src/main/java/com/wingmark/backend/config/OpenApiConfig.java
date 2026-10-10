package com.wingmark.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata and the bearer-JWT security scheme for Swagger UI. */
@Configuration
public class OpenApiConfig {

    /** Name of the bearer-JWT security scheme. */
    private static final String BEARER_SCHEME = "bearerAuth";

    /** Builds the API title, description, contact and bearer-JWT scheme. */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wingmark API")
                        .description("Backend API for Wingmark, a personal bird-logging app. Log a bird sighting " +
                                "(photo, species, life stage, gender, location, notes), browse it on a map, look it " +
                                "up in a species guide with photos and live call/song recordings, and earn badges " +
                                "as you go. Personal-only for now: every user sees just their own logs. " +
                                "Send the access token as 'Authorization: Bearer <token>'; endpoints marked as public need no token. " +
                                "Errors use one JSON body (ErrorResponse) whose stable 'code' clients should branch on. " +
                                "Every endpoint is rate limited per IP, and credential and email endpoints also per account: " +
                                "exceeding a limit returns 429 with a Retry-After header.")
                        .version("v1")
                        .contact(new Contact().name("Wingmark support").email("support@wingmarkapp.com")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
