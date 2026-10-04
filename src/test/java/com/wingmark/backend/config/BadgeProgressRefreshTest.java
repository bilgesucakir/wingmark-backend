package com.wingmark.backend.config;

import com.wingmark.backend.service.BadgeService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgeProgressRefreshTest {

    @Test
    void startupRecomputesBadgesForAllUsers() {
        BadgeService badgeService = mock(BadgeService.class);
        when(badgeService.recomputeForAllUsers()).thenReturn(7);

        new BadgeProgressRefresh(badgeService).run(null);

        verify(badgeService).recomputeForAllUsers();
    }
}
