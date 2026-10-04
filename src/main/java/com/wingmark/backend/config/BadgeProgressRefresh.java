package com.wingmark.backend.config;

import com.wingmark.backend.service.BadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * On startup, recomputes every user's progress for all badges, so a changed progress rule shows up after a deploy.
 * Safe to rerun; set {@code BADGE_RECOMPUTE_ON_STARTUP=false} to skip it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wingmark.badges.recompute-on-startup", havingValue = "true", matchIfMissing = true)
public class BadgeProgressRefresh implements ApplicationRunner {

    private final BadgeService badgeService;

    /** Recomputes all badges for all users and logs how many users were processed. */
    @Override
    public void run(ApplicationArguments args) {
        int users = badgeService.recomputeForAllUsers();
        log.info("Recomputed badge progress for {} user(s) on startup", users);
    }
}
