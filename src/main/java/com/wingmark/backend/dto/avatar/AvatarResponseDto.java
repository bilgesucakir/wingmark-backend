package com.wingmark.backend.dto.avatar;

/** A preset avatar. The image ships inside the app, keyed by {@code key}. */
public record AvatarResponseDto(
        String key
) {
}
