package com.wingmark.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final XenoCantoProperties xenoCantoProperties;
    private final INaturalistProperties iNaturalistProperties;

    @Bean
    public WebClient xenoCantoWebClient() {
        return WebClient.builder()
                .baseUrl(xenoCantoProperties.baseUrl())
                .build();
    }

    @Bean
    public WebClient iNaturalistWebClient() {
        return WebClient.builder()
                .baseUrl(iNaturalistProperties.baseUrl())
                .build();
    }
}
