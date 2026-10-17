package com.wingmark.backend.dto.appicon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to change an app icon's display names. The iconId cannot change: it names the icon built into the app. */
public record UpdateAppIconRequestDto(
        @NotBlank @Size(max = 60)
        String nameEn,

        @NotBlank @Size(max = 60)
        String nameTr
) {
}
