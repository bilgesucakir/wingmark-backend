package com.wingmark.backend.service;

/** Sends transactional emails: account verification, password-reset codes and account-deletion confirmation. */
public interface EmailService {

    /** Sends the "verify your email" message containing a link back to verifyUrl. */
    void sendVerificationEmail(String toEmail, String verifyUrl);

    /** Sends the 6-digit password-reset code the user types into the app. */
    void sendPasswordResetCode(String toEmail, String code, int validMinutes);

    /** Security notice after a password change or reset, so an unexpected change gets noticed. */
    void sendPasswordChangedEmail(String toEmail);

    /** Confirms to the (former) account holder that their account and data were deleted. */
    void sendAccountDeletedEmail(String toEmail);

    /** Warns an inactive account's owner that it will be deleted on the given date unless they sign in. */
    void sendInactivityWarningEmail(String toEmail, java.time.LocalDate deletionDate);
}
