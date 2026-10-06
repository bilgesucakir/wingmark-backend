package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.UnverifiedAccountProperties;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.DeletionInitiator;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.service.AccountDeletionService;
import com.wingmark.backend.service.UnverifiedAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Removes abandoned signups. Logs user ids only, never email addresses. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedAccountServiceImpl implements UnverifiedAccountService {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;
    private final UnverifiedAccountProperties properties;

    @Override
    public Result purge(Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(properties.olderThanDays()));
        int deleted = 0;
        for (User user : userRepository.findByEmailVerifiedFalseAndVerificationEmailSentAtBefore(cutoff)) {
            if (user.getRole() == Role.ADMIN || user.getLastLoginAt() != null) {
                continue;
            }
            if (deleted >= properties.maxPerRun()) {
                break;
            }
            deleted++;
            if (properties.dryRun()) {
                log.info("Dry run: would delete unverified user {}", user.getId());
                continue;
            }
            try {
                accountDeletionService.deleteAccount(user.getId(), DeletionInitiator.UNVERIFIED);
            } catch (RuntimeException e) {
                deleted--;
                log.error("Could not delete unverified user {}", user.getId(), e);
            }
        }
        log.info("Unverified account cleanup {}: {} deleted", properties.dryRun() ? "(dry run)" : "done", deleted);
        return new Result(deleted, properties.dryRun());
    }
}
