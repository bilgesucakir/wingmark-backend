package com.wingmark.backend.config;

import com.wingmark.backend.service.UnverifiedAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Runs the unverified account cleanup on a schedule; exists only when {@code UNVERIFIED_CLEANUP_ENABLED=true}. */
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wingmark.unverified-cleanup.enabled", havingValue = "true")
public class UnverifiedAccountJob {

    private final UnverifiedAccountService unverifiedAccountService;

    /** Deletes abandoned signups, once per schedule tick. */
    @Scheduled(cron = "${wingmark.unverified-cleanup.cron}", zone = "UTC")
    public void run() {
        unverifiedAccountService.purge(Instant.now());
    }
}
