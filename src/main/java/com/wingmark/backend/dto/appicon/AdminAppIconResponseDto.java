package com.wingmark.backend.dto.appicon;

import java.time.Instant;

/** An alternate app icon as the admin sees it. */
public record AdminAppIconResponseDto(
        String iconId,
        String nameEn,
        String nameTr,
        Instant createdAt
) {
}
