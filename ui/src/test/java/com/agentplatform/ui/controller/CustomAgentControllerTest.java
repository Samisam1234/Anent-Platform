package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.service.AgentChatException;
import com.agentplatform.orchestrator.service.AgentChatService;
import com.agentplatform.ui.dto.AgentRequest;
import com.agentplatform.ui.model.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link CustomAgentController}.
 *
 * <p>Uses {@code @WebMvcTest} — only the web layer is loaded (no Spring context,
 * no LLM connection required). {@link AgentChatService} is mocked.</p>
 */
@WebMvcTest(controllers = {CustomAgentController.class, GlobalExceptionHandler.class})
class CustomAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentChatService agentChatService;

    // ─── Status ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/custom/status — returns 200 with status message")
    void status_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/custom/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("OK"));
    }

    // ─── Structured processing (happy path) ─────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/custom/process — SUMMARIZE returns parsed structured result + execution time")
    void process_summarize_returnsStructuredResult() throws Exception {
        when(agentChatService.chat(anyString(), eq("A long text to summarize"), isNull()))
                .thenReturn("{\"summary\":\"Concise summary\"}");

        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("A long text to summarize", TaskType.SUMMARIZE, null)
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.prompt").value("A long text to summarize"))
                .andExpect(jsonPath("$.taskType").value("SUMMARIZE"))
                .andExpect(jsonPath("$.result.summary").value("Concise summary"))
                .andExpect(jsonPath("$.executionTimeMs").isNumber());

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentChatService).chat(systemPromptCaptor.capture(), eq("A long text to summarize"), isNull());
        assertThat(systemPromptCaptor.getValue())
                .contains("summarization")
                .contains("JSON object");
    }

    @Test
    @DisplayName("POST /api/v1/custom/process — taskType omitted defaults to GENERAL")
    void process_defaultsToGeneral() throws Exception {
        when(agentChatService.chat(anyString(), eq("Hello!"), isNull()))
                .thenReturn("{\"summary\":\"Hi there!\"}");

        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("Hello!", null, null)
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("GENERAL"))
                .andExpect(jsonPath("$.result.summary").value("Hi there!"));
    }

    @Test
    @DisplayName("POST /api/v1/custom/process — EXTRACT_KEY_POINTS parses keyPoints list")
    void process_extractKeyPoints_parsesList() throws Exception {
        when(agentChatService.chat(anyString(), anyString(), isNull()))
                .thenReturn("{\"keyPoints\":[\"First point\",\"Second point\"]}");

        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("Any text", TaskType.EXTRACT_KEY_POINTS, null)
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.keyPoints.length()").value(2))
                .andExpect(jsonPath("$.result.keyPoints[0]").value("First point"));
    }

    @Test
    @DisplayName("POST /api/v1/custom/process — CLASSIFY parses category + confidence")
    void process_classify_parsesCategoryAndConfidence() throws Exception {
        when(agentChatService.chat(anyString(), anyString(), isNull()))
                .thenReturn("{\"category\":\"AI\",\"confidence\":0.9}");

        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("Promote our new ML platform", TaskType.CLASSIFY, null)
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.category").value("AI"))
                .andExpect(jsonPath("$.result.confidence").value(0.9));
    }

    @Test
    @DisplayName("POST /api/v1/custom/process — strips ```json fences from model output")
    void process_stripsJsonFences() throws Exception {
        when(agentChatService.chat(anyString(), anyString(), isNull()))
                .thenReturn("```json\n{\"summary\":\"Fenced summary\"}\n```");

        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("Summarize", TaskType.SUMMARIZE, null)
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summary").value("Fenced summary"));
    }

    // ─── Dynamic model selection ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/custom/process — requested model is forwarded to the service")
    void process_forwardsRequestedModel() throws Exception {
        when(agentChatService.chat(anyString(), eq("Refactor this class"), eq("qwen2.5")))
                .thenReturn("{\"summary\":\"Refactored\"}");

        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("Refactor this class", TaskType.SUMMARIZE, "qwen2.5")
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summary").value("Refactored"));

        verify(agentChatService).chat(anyString(), eq("Refactor this class"), eq("qwen2.5"));
    }

    // ─── Validation & errors ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/custom/process — blank prompt returns 400")
    void process_blankPrompt_returns400() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("   ", TaskType.SUMMARIZE, null)
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/custom/process — returns 503 when the AI model is unreachable")
    void process_aiModelUnavailable_returns503() throws Exception {
        when(agentChatService.chat(anyString(), anyString(), isNull()))
                .thenThrow(new AgentChatException(
                        "Could not reach Ollama at http://localhost:11434. "
                                + "Check that the Ollama server is running and that the selected model is pulled."));

        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("Hello!", TaskType.SUMMARIZE, null)
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST /api/v1/custom/process — unparseable model output returns 503")
    void process_unparseableOutput_returns503() throws Exception {
        when(agentChatService.chat(anyString(), anyString(), isNull()))
                .thenReturn("This is not JSON at all");

        String requestBody = objectMapper.writeValueAsString(
                new AgentRequest("Hello!", TaskType.SUMMARIZE, null)
        );

        mockMvc.perform(post("/api/v1/custom/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable());
    }
}