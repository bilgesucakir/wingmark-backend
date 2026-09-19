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
}
