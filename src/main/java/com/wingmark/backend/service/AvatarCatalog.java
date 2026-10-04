package com.wingmark.backend.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/** Preset avatar keys for profile pictures; the images ship in the iOS app. Add keys at the end and never rename or remove one. */
@Component
public class AvatarCatalog {

    private static final List<String> KEYS = IntStream.rangeClosed(1, 12)
            .mapToObj(i -> "avatar-" + i)
            .toList();

    public List<String> keys() {
        return KEYS;
    }

    public boolean contains(String key) {
        return KEYS.contains(key);
    }
}
