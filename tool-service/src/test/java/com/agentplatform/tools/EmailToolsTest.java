package com.agentplatform.tools;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailToolsTest {

    private JavaMailSender mailSender;
    private EmailTools emailTools;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        // Real session-backed MimeMessage so MimeMessageHelper can build it.
        when(mailSender.createMimeMessage())
                .thenReturn(new JavaMailSenderImpl().createMimeMessage());
        emailTools = new EmailTools(mailSender);
    }

    @Test
    @DisplayName("sendEmail builds and sends a mail through JavaMailSender")
    void sendEmail_sendsMessage() {
        String result = emailTools.sendEmail("target@example.com", "Hello", "Body text");

        assertThat(result).contains("Email sent to target@example.com");
        assertThat(result).contains("Hello");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMTP failure is returned as an informative string instead of throwing")
    void sendEmail_returnsFailureString() {
        doThrow(new MailSendException("smtp connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        String result = emailTools.sendEmail("target@example.com", "Hi", "b");

        assertThat(result).startsWith("Failed to send email to target@example.com");
        assertThat(result).contains("smtp connection refused");
    }

    @Test
    @DisplayName("missing JavaMailSender yields a clear message (defensive null handling)")
    void sendEmail_unconfigured() {
        EmailTools unconfigured = new EmailTools(null);

        String result = unconfigured.sendEmail("a@b.com", "s", "b");

        assertThat(result).contains("not configured");
    }

    @Test
    @DisplayName("nullable constructor never sends when the tool is used with no sender")
    void sendEmail_noSender_neverSends() {
        EmailTools unconfigured = new EmailTools(null);
        unconfigured.sendEmail("a@b.com", "s", "b");
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}