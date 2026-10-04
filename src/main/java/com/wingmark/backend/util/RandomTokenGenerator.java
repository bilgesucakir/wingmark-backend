package com.wingmark.backend.util;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates secure random tokens. */
public final class RandomTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RandomTokenGenerator() {
    }

    /** Returns a random numeric code of the given length, zero-padded, e.g. {@code 048213}. */
    public static String numericCode(int digits) {
        int bound = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", SECURE_RANDOM.nextInt(bound));
    }

    /** Returns a URL-safe random token with 384 bits of entropy. */
    public static String generate() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
