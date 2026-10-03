package com.agentplatform.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.internal.OpenAiUtils;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the Cloudflare Workers AI content-shape incompatibility:
 * every message content in the OpenAI-compatible request sent by
 * {@link CloudflareAiGatewayProvider} must serialize as a plain string, never as an
 * array of content parts. Cloudflare fp8 rejects {@code "content":[...]} with HTTP 503.
 *
 * <p>Mirrors the exact messages {@code AgentChatService} builds for BOTH the simple
 * {@code /api/v1/agent/chat} smoke test and the post-tool round.</p>
 */
class CloudflareRequestFormatCompatibilityTest {

    private static final String MODEL = "@cf/meta/llama-3.1-8b-instruct-fp8";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ChatCompletionRequest map(List<ChatMessage> messages) {
        OpenAiChatRequestParameters params = OpenAiChatRequestParameters.builder()
                .modelName(MODEL)
                .temperature(0.1)
                .maxOutputTokens(4096)
                .build();
        return OpenAiUtils.toOpenAiChatRequest(
                ChatRequest.builder().messages(messages).build(), params, false, false)
                .build();
    }

    private static JsonNode asJson(ChatCompletionRequest request) throws Exception {
        return MAPPER.readTree(MAPPER.writeValueAsString(request));
    }

    @Test
    @DisplayName("simple chat request content serializes as strings, not arrays")
    void simpleChat_contentIsString() throws Exception {
        JsonNode json = asJson(map(List.of(
                SystemMessage.from("You are a helpful research assistant."),
                UserMessage.from("Hello, how are you?"))));

        for (JsonNode message : json.get("messages")) {
            System.out.println("simple chat message: " + message);
            assertThat(message.get("content").isTextual())
                    .as("message %s content must be a plain string, got: %s",
                            message, message.get("content"))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("post-tool round request content serializes as strings, never arrays")
    void toolRound_contentIsString() throws Exception {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("generateImage")
                .arguments("{\"prompt\":\"a sunset\"}")
                .build();
        List<ChatMessage> messages = List.of(
                UserMessage.from("draw a sunset"),
                AiMessage.from(toolCall),
                ToolExecutionResultMessage.from("call_1", "generateImage",
                        "<div class=\"tool-image-result\"><img src=\"https://picsum.photos/...\"/></div>"));

        JsonNode json = asJson(map(messages));

        for (JsonNode message : json.get("messages")) {
            System.out.println("tool round message: " + message);
            JsonNode content = message.get("content");
            assertThat(content == null || content.isTextual())
                    .as("message %s content must be null or a plain string, got: %s",
                            message, content)
                    .isTrue();
        }
    }
}