package com.agentplatform.ui.controller;

import com.agentplatform.core.ai.AiStatusResponse;
import com.agentplatform.core.ai.AiStatusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AiStatusController}; {@link AiStatusService} is mocked.
 */
@WebMvcTest(controllers = AiStatusController.class)
class AiStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiStatusService aiStatusService;

    @Test
    @DisplayName("GET /api/v1/ai/status — reports Ollama-not-configured without probing")
    void status_notConfigured() throws Exception {
        when(aiStatusService.status(false)).thenReturn(
                AiStatusResponse.unconfigured("Ollama", "llama3.2:3b"));

        mockMvc.perform(get("/api/v1/ai/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.provider").value("Ollama"))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.model").value("llama3.2:3b"))
                .andExpect(jsonPath("$.message").value(
                        "Ollama is the active provider. Call with ?probe=true to run a live probe."));
    }

    @Test
    @DisplayName("GET /api/v1/ai/status?probe=true — runs a live probe and reports availability")
    void status_probe_trueAndAvailable() throws Exception {
        when(aiStatusService.status(true)).thenReturn(
                AiStatusResponse.verified("Ollama", "llama3.2:3b", true,
                        "OK — Ollama reached a model successfully.", 123456L));

        mockMvc.perform(get("/api/v1/ai/status").param("probe", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.model").value("llama3.2:3b"))
                .andExpect(jsonPath("$.message").value("OK — Ollama reached a model successfully."))
                .andExpect(jsonPath("$.checkedAt").value(123456));
    }

    @Test
    @DisplayName("GET /api/v1/ai/status?probe=true — surfaces a network diagnostic for Ollama")
    void status_probe_networkDiagnostic() throws Exception {
        when(aiStatusService.status(eq(true))).thenReturn(
                AiStatusResponse.verified("Ollama", "llama3.2:3b", false,
                        "Could not reach Ollama at http://localhost:11434. "
                                + "Check that the Ollama server is running and that the selected model is pulled, "
                                + "then try again.",
                        123456L));

        mockMvc.perform(get("/api/v1/ai/status").param("probe", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Could not reach Ollama at http://localhost:11434. "
                                + "Check that the Ollama server is running and that the selected model is pulled, "
                                + "then try again."));
    }
}