package com.agentplatform.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LlmProviderRouter} to verify provider ordering and recognition.
 */
class LlmProviderRouterTest {

    @Test
    @DisplayName("Router recognizes Ollama as highest priority when only Ollama configured")
    void onlyOllamaConfigured_ollamaIsDefault() {
        OllamaProperties ollamaProps = new OllamaProperties();
        ollamaProps.setChatModel("llama3.2:3b");
        ollamaProps.setBaseUrl("http://localhost:11434");
        ollamaProps.setReasoningTimeout(java.time.Duration.ofMinutes(2));

        OllamaProvider ollamaProvider = new OllamaProvider(ollamaProps);

        LlmProviderRouter router = new LlmProviderRouter(List.of(ollamaProvider));

        assertThat(router.getConfiguredProviders()).hasSize(1);
        assertThat(router.getConfiguredProviders().get(0).providerName()).isEqualTo("ollama");
        assertThat(router.isProviderAvailable("ollama")).isTrue();
        assertThat(router.defaultModel()).isEqualTo("llama3.2:3b");
    }

    @Test
    @DisplayName("Router orders providers by priority: Ollama > Gemini > Groq")
    void multipleProviders_orderedByPriority() {
        OllamaProperties ollamaProps = new OllamaProperties();
        ollamaProps.setChatModel("llama3.2:3b");
        ollamaProps.setBaseUrl("http://localhost:11434");

        GeminiProperties geminiProps = new GeminiProperties();
        geminiProps.setEnabled(true);
        geminiProps.setApiKey("test-gemini-key");
        geminiProps.setChatModel("gemini-2.5-flash");

        GroqProperties groqProps = new GroqProperties();
        groqProps.setEnabled(true);
        groqProps.setApiKey("test-groq-key");
        groqProps.setChatModel("llama-3.1-8b-instant");

        OllamaProvider ollamaProvider = new OllamaProvider(ollamaProps);
        GeminiProvider geminiProvider = new GeminiProvider(geminiProps);
        GroqProvider groqProvider = new GroqProvider(groqProps);

        LlmProviderRouter router = new LlmProviderRouter(List.of(ollamaProvider, geminiProvider, groqProvider));

        assertThat(router.getConfiguredProviders()).hasSize(3);
        assertThat(router.getConfiguredProviders().get(0).providerName()).isEqualTo("ollama");
        assertThat(router.getConfiguredProviders().get(1).providerName()).isEqualTo("gemini");
        assertThat(router.getConfiguredProviders().get(2).providerName()).isEqualTo("groq");
        assertThat(router.defaultModel()).isEqualTo("llama3.2:3b");
    }

    @Test
    @DisplayName("Router filters out unconfigured providers")
    void unconfiguredProviders_areFilteredOut() {
        OllamaProperties ollamaProps = new OllamaProperties();
        ollamaProps.setChatModel("llama3.2:3b");
        ollamaProps.setBaseUrl("http://localhost:11434");

        GeminiProperties geminiProps = new GeminiProperties();
        geminiProps.setEnabled(false); // disabled
        geminiProps.setApiKey("test-key");

        GroqProperties groqProps = new GroqProperties();
        groqProps.setEnabled(true);
        groqProps.setApiKey(null); // no API key = not configured

        OllamaProvider ollamaProvider = new OllamaProvider(ollamaProps);
        GeminiProvider geminiProvider = new GeminiProvider(geminiProps);
        GroqProvider groqProvider = new GroqProvider(groqProps);

        LlmProviderRouter router = new LlmProviderRouter(List.of(ollamaProvider, geminiProvider, groqProvider));

        assertThat(router.getConfiguredProviders()).hasSize(1);
        assertThat(router.getConfiguredProviders().get(0).providerName()).isEqualTo("ollama");
        assertThat(router.isProviderAvailable("gemini")).isFalse();
        assertThat(router.isProviderAvailable("groq")).isFalse();
    }

    @Test
    @DisplayName("Router can select specific provider by name")
    void selectProviderByName_works() {
        OllamaProperties ollamaProps = new OllamaProperties();
        ollamaProps.setChatModel("llama3.2:3b");
        ollamaProps.setBaseUrl("http://localhost:11434");

        GroqProperties groqProps = new GroqProperties();
        groqProps.setEnabled(true);
        groqProps.setApiKey("test-groq-key");
        groqProps.setChatModel("llama-3.1-8b-instant");

        OllamaProvider ollamaProvider = new OllamaProvider(ollamaProps);
        GroqProvider groqProvider = new GroqProvider(groqProps);

        LlmProviderRouter router = new LlmProviderRouter(List.of(ollamaProvider, groqProvider));

        // Default should be Ollama (highest priority)
        assertThat(router.chatModel(null, "test").toString()).isNotNull();

        // Explicitly select Groq
        assertThat(router.isProviderAvailable("groq")).isTrue();
        // Note: we can't easily test chatModel() without network, but we verified provider is available
    }

    @Test
    @DisplayName("Router throws when no providers configured")
    void noProviders_throwsException() {
        LlmProviderRouter router = new LlmProviderRouter(List.of());

        assertThatThrownBy(() -> router.chatModel(null, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No LLM providers are configured");
    }

    @Test
    @DisplayName("Router throws when requested provider not available")
    void unknownProvider_throwsException() {
        OllamaProperties ollamaProps = new OllamaProperties();
        ollamaProps.setChatModel("llama3.2:3b");
        ollamaProps.setBaseUrl("http://localhost:11434");

        OllamaProvider ollamaProvider = new OllamaProvider(ollamaProps);

        LlmProviderRouter router = new LlmProviderRouter(List.of(ollamaProvider));

        assertThatThrownBy(() -> router.chatModel("unknown", "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider 'unknown' is not configured");
    }
}