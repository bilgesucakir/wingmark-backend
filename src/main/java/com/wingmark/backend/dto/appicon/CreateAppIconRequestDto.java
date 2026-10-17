package com.wingmark.backend.dto.appicon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request to make an alternate app icon available. */
public record CreateAppIconRequestDto(
        @NotBlank
        @Pattern(regexp = "[a-z0-9][a-z0-9_-]{0,39}", message = "iconId must be 1-40 characters: lowercase letters, digits, - or _, starting with a letter or digit")
        String iconId,

        @NotBlank @Size(max = 60)
        String nameEn,

        @NotBlank @Size(max = 60)
        String nameTr
) {
}
