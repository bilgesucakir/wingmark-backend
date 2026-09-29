package com.wingmark.backend.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class RandomTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RandomTokenGenerator() {
    }

    /** A uniformly random numeric code with the given number of digits, zero-padded (e.g. "048213"). */
    public static String numericCode(int digits) {
        int bound = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", SECURE_RANDOM.nextInt(bound));
    }

    public static String generate() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
