package com.wingmark.backend.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wingmark.backend.config.BusinessProperties;
import com.wingmark.backend.config.MailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(EmailServiceImpl.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void captureLogs() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("no SMTP")).when(mailSender).send(any(MimeMessage.class));
        originalLevel = logger.getLevel();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(logs);
        logger.setLevel(originalLevel);
    }

    private EmailServiceImpl service(BusinessProperties business) {
        return new EmailServiceImpl(mailSender, new MailProperties("no-reply@wingmark.app"), business);
    }

    private static final BusinessProperties UNCONFIGURED = new BusinessProperties("", "", "");

    @Test
    void secretsAreNeverLoggedAboveDebugAndAddressesAreMasked() {
        logger.setLevel(Level.INFO); // production default

        assertThatCode(() -> service(UNCONFIGURED).sendPasswordResetCode("yildirim@gmail.com", "482913", 15))
                .doesNotThrowAnyException();

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("482913"));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("yildirim@gmail.com"));
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("y***@gmail.com"));
    }

    @Test
    void atDebugTheUndeliveredSecretIsAvailableForLocalDevelopment() {
        logger.setLevel(Level.DEBUG);

        service(UNCONFIGURED).sendVerificationEmail("a@b.co", "http://localhost:8080/api/auth/verify-email?token=SECRET123");

        assertThat(logs.list).anyMatch(e -> e.getLevel() == Level.DEBUG && e.getFormattedMessage().contains("SECRET123"));
        assertThat(logs.list).filteredOn(e -> e.getLevel() != Level.DEBUG)
                .noneMatch(e -> e.getFormattedMessage().contains("SECRET123"));
    }

    @Test
    void footerIsEmptyUntilBusinessDetailsAreConfiguredAndEscapedOnceTheyAre() {
        assertThat(service(UNCONFIGURED).footer()).isEmpty();

        String footer = service(new BusinessProperties("Wingmark <Ltd>", "1 Bird St", "hi@wingmark.app")).footer();
        assertThat(footer).contains("Wingmark &lt;Ltd&gt;", "1 Bird St", "hi@wingmark.app");
    }

    @Test
    void contactLineNamesTheSupportAddressInsteadOfAskingForAReplyToANoReplySender() {
        assertThat(service(new BusinessProperties("Wingmark", "", "support.wingmark@gmail.com")).contactSupport())
                .isEqualTo("contact us at support.wingmark@gmail.com");
        assertThat(service(new BusinessProperties("", "", "")).contactSupport())
                .doesNotContain("reply").contains("support");
    }

    @Test
    void repliesGoToTheSupportAddressWhenConfigured() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service(new BusinessProperties("", "", "support.wingmark@gmail.com")).sendAccountDeletedEmail("a@b.com");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo("support.wingmark@gmail.com");

        MimeMessage plain = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(plain);
        service(UNCONFIGURED).sendAccountDeletedEmail("a@b.com");
        // No Reply-To header: javax falls back to From.
        assertThat(plain.getHeader("Reply-To")).isNull();
    }

    @Test
    void maskEmailKeepsOnlyTheFirstLetterAndDomain() {
        assertThat(EmailServiceImpl.maskEmail("yildirim@gmail.com")).isEqualTo("y***@gmail.com");
        assertThat(EmailServiceImpl.maskEmail("nope")).isEqualTo("***");
        assertThat(EmailServiceImpl.maskEmail(null)).isNull();
    }
}
