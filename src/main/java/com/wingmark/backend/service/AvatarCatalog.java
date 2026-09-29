package com.wingmark.backend.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The fixed set of preset avatars a user can pick as their profile picture. Only the keys
 * live here - the images themselves ship inside the iOS app, keyed by the same strings.
 * Add keys at the end; never rename or remove one, since users' profiles store them.
 */
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
