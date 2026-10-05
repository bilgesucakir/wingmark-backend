package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Counts emails sent per UTC day and enforces the daily cap. In memory, so it restarts from zero with the
 * application; that is enough for a single instance. Warns once at 80% of the cap and when the cap is reached.
 */
@Slf4j
@Component
public class EmailSendCounter {

    private final MailProperties mailProperties;
    private final Clock clock;

    private LocalDate day;
    private int sent;
    private boolean warnedNearCap;

    @Autowired
    public EmailSendCounter(MailProperties mailProperties) {
        this(mailProperties, Clock.systemUTC());
    }

    EmailSendCounter(MailProperties mailProperties, Clock clock) {
        this.mailProperties = mailProperties;
        this.clock = clock;
    }

    /** Reserves one send for today. Returns false, without counting, when the daily cap is already used up. */
    public synchronized boolean tryReserve() {
        rollDay();
        int cap = mailProperties.dailyCap();
        if (cap > 0 && sent >= cap) {
            return false;
        }
        sent++;
        if (cap > 0 && !warnedNearCap && sent >= Math.ceil(cap * 0.8)) {
            warnedNearCap = true;
            log.warn("Email volume is at {} of the daily cap of {}", sent, cap);
        }
        return true;
    }

    /** Gives back a reservation for a send that failed, so failures do not use up the cap. */
    public synchronized void release() {
        rollDay();
        if (sent > 0) {
            sent--;
        }
    }

    /** Returns how many emails have been sent today (UTC). */
    public synchronized int sentToday() {
        rollDay();
        return sent;
    }

    private void rollDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            sent = 0;
            warnedNearCap = false;
        }
    }
}
