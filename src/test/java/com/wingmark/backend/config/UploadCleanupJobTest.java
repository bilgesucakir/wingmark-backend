package com.wingmark.backend.config;

import com.wingmark.backend.service.OrphanedUploadService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UploadCleanupJobTest {

    @Test
    void theScheduledRunCallsTheCleanupOnce() {
        OrphanedUploadService service = mock(OrphanedUploadService.class);

        new UploadCleanupJob(service).run();

        verify(service).cleanup(any(Instant.class));
    }

    @Test
    void theCleanupIsOffAndInDryRunByDefaultWithAWeekOfGrace() {
        UploadCleanupProperties defaults = new UploadCleanupProperties(false, true, 7, 100, "0 45 4 * * *");

        assertThat(defaults.enabled()).isFalse();
        assertThat(defaults.dryRun()).isTrue();
        assertThat(defaults.graceDays()).isEqualTo(7);
    }
}
