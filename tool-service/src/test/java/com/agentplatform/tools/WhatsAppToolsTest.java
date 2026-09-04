package com.agentplatform.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppToolsTest {

    private final WhatsAppTools whatsAppTools = new WhatsAppTools();

    @Test
    @DisplayName("valid international phone yields a stub confirmation")
    void sendWhatsAppMessage_validPhone() {
        String result = whatsAppTools.sendWhatsAppMessage("+15551234567", "Hello from the agent");

        assertThat(result).contains("WhatsApp message sent to +15551234567");
        assertThat(result).contains("stub integration");
    }

    @Test
    @DisplayName("invalid phone number is rejected with feedback the LLM can relay")
    void sendWhatsAppMessage_invalidPhone() {
        String result = whatsAppTools.sendWhatsAppMessage("not-a-number", "hi");

        assertThat(result).contains("Invalid phone number");
        assertThat(result).contains("international format");
    }
}