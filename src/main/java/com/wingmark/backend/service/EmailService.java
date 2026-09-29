package com.wingmark.backend.service;

/** Sends transactional emails: account verification and password-reset codes. */
public interface EmailService {

    /** Sends the "verify your email" message containing a link back to verifyUrl. */
    void sendVerificationEmail(String toEmail, String verifyUrl);

    /** Sends the 6-digit password-reset code the user types into the app. */
    void sendPasswordResetCode(String toEmail, String code, int validMinutes);
}
