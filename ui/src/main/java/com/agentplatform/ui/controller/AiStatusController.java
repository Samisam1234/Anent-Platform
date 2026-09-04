package com.agentplatform.ui.controller;

import com.agentplatform.core.ai.AiStatusResponse;
import com.agentplatform.core.ai.AiStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI health check endpoint.
 *
 * <p>{@code GET /api/v1/ai/status} — cheap report of whether {@code GEMINI_API_KEY}
 * is configured and, if a fresh live probe exists, whether Gemini or Ollama
 * is currently available. It never calls the LLM on its own.</p>
 *
 * <p>{@code GET /api/v1/ai/status?probe=true} — runs a real one-shot probe
 * against the primary cloud model (Gemini), falling back to Ollama if the
 * cloud key is absent, and caches the result for one minute. Failures
 * (auth / quota / model / network) are classified into safe, actionable
 * diagnostics. The API key is never returned.</p>
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiStatusController {

    private final AiStatusService aiStatusService;

    public AiStatusController(AiStatusService aiStatusService) {
        this.aiStatusService = aiStatusService;
    }

    @GetMapping("/status")
    public AiStatusResponse status(@RequestParam(value = "probe", defaultValue = "false") boolean probe) {
        return aiStatusService.status(probe);
    }
}