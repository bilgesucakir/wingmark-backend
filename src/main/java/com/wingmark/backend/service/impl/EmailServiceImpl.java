package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.BusinessProperties;
import com.wingmark.backend.config.MailProperties;
import com.wingmark.backend.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/** Sends transactional emails only (no marketing). A failed send never fails the request; links and codes are logged at DEBUG only and addresses are masked. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final BusinessProperties businessProperties;
    private final EmailSendCounter sendCounter;

    @Override
    public void sendVerificationEmail(String toEmail, String verifyUrl) {
        String html = """
                <p>Welcome to Wingmark!</p>
                <p>Confirm this is your email address to finish setting up your account:</p>
                <p><a href="%s">Verify my email</a></p>
                <p>Or paste this link into your browser:<br>%s</p>
                <p>This link expires in 24 hours. If you didn't create a Wingmark account, you can ignore this email.</p>
                """.formatted(verifyUrl, verifyUrl);
        // A user whose email failed can request a fresh link via POST /api/auth/resend-verification-email.
        send(toEmail, "verification", "Verify your Wingmark email", html, "verification link", verifyUrl);
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code, int validMinutes) {
        String html = """
                <p>Someone asked to reset the password for your Wingmark account.</p>
                <p>Enter this code in the app:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">%s</p>
                <p>It expires in %d minutes and can only be used once. If you didn't ask for this, you can ignore this email - your password hasn't changed.</p>
                """.formatted(code, validMinutes);
        send(toEmail, "password-reset", "Your Wingmark password reset code", html, "reset code", code);
    }

    @Override
    public void sendPasswordChangedEmail(String toEmail) {
        String html = """
                <p>The password for your Wingmark account was just changed, and every device was signed out.</p>
                <p>If this was you, there's nothing else to do.</p>
                <p>If it wasn't, reset your password right away from the Wingmark app (Forgot password) and %s so we can help.</p>
                """.formatted(contactSupport());
        send(toEmail, "password-changed", "Your Wingmark password was changed", html, null, null);
    }

    @Override
    public void sendAccountDeletedEmail(String toEmail) {
        String html = """
                <p>Your Wingmark account has been deleted.</p>
                <p>Your profile, bird logs, badge progress, settings, sign-in sessions and uploaded photos have been permanently removed from our database. This can't be undone.</p>
                <p>If you didn't request this, please %s.</p>
                """.formatted(contactSupport());
        send(toEmail, "account-deleted", "Your Wingmark account has been deleted", html, null, null);
    }

    /** Sends one email unless the daily cap is used up, and logs the outcome (sent, failed or skipped) with a masked recipient. */
    private void send(String toEmail, String type, String subject, String bodyHtml, String secretLabel, String secret) {
        if (!sendCounter.tryReserve()) {
            log.warn("Email skipped, daily cap of {} reached: type={} to={}", mailProperties.dailyCap(), type, maskEmail(toEmail));
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(mailProperties.from());
            if (StringUtils.hasText(businessProperties.contactEmail())) {
                // The sender is a no-reply address; replies should reach support.
                helper.setReplyTo(businessProperties.contactEmail());
            }
            helper.setSubject(subject);
            helper.setText(bodyHtml + footer(), true);
            mailSender.send(message);
            log.info("Email sent: type={} to={} (sent today: {})", type, maskEmail(toEmail), sendCounter.sentToday());
        } catch (Exception e) {
            sendCounter.release();
            log.error("Email failed: type={} to={}", type, maskEmail(toEmail), e);
            if (secret != null) {
                // DEBUG only (off in production): lets local development proceed without a mail server.
                log.debug("Undelivered {} for {}: {}", secretLabel, maskEmail(toEmail), secret);
            }
        }
    }

    /** Returns the "contact us" wording: the support address if configured, else a pointer to the app. */
    String contactSupport() {
        String email = businessProperties.contactEmail();
        return StringUtils.hasText(email)
                ? "contact us at " + HtmlUtils.htmlEscape(email)
                : "contact support through the Wingmark app";
    }

    /** Returns the legal footer HTML, or an empty string when no legal name is configured. */
    String footer() {
        if (!businessProperties.isConfigured()) {
            return "";
        }
        StringBuilder footer = new StringBuilder("<hr><p style=\"color:#595959;font-size:12px\">")
                .append(HtmlUtils.htmlEscape(businessProperties.legalName()));
        if (StringUtils.hasText(businessProperties.address())) {
            footer.append("<br>").append(HtmlUtils.htmlEscape(businessProperties.address()));
        }
        if (StringUtils.hasText(businessProperties.contactEmail())) {
            footer.append("<br>").append(HtmlUtils.htmlEscape(businessProperties.contactEmail()));
        }
        return footer.append("</p>").toString();
    }

    /** Masks an address for logs, e.g. {@code y***@gmail.com}. */
    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
