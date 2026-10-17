package com.wingmark.backend.dto.appicon;

/** An available alternate app icon: the id of the icon built into the app, and its display names. */
public record AppIconResponseDto(
        String iconId,
        String nameEn,
        String nameTr
) {
}
