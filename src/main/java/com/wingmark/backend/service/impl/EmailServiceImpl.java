package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.MailProperties;
import com.wingmark.backend.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Override
    public void sendVerificationEmail(String toEmail, String verifyUrl) {
        String html = """
                <p>Welcome to Wingmark!</p>
                <p>Confirm this is your email address to finish setting up your account:</p>
                <p><a href="%s">Verify my email</a></p>
                <p>Or paste this link into your browser:<br>%s</p>
                <p>This link expires in 24 hours. If you didn't create a Wingmark account, you can ignore this email.</p>
                """.formatted(verifyUrl, verifyUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(mailProperties.from());
            helper.setSubject("Verify your Wingmark email");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            // Don't let a mail-provider hiccup fail registration - the user can still
            // request a fresh link via POST /api/auth/resend-verification-email.
            log.error("Failed to send verification email to {}. Verification link: {}", toEmail, verifyUrl, e);
        }
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code, int validMinutes) {
        String html = """
                <p>Someone asked to reset the password for your Wingmark account.</p>
                <p>Enter this code in the app:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">%s</p>
                <p>It expires in %d minutes and can only be used once. If you didn't ask for this, you can ignore this email - your password hasn't changed.</p>
                """.formatted(code, validMinutes);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(mailProperties.from());
            helper.setSubject("Your Wingmark password reset code");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            // Same policy as verification: a mail failure doesn't fail the request (it
            // always answers 202 anyway). The code is logged so local dev without SMTP
            // can still exercise the flow.
            log.error("Failed to send password reset code to {}. Code: {}", toEmail, code, e);
        }
    }
}
