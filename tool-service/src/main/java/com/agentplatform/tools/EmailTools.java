package com.agentplatform.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email sending tool backed by Spring's {@link JavaMailSender} (auto-configured
 * by {@code spring-boot-starter-mail}). The sender address is configurable via
 * {@code tools.email.from}; without any {@code spring.mail.*} properties the app
 * still boots — sending simply targets the SMTP defaults until configured.
 */
@Component
public class EmailTools {

    public static final String DEFAULT_FROM = "no-reply@agentplatform.local";

    private static final Logger log = LoggerFactory.getLogger(EmailTools.class);

    private final JavaMailSender mailSender;

    private String from = DEFAULT_FROM;

    public EmailTools(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${tools.email.from:" + DEFAULT_FROM + "}")
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * Sends a plain-text email. Failures are returned as a message string so the
     * LLM can relay what happened instead of failing the whole turn.
     */
    @Tool("Sends an email to a recipient with a subject and a plain-text body.")
    public String sendEmail(@P("Recipient email address") String recipient,
                            @P("Email subject line") String subject,
                            @P("Email body (plain text)") String body) {
        if (mailSender == null) {
            return "Email tool is not configured (no JavaMailSender available).";
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(message);
            log.info("Email sent successfully (subject length={})", subject == null ? 0 : subject.length());
            return "Email sent to " + recipient + " (subject: \"" + subject + "\").";
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
            return "Failed to send email to " + recipient + ": " + e.getMessage();
        }
    }
}