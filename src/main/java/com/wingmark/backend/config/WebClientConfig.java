package com.wingmark.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Web clients for the external Xeno-canto and Wikimedia Commons APIs. */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final XenoCantoProperties xenoCantoProperties;
    private final CommonsProperties commonsProperties;

    /** Client for the Xeno-canto API. */
    @Bean
    public WebClient xenoCantoWebClient() {
        return WebClient.builder()
                .baseUrl(xenoCantoProperties.baseUrl())
                .build();
    }

    /** Client for the Wikimedia Commons API, always sending the descriptive User-Agent Commons requires. */
    @Bean
    public WebClient commonsWebClient() {
        return WebClient.builder()
                .baseUrl(commonsProperties.baseUrl())
                .defaultHeader("User-Agent", commonsProperties.userAgent())
                .build();
    }
}
