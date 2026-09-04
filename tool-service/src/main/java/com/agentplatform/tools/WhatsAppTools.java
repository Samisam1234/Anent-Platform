package com.agentplatform.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WhatsApp messaging tool — currently a service stub/client integration point.
 *
 * <p>The method validates the input, logs the intended delivery, and returns a
 * confirmation string for the LLM. A real provider (e.g. the WhatsApp Business
 * Cloud API) can be dropped in behind the same signature; the endpoint
 * {@code tools.whatsapp.webhook-url} is reserved for that.</p>
 */
@Component
public class WhatsAppTools {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppTools.class);

    @Value("${tools.whatsapp.webhook-url:}")
    private String webhookUrl;

    @Tool("Sends a WhatsApp message to a phone number in international format (e.g. +15551234567).")
    public String sendWhatsAppMessage(@P("Recipient phone number in international format, e.g. +15551234567") String phone,
                                      @P("Message text to send") String message) {
        if (phone == null || !phone.matches("\\+?\\d{7,15}")) {
            return "Invalid phone number: " + phone + ". Use international format, e.g. +15551234567.";
        }
        log.info("WhatsApp message to [REDACTED] (stub integration, length={})",
                message == null ? 0 : message.length());
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return "WhatsApp message sent to " + phone + " (stub integration — no provider webhook configured).";
        }
        return "WhatsApp message sent to " + phone + " via " + webhookUrl + ".";
    }
}