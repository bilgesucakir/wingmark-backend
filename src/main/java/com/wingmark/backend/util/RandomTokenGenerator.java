package com.wingmark.backend.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class RandomTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RandomTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
