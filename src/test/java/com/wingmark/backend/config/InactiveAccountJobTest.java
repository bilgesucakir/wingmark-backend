package com.wingmark.backend.config;

import com.wingmark.backend.service.InactiveAccountService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InactiveAccountJobTest {

    @Test
    void theScheduledRunCallsTheCleanupOnce() {
        InactiveAccountService service = mock(InactiveAccountService.class);

        new InactiveAccountJob(service).run();

        verify(service).runCleanup(any(Instant.class));
    }

    @Test
    void theCleanupIsOffAndInDryRunByDefault() {
        InactivityProperties defaults = new InactivityProperties(false, true, 730, 7, 50, 20, "0 30 3 * * *");

        assertThat(defaults.enabled()).isFalse();
        assertThat(defaults.dryRun()).isTrue();
        assertThat(defaults.inactiveAfterDays()).isEqualTo(730);
        assertThat(defaults.warnDaysBeforeDelete()).isEqualTo(7);
    }
}
