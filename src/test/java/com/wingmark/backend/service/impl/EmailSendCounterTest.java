package com.wingmark.backend.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wingmark.backend.config.MailProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSendCounterTest {

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T10:00:00Z");

        void advanceDays(long days) {
            now = now.plusSeconds(days * 86400);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final Logger logger = (Logger) LoggerFactory.getLogger(EmailSendCounter.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final MovableClock clock = new MovableClock();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(logs);
    }

    private EmailSendCounter counter(int cap) {
        return new EmailSendCounter(new MailProperties("no-reply@wingmarkapp.com", cap), clock);
    }

    @Test
    void sendsAreAllowedUpToTheCapAndRefusedAfterwards() {
        EmailSendCounter counter = counter(3);

        assertThat(counter.tryReserve()).isTrue();
        assertThat(counter.tryReserve()).isTrue();
        assertThat(counter.tryReserve()).isTrue();
        assertThat(counter.tryReserve()).isFalse();
        assertThat(counter.sentToday()).isEqualTo(3);
    }

    @Test
    void aCapOfZeroMeansNoLimit() {
        EmailSendCounter counter = counter(0);

        for (int i = 0; i < 1000; i++) {
            assertThat(counter.tryReserve()).isTrue();
        }
    }

    @Test
    void releasingAReservationFreesTheSlot() {
        EmailSendCounter counter = counter(1);
        counter.tryReserve();

        counter.release();

        assertThat(counter.sentToday()).isZero();
        assertThat(counter.tryReserve()).isTrue();
    }

    @Test
    void theCountStartsOverOnTheNextUtcDay() {
        EmailSendCounter counter = counter(1);
        counter.tryReserve();
        assertThat(counter.tryReserve()).isFalse();

        clock.advanceDays(1);

        assertThat(counter.sentToday()).isZero();
        assertThat(counter.tryReserve()).isTrue();
    }

    @Test
    void itWarnsOnceWhenVolumeReachesEightyPercentOfTheCap() {
        EmailSendCounter counter = counter(10);

        for (int i = 0; i < 10; i++) {
            counter.tryReserve();
        }

        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage()).contains("8 of the daily cap of 10"));
    }
}
