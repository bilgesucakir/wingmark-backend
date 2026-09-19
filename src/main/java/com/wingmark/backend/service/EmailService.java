package com.wingmark.backend.service;

/** Sends transactional emails (currently just account-verification). */
public interface EmailService {

    /** Sends the "verify your email" message containing a link back to verifyUrl. */
    void sendVerificationEmail(String toEmail, String verifyUrl);
}
