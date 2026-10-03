package com.agentplatform.core.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failover behavior tests for {@link LlmProviderRouter}. Uses hand-rolled
 * fakes only — no real network or providers involved.
 */
class LlmProviderRouterFailoverTest {

    private static final ChatRequest REQUEST = ChatRequest.builder()
            .messages(List.of(UserMessage.from("hello")))
            .build();

    private final List<String> attemptOrder = new ArrayList<>();

    private FakeProvider provider(String name, Feed feed) {
        return new FakeProvider(name, true, m -> model(name, feed));
    }

    private FakeProvider unconfigured(String name, Feed feed) {
        return new FakeProvider(name, false, m -> model(name, feed));
    }

    private ChatModel model(String name, Feed feed) {
        return new FakeModel(name, feed);
    }

    @Test
    @DisplayName("Default provider succeeds: only the default provider is called")
    void defaultProviderSucceeds_onlyDefaultCalled() {
        FakeProvider ollama = provider("ollama", Feed.OK);
        FakeProvider gemini = provider("gemini", Feed.OK);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(gemini, ollama), props);

        ChatResponse response = router.chatModel(null, "any-model").chat(REQUEST);

        assertThat(response.aiMessage().text()).isEqualTo("ok from ollama");
        assertThat(attemptOrder).containsExactly("ollama");
    }

    @Test
    @DisplayName("Default provider fails: the next configured provider is attempted and succeeds")
    void defaultFails_nextProviderSucceeds() {
        FakeProvider ollama = provider("ollama", Feed.QUOTA_429);
        FakeProvider gemini = provider("gemini", Feed.OK);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(gemini, ollama), props);

        ChatResponse response = router.chatModel(null, null).chat(REQUEST);

        assertThat(response.aiMessage().text()).isEqualTo("ok from gemini");
        assertThat(attemptOrder).containsExactly("ollama", "gemini");
    }

    @Test
    @DisplayName("Multiple failures: providers are attempted in deterministic order, default first then priority")
    void multipleFailures_deterministicOrder() {
        FakeProvider ollama = provider("ollama", Feed.MODEL_404);   // priority 10, default
        FakeProvider gemini = provider("gemini", Feed.QUOTA_429);   // priority 20
        FakeProvider groq = provider("groq", Feed.SERVER_500);      // priority 30
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        // Scrambled input order: priority sort + default-first must still yield ollama > gemini > groq
        LlmProviderRouter router = new LlmProviderRouter(List.of(groq, ollama, gemini), props);

        assertThatThrownBy(() -> router.chatModel(null, "m").chat(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All configured AI providers failed")
                .hasMessageContaining("ollama")
                .hasMessageContaining("gemini")
                .hasMessageContaining("groq");

        assertThat(attemptOrder).containsExactly("ollama", "gemini", "groq");
    }

    @Test
    @DisplayName("Middle provider succeeds: later providers are never called")
    void middleProviderSucceeds_laterProvidersNotCalled() {
        FakeProvider ollama = provider("ollama", Feed.QUOTA_429);
        FakeProvider gemini = provider("gemini", Feed.OK);
        FakeProvider groq = provider("groq", Feed.OK);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(ollama, gemini, groq), props);

        ChatResponse response = router.chatModel(null, null).chat(REQUEST);

        assertThat(response.aiMessage().text()).isEqualTo("ok from gemini");
        assertThat(attemptOrder).containsExactly("ollama", "gemini");
    }

    @Test
    @DisplayName("All providers fail: final failure thrown, no fabricated success")
    void allProvidersFail_finalFailureThrown() {
        FakeProvider ollama = provider("ollama", Feed.MODEL_404);
        FakeProvider gemini = provider("gemini", Feed.QUOTA_429);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(ollama, gemini), props);

        assertThatThrownBy(() -> router.chatModel(null, null).chat(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ollama")
                .hasMessageContaining("gemini")
                .hasMessageContaining("quota");
    }

    @Test
    @DisplayName("Explicit provider request preserves single-provider behavior (no failover)")
    void explicitProvider_singleProviderNoFailover() {
        FakeProvider ollama = provider("ollama", Feed.MODEL_404);
        FakeProvider gemini = provider("gemini", Feed.OK);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(ollama, gemini), props);

        // Working explicit provider succeeds
        ChatResponse ok = router.chatModel("gemini", "g").chat(REQUEST);
        assertThat(ok.aiMessage().text()).isEqualTo("ok from gemini");

        // Failing explicit provider does NOT fail over to others
        assertThatThrownBy(() -> router.chatModel("ollama", "o").chat(REQUEST))
                .isInstanceOf(RuntimeException.class);

        // Only the explicitly named providers were attempted
        assertThat(attemptOrder).containsExactly("gemini", "ollama");
    }

    @Test
    @DisplayName("Unconfigured providers are never attempted")
    void unconfiguredProviders_neverAttempted() {
        FakeProvider ollama = unconfigured("ollama", Feed.OK); // filtered out by router
        FakeProvider gemini = provider("gemini", Feed.OK);
        LlmProviderRouter router = new LlmProviderRouter(List.of(ollama, gemini), new LlmProperties());

        ChatResponse response = router.chatModel(null, null).chat(REQUEST);

        assertThat(response.aiMessage().text()).isEqualTo("ok from gemini");
        assertThat(attemptOrder).containsExactly("gemini");
        assertThat(router.getConfiguredProviders()).hasSize(1);
    }

    @Test
    @DisplayName("Provider order: explicit default first, remaining configured by priority")
    void providerOrder_defaultFirstThenPriority() {
        FakeProvider ollama = provider("ollama", Feed.MODEL_404);   // priority 10
        FakeProvider gemini = provider("gemini", Feed.SERVER_500);  // priority 20, default
        FakeProvider groq = provider("groq", Feed.MODEL_404);       // priority 30
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("gemini");
        LlmProviderRouter router = new LlmProviderRouter(List.of(groq, ollama, gemini), props);

        assertThatThrownBy(() -> router.chatModel(null, null).chat(REQUEST))
                .isInstanceOf(IllegalStateException.class);

        // Default (gemini, priority 20) first, then ollama (10), then groq (30)
        assertThat(attemptOrder).containsExactly("gemini", "ollama", "groq");
    }

    @Test
    @DisplayName("No duplicate provider attempts per chat request")
    void noDuplicateProviderAttempts() {
        FakeProvider ollama = provider("ollama", Feed.QUOTA_429);
        FakeProvider gemini = provider("gemini", Feed.OK);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(ollama, gemini), props);

        router.chatModel(null, null).chat(REQUEST);

        assertThat(attemptOrder).containsExactly("ollama", "gemini");
    }

    @Test
    @DisplayName("HTTP 429/quota failure is failover-eligible")
    void quotaFailure_isFailoverEligible() {
        FakeProvider ollama = provider("ollama", Feed.QUOTA_429);
        FakeProvider groq = provider("groq", Feed.OK);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(ollama, groq), props);

        assertThat(router.chatModel(null, null).chat(REQUEST).aiMessage().text())
                .isEqualTo("ok from groq");
        assertThat(attemptOrder).containsExactly("ollama", "groq");
    }

    @Test
    @DisplayName("HTTP 404 / model unavailable failure is failover-eligible")
    void modelUnavailableFailure_isFailoverEligible() {
        FakeProvider ollama = provider("ollama", Feed.MODEL_404);
        FakeProvider openrouter = provider("openrouter", Feed.OK);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(ollama, openrouter), props);

        assertThat(router.chatModel(null, null).chat(REQUEST).aiMessage().text())
                .isEqualTo("ok from openrouter");
        assertThat(attemptOrder).containsExactly("ollama", "openrouter");
    }

    @Test
    @DisplayName("HTTP 5xx failure is failover-eligible")
    void serverErrorFailure_isFailoverEligible() {
        FakeProvider ollama = provider("ollama", Feed.SERVER_500);
        FakeProvider cloudflare = provider("cloudflare-ai-gateway", Feed.OK);
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        LlmProviderRouter router = new LlmProviderRouter(List.of(ollama, cloudflare), props);

        assertThat(router.chatModel(null, null).chat(REQUEST).aiMessage().text())
                .isEqualTo("ok from cloudflare-ai-gateway");
        assertThat(attemptOrder).containsExactly("ollama", "cloudflare-ai-gateway");
    }

    // ─── Test doubles ─────────────────────────────────────────────────────────

    private enum Feed {
        OK, QUOTA_429, MODEL_404, SERVER_500
    }

    private final class FakeProvider implements LlmProvider {

        private final String name;
        private final boolean configured;
        private final Function<String, ChatModel> modelFactory;

        private FakeProvider(String name, boolean configured, Function<String, ChatModel> modelFactory) {
            this.name = name;
            this.configured = configured;
            this.modelFactory = modelFactory;
        }

        @Override
        public ChatModel chatModel(String model) {
            attemptOrder.add(name);
            return modelFactory.apply(model);
        }

        @Override
        public String providerName() {
            return name;
        }

        @Override
        public String providerLabel() {
            return name;
        }

        @Override
        public String defaultModel() {
            return name + "-default";
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public Duration timeout() {
            return Duration.ofMinutes(1);
        }
    }

    private final class FakeModel implements ChatModel {

        private final String name;
        private final Feed feed;

        private FakeModel(String name, Feed feed) {
            this.name = name;
            this.feed = feed;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            switch (feed) {
                case OK -> {
                    return ChatResponse.builder().aiMessage(AiMessage.from("ok from " + name)).build();
                }
                case QUOTA_429 -> throw new RuntimeException("HTTP error (429): quota exceeded");
                case MODEL_404 -> throw new RuntimeException("HTTP error (404): model 'missing' not found");
                case SERVER_500 -> throw new RuntimeException("HTTP error (500): internal server error");
                default -> throw new IllegalStateException();
            }
        }
    }
}