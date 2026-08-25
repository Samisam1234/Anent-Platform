package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.service.AgentChatService;
import com.agentplatform.ui.dto.ChatRequest;
import com.agentplatform.ui.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the AI agent chat endpoint.
 *
 * <p>Endpoint: {@code POST /api/v1/agent/chat}</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);

    private final AgentChatService agentChatService;

    public AgentChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * Accepts a user query and returns the AI model's response.
     *
     * <p>Request body:
     * <pre>{@code {"query": "Hello, how are you?"}}</pre>
     *
     * <p>Response body (200 OK):
     * <pre>{@code {"response": "I'm doing well, thank you!"}}</pre>
     *
     * <p>Error responses are handled by {@link GlobalExceptionHandler}.
     *
     * @param request the chat request containing the user's query
     * @return 200 OK with the model's response, or an error response
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: query='{}'", request.query());

        String aiResponse = agentChatService.chat(request.query());
        return ResponseEntity.ok(new ChatResponse(aiResponse));
    }
}
