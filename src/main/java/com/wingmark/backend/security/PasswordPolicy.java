package com.wingmark.backend.security;

import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Server-side password rules applied at signup, reset and change: not derived from the email or username, not a
 * common password, and not in a known breach.
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    /** Well-known passwords that satisfy the length/letter/digit rules (HIBP catches the rest). */
    private static final Set<String> COMMON = Set.of(
            "password12", "password123", "password1234", "passw0rd123", "p4ssword123", "qwerty1234",
            "qwerty12345", "qwertyuiop1", "1q2w3e4r5t", "1qaz2wsx3edc", "zaq12wsxcde", "abc1234567",
            "abcd123456", "a1b2c3d4e5", "iloveyou12", "iloveyou123", "welcome123", "welcome1234",
            "letmein123", "monkey1234", "dragon1234", "football12", "football123", "baseball12",
            "sunshine12", "princess12", "admin12345", "administrator1", "trustno1234", "superman12",
            "batman1234", "starwars12", "master1234", "shadow1234", "michael123", "charlie123",
            "123456789a", "a123456789", "1234567890a", "q1w2e3r4t5", "asdfghjkl1", "zxcvbnm123",
            "changeme123", "secret1234", "test123456", "testtest12", "wingmark123", "wingmark1",
            "birdwatch1", "birdwatcher1");

    private final PwnedPasswordChecker pwnedPasswordChecker;

/** Rejects a password that contains the email or username, is a common password or appears in a breach. */
    public void validate(String password, String email, String username) {
        String lower = password.toLowerCase(Locale.ROOT);
        String emailLocal = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@")[0];
        String user = username == null ? "" : username.toLowerCase(Locale.ROOT);
        if ((emailLocal.length() >= 4 && lower.contains(emailLocal)) || (user.length() >= 4 && lower.contains(user))) {
            throw new BadRequestException(ErrorCode.WEAK_PASSWORD, "Password must not contain your email address or username");
        }
        if (COMMON.contains(lower)) {
            throw new BadRequestException(ErrorCode.WEAK_PASSWORD, "This password is too common - choose a less predictable one");
        }
        if (pwnedPasswordChecker.isBreached(password)) {
            throw new BadRequestException(ErrorCode.PASSWORD_BREACHED,
                    "This password has appeared in a data breach - choose a different one");
        }
    }
}
