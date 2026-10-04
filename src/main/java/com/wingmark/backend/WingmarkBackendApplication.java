package com.wingmark.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Spring Boot entry point of the Wingmark backend. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WingmarkBackendApplication {

/** Starts the application. */
    public static void main(String[] args) {
        SpringApplication.run(WingmarkBackendApplication.class, args);
    }

}
