package com.wingmark.backend.security;

import com.wingmark.backend.config.BcryptLimitProperties;
import com.wingmark.backend.security.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BcryptGateTest {

    /** Starts a check that holds its slot until the returned latch is released. */
    private static CountDownLatch holdASlot(BcryptGate gate) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread thread = new Thread(() -> gate.run(() -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }));
        thread.setDaemon(true);
        thread.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        return release;
    }

    @Test
    void aCheckOverTheCapIsTurnedAwayWith429AndTheConfiguredRetryAfter() throws Exception {
        BcryptGate gate = new BcryptGate(new BcryptLimitProperties(1, 20, 7));
        CountDownLatch release = holdASlot(gate);

        assertThatThrownBy(() -> gate.run(() -> true))
                .isInstanceOfSatisfying(RateLimitExceededException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(7));

        release.countDown();
    }

    @Test
    void aCheckWaitsForASlotThatFreesUpInTime() throws Exception {
        BcryptGate gate = new BcryptGate(new BcryptLimitProperties(1, 3000, 2));
        CountDownLatch release = holdASlot(gate);
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            release.countDown();
        }).start();

        assertThat(gate.run(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void theSlotIsGivenBackWhenACheckFails() {
        BcryptGate gate = new BcryptGate(new BcryptLimitProperties(1, 20, 2));

        assertThatThrownBy(() -> gate.run(() -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(gate.run(() -> "free again")).isEqualTo("free again");
    }

    @Test
    void zeroTurnsTheCapOff() throws Exception {
        BcryptGate gate = new BcryptGate(new BcryptLimitProperties(0, 0, 2));
        holdASlot(gate);
        holdASlot(gate);

        assertThat(gate.run(() -> "unlimited")).isEqualTo("unlimited");
    }
}
