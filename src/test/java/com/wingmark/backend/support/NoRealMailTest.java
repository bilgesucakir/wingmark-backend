package com.wingmark.backend.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards against tests sending real email with SMTP settings from {@code .env} or the environment. */
@SpringBootTest
class NoRealMailTest {

    @Autowired
    private JavaMailSender mailSender;

    @Test
    void testContextsUseTheCapturingSenderInsteadOfSmtp() {
        assertThat(mailSender).isInstanceOf(TestMailConfig.CapturingMailSender.class);
    }
}
