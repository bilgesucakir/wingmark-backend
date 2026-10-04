package com.wingmark.backend.support;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replaces the real mail sender in every Spring test context, so tests never reach an SMTP server even when
 * production SMTP settings are present in {@code .env} or the environment.
 */
@Configuration
public class TestMailConfig {

    /** Mail "sent" by a test context; nothing leaves the JVM. */
    public static final class CapturingMailSender extends JavaMailSenderImpl {

        private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();

        /** Returns the messages that would have been sent. */
        public List<MimeMessage> sent() {
            return sent;
        }

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            sent.addAll(List.of(mimeMessages));
        }
    }

    /** Capturing sender that takes precedence over the auto-configured SMTP one. */
    @Bean
    @Primary
    public JavaMailSender testMailSender() {
        return new CapturingMailSender();
    }
}
