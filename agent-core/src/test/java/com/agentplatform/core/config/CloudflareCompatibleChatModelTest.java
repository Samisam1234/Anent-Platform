package com.agentplatform.core.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@link CloudflareAiGatewayProvider.CloudflareCompatibleChatModel}:
 * the Cloudflare-only wrapper must strip tool specifications and normalize every message
 * to plain single-string content, while the unwrapped {@link ChatModel} path (used by every
 * other provider) still receives tool-enabled requests untouched.
 */
class CloudflareCompatibleChatModelTest {

    private static final class RecordingChatModel implements ChatModel {
        ChatRequest received;

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            this.received = chatRequest;
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    private static ChatRequest requestWithTools() {
        ToolSpecification tool = ToolSpecification.builder().name("generateImage").build();
        return ChatRequest.builder()
                .messages(UserMessage.from("draw a sunset"))
                .toolSpecifications(tool)
                .build();
    }

    @Test
    @DisplayName("Cloudflare wrapper strips tool specifications before delegating")
    void wrapper_stripsTools() {
        RecordingChatModel delegate = new RecordingChatModel();
        ChatModel wrapper = new CloudflareAiGatewayProvider.CloudflareCompatibleChatModel(delegate);

        wrapper.chat(requestWithTools());

        assertThat(delegate.received.parameters().toolSpecifications()).isNullOrEmpty();
        assertThat(delegate.received.messages()).containsExactly(UserMessage.from("draw a sunset"));
    }

    @Test
    @DisplayName("Cloudflare wrapper flattens multi-content user messages to a plain string")
    void wrapper_flattensMultiContentUserMessage() {
        RecordingChatModel delegate = new RecordingChatModel();
        ChatModel wrapper = new CloudflareAiGatewayProvider.CloudflareCompatibleChatModel(delegate);

        UserMessage multiContent = UserMessage.from(List.of(
                new TextContent("first part"),
                new TextContent("second part")));
        wrapper.chat(ChatRequest.builder().messages(multiContent).build());

        ChatMessage delivered = delegate.received.messages().get(0);
        assertThat(delivered).isInstanceOf(UserMessage.class);
        UserMessage user = (UserMessage) delivered;
        assertThat(user.hasSingleText()).isTrue();
        assertThat(user.singleText()).isEqualTo("first part\nsecond part");
    }

    @Test
    @DisplayName("Cloudflare wrapper strips tool calls from assistant messages")
    void wrapper_stripsAssistantToolCalls() {
        RecordingChatModel delegate = new RecordingChatModel();
        ChatModel wrapper = new CloudflareAiGatewayProvider.CloudflareCompatibleChatModel(delegate);

        AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("call_1")
                .name("generateImage")
                .arguments("{\"prompt\":\"a sunset\"}")
                .build());
        wrapper.chat(ChatRequest.builder()
                .messages(UserMessage.from("draw a sunset"), toolCall)
                .build());

        ChatMessage delivered = delegate.received.messages().get(1);
        assertThat(delivered).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) delivered).hasToolExecutionRequests()).isFalse();
    }

    @Test
    @DisplayName("unwrapped ChatModel path (other providers) keeps tool specifications")
    void unwrappedModel_retainsTools() {
        RecordingChatModel delegate = new RecordingChatModel();

        delegate.chat(requestWithTools());

        assertThat(delegate.received.parameters().toolSpecifications()).hasSize(1);
        assertThat(delegate.received.parameters().toolSpecifications().get(0).name())
                .isEqualTo("generateImage");
    }

    @Test
    @DisplayName("wrapper still delegates simple string chat unchanged")
    void wrapper_keepsPlainMessages() {
        RecordingChatModel delegate = new RecordingChatModel();
        ChatModel wrapper = new CloudflareAiGatewayProvider.CloudflareCompatibleChatModel(delegate);

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hello"), UserMessage.from("world"))
                .build();
        wrapper.chat(request);

        assertThat(delegate.received.messages()).isEqualTo(request.messages());
    }
}