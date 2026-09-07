package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.service.AgentChatException;
import com.agentplatform.orchestrator.service.AgentChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link AgentChatController}.
 *
 * <p>Uses {@code @WebMvcTest} — only the web layer is loaded (no Spring context,
 * no Ollama connection required). {@link AgentChatService} is mocked.</p>
 */
@WebMvcTest(controllers = {AgentChatController.class, GlobalExceptionHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class AgentChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentChatService agentChatService;

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/agent/chat — returns 200 with AI response")
    void chat_validQuery_returns200() throws Exception {
        when(agentChatService.chat("Hello!")).thenReturn("Hi there! How can I help?");

        String requestBody = objectMapper.writeValueAsString(
                new com.agentplatform.ui.dto.ChatRequest("Hello!")
        );

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response").value("Hi there! How can I help?"));
    }

    // ─── Error: blank query ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/agent/chat — returns 400 when query is blank")
    void chat_blankQuery_returns400() throws Exception {
        when(agentChatService.chat(""))
                .thenThrow(new IllegalArgumentException("User message must not be blank"));

        String requestBody = objectMapper.writeValueAsString(
                new com.agentplatform.ui.dto.ChatRequest("")
        );

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    // ─── Ollama unavailable ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/agent/chat — returns 503 when Ollama is unreachable")
    void chat_ollamaUnavailable_returns503() throws Exception {
        when(agentChatService.chat(anyString()))
                .thenThrow(new AgentChatException(
                        "Failed to communicate with the AI model. Please ensure Ollama is running."));

        String requestBody = objectMapper.writeValueAsString(
                new com.agentplatform.ui.dto.ChatRequest("Hello!")
        );

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable());
    }

    // ─── E1. Raw query is never logged ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/agent/chat — does not log the raw user query")
    void chat_doesNotLogRawQuery(CapturedOutput output) throws Exception {
        String secret = "super-secret-query-" + UUID.randomUUID();
        when(agentChatService.chat(secret)).thenReturn("ok");

        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.agentplatform.ui.dto.ChatRequest(secret))))
                .andExpect(status().isOk());

        assertFalse(output.getAll().contains(secret),
                "the raw user query must never appear in application logs");
        assertTrue(output.getAll().contains("queryChars"),
                "safe query length metadata should be present");
    }
}
