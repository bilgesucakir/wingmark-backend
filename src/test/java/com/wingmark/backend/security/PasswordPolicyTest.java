package com.wingmark.backend.security;

import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordPolicyTest {

    private final PwnedPasswordChecker pwned = mock(PwnedPasswordChecker.class);
    private final PasswordPolicy policy = new PasswordPolicy(pwned);

    private void assertRejected(String password, ErrorCode code) {
        assertThatThrownBy(() -> policy.validate(password, "amelia.rivera@example.com", "ameliabirds"))
                .as(password)
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    @Test
    void rejectsPasswordsBuiltFromTheEmailOrUsername() {
        assertRejected("amelia.rivera2026", ErrorCode.WEAK_PASSWORD);
        assertRejected("AmeliaBirds99!", ErrorCode.WEAK_PASSWORD);
    }

    @Test
    void rejectsWellKnownPasswordsCaseInsensitively() {
        assertRejected("Password123", ErrorCode.WEAK_PASSWORD);
        assertRejected("qwerty1234", ErrorCode.WEAK_PASSWORD);
    }

    @Test
    void rejectsBreachedPasswords() {
        when(pwned.isBreached("kestrel-hover-77")).thenReturn(true);
        assertRejected("kestrel-hover-77", ErrorCode.PASSWORD_BREACHED);
    }

    @Test
    void acceptsAStrongUnrelatedPassword() {
        assertThatCode(() -> policy.validate("kestrel-hover-77", "amelia.rivera@example.com", "ameliabirds"))
                .doesNotThrowAnyException();
    }

    @Test
    void shortEmailOrUsernameFragmentsDontCauseFalsePositives() {
        // A 3-letter username shouldn't ban every password containing those letters.
        assertThatCode(() -> policy.validate("kestrel-bob-77x", "bob@example.com", "bob")).doesNotThrowAnyException();
    }
}
