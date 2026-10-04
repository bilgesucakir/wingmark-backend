package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.export.UserDataExportDto;
import com.wingmark.backend.service.BadgeService;
import com.wingmark.backend.service.BirdLogService;
import com.wingmark.backend.service.ConsentService;
import com.wingmark.backend.service.DataExportService;
import com.wingmark.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Builds the export from the same services the app uses; photos appear as their {@code /uploads/...} URLs. */
@Service
@RequiredArgsConstructor
public class DataExportServiceImpl implements DataExportService {

    private final UserService userService;
    private final BirdLogService birdLogService;
    private final BadgeService badgeService;
    private final ConsentService consentService;

    @Override
    public UserDataExportDto export(UUID userId, Locale locale) {
        return new UserDataExportDto(
                Instant.now(),
                userService.getProfile(userId, locale),
                userService.getSettings(userId),
                birdLogService.getByUserId(userId, null, null, null, Sort.Direction.ASC, locale),
                badgeService.getByUserId(userId, locale),
                consentService.history(userId));
    }
}
