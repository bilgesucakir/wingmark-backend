package com.wingmark.backend.config;

import com.wingmark.backend.service.InactiveAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Runs the inactive account cleanup on a schedule; exists only when {@code INACTIVITY_CLEANUP_ENABLED=true}. */
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wingmark.inactivity.enabled", havingValue = "true")
public class InactiveAccountJob {

    private final InactiveAccountService inactiveAccountService;

    /** Warns and deletes inactive accounts, once per schedule tick. */
    @Scheduled(cron = "${wingmark.inactivity.cron}", zone = "UTC")
    public void run() {
        inactiveAccountService.runCleanup(Instant.now());
    }
}
