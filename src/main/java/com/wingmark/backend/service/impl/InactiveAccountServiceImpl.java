package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.InactivityProperties;
import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.DeletionInitiator;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.UserRepository;
import com.wingmark.backend.service.AccountDeletionService;
import com.wingmark.backend.service.EmailService;
import com.wingmark.backend.service.InactiveAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Warns and deletes inactive accounts. Logs user ids only, never email addresses. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InactiveAccountServiceImpl implements InactiveAccountService {

    private final UserRepository userRepository;
    private final BirdLogRepository birdLogRepository;
    private final AccountDeletionService accountDeletionService;
    private final EmailService emailService;
    private final InactivityProperties properties;

    @Override
    public Result runCleanup(Instant now) {
        Duration inactiveAfter = Duration.ofDays(properties.inactiveAfterDays());
        Duration warnBefore = Duration.ofDays(properties.warnDaysBeforeDelete());
        int warned = 0;
        int deleted = 0;

        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getRole() == Role.ADMIN) {
                continue;
            }
            Instant lastActivity = lastActivity(user, now, inactiveAfter.minus(warnBefore));
            if (lastActivity == null) {
                continue;
            }
            Instant deleteAt = lastActivity.plus(inactiveAfter);
            Instant warnAt = deleteAt.minus(warnBefore);
            Instant warnedAt = user.getInactivityWarningSentAt();
            boolean warnedForThisPeriod = warnedAt != null && !warnedAt.isBefore(lastActivity);

            if (!warnedForThisPeriod) {
                if (!now.isBefore(warnAt) && warned < properties.maxWarningsPerRun()) {
                    warned++;
                    warn(user, now, deleteAt, warnBefore);
                }
            } else if (!now.isBefore(deleteAt) && !now.isBefore(warnedAt.plus(warnBefore))
                    && deleted < properties.maxDeletionsPerRun()) {
                deleted++;
                delete(user);
            }
        }
        log.info("Inactive account cleanup {}: {} warned, {} deleted", properties.dryRun() ? "(dry run)" : "done", warned, deleted);
        return new Result(warned, deleted, properties.dryRun());
    }

    /**
     * Returns the latest activity of the user (login or refresh, creation, or a bird log change), or null if the
     * account has no usable timestamp. Bird logs are only looked up when the login-based activity is already old.
     */
    private Instant lastActivity(User user, Instant now, Duration warnAfter) {
        Instant base = Stream.of(user.getLastLoginAt(), user.getCreatedAt())
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        if (base == null) {
            log.warn("User {} has no activity or creation timestamp; skipped by the inactive account cleanup", user.getId());
            return null;
        }
        if (base.plus(warnAfter).isAfter(now)) {
            return base;
        }
        Instant lastLog = birdLogRepository.findFirstByUserIdOrderByUpdatedAtDesc(user.getId())
                .map(InactiveAccountServiceImpl::logActivity).orElse(null);
        return lastLog != null && lastLog.isAfter(base) ? lastLog : base;
    }

    private static Instant logActivity(BirdLog birdLog) {
        return birdLog.getUpdatedAt() != null ? birdLog.getUpdatedAt() : birdLog.getCreatedAt();
    }

    private void warn(User user, Instant now, Instant deleteAt, Duration warnBefore) {
        Instant effectiveDeletion = deleteAt.isAfter(now.plus(warnBefore)) ? deleteAt : now.plus(warnBefore);
        if (properties.dryRun()) {
            log.info("Dry run: would warn user {} about deletion on {}", user.getId(), effectiveDeletion);
            return;
        }
        emailService.sendInactivityWarningEmail(user.getEmail(), effectiveDeletion.atZone(ZoneOffset.UTC).toLocalDate());
        user.setInactivityWarningSentAt(now);
        userRepository.save(user);
        log.info("Warned user {} about deletion on {}", user.getId(), effectiveDeletion);
    }

    private void delete(User user) {
        if (properties.dryRun()) {
            log.info("Dry run: would delete inactive user {}", user.getId());
            return;
        }
        try {
            accountDeletionService.deleteAccount(user.getId(), DeletionInitiator.INACTIVITY);
        } catch (RuntimeException e) {
            log.error("Could not delete inactive user {}", user.getId(), e);
        }
    }
}
