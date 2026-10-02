package com.wingmark.backend.dto.export;

import com.wingmark.backend.dto.badge.UserBadgeResponseDto;
import com.wingmark.backend.dto.birdlog.BirdLogResponseDto;
import com.wingmark.backend.dto.legal.ConsentResponseDto;
import com.wingmark.backend.dto.user.SettingsResponseDto;
import com.wingmark.backend.dto.user.UserProfileResponseDto;

import java.time.Instant;
import java.util.List;

/** Everything Wingmark holds about one user, for the right of access / data portability. */
public record UserDataExportDto(
        Instant exportedAt,
        UserProfileResponseDto profile,
        SettingsResponseDto settings,
        List<BirdLogResponseDto> birdLogs,
        List<UserBadgeResponseDto> badges,
        List<ConsentResponseDto> consents
) {
}
