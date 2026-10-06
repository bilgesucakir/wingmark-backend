package com.wingmark.backend.config;

import com.wingmark.backend.service.UnverifiedAccountService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UnverifiedAccountJobTest {

    @Test
    void theScheduledRunCallsThePurgeOnce() {
        UnverifiedAccountService service = mock(UnverifiedAccountService.class);

        new UnverifiedAccountJob(service).run();

        verify(service).purge(any(Instant.class));
    }
}
