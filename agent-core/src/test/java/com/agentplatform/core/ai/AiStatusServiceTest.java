package com.agentplatform.core.ai;

import com.agentplatform.core.config.GeminiProperties;
import com.agentplatform.core.config.OllamaChatModelFactory;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiStatusService}. GeminiProperties + ChatModel are
 * mocked so no network is touched.
 */
class AiStatusServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-30T10:00:00Z"), ZoneOffset.UTC);

    private static GeminiProperties props(boolean enabled, String apiKey) {
        GeminiProperties props = new GeminiProperties();
        props.setEnabled(enabled);
        props.setApiKey(apiKey);
        return props;
    }

    @Test
    @DisplayName("Ollama active by default: reports Ollama unconfigured and never calls the model")
    void ollamaDefault_notConfiguredNoProbe() {
        ChatModel model = mock(ChatModel.class);
        OllamaChatModelFactory ollamaFactory = mock(OllamaChatModelFactory.class);
        when(ollamaFactory.resolveModelName(null)).thenReturn("llama3.2:3b");
        AiStatusService service = new AiStatusService(props(false, null), model, ollamaFactory, fixedClock);

        AiStatusResponse noProbe = service.status(false);
        assertThat(noProbe.configured()).isFalse();
        assertThat(noProbe.message()).contains("Ollama");
        assertThat(noProbe.model()).isEqualTo("llama3.2:3b");
        assertThat(noProbe.checkedAt()).isNull();
        verify(model, never()).chat(anyString());
    }

    @Test
    @DisplayName("Gemini disabled even with a stray key still reports Ollama-not-configured")
    void geminiDisabledButKeyPresent_reportsOllama() {
        AiStatusService service = new AiStatusService(props(false, "test-key"),
                mock(ChatModel.class), mock(OllamaChatModelFactory.class), fixedClock);
        assertThat(service.status(false).configured()).isFalse();
        assertThat(service.status(false).provider()).isEqualTo("Ollama");
    }

    @Test
    @DisplayName("Gemini enabled but never probed reports unverified and does not call the model")
    void enabled_unverifiedWithoutProbe() {
        ChatModel model = mock(ChatModel.class);
        AiStatusService service = new AiStatusService(props(true, "test-key"), model, mock(OllamaChatModelFactory.class), fixedClock);

        AiStatusResponse response = service.status(false);

        assertThat(response.configured()).isTrue();
        assertThat(response.available()).isNull();
        assertThat(response.message()).contains("probe=true");
        verify(model, never()).chat(anyString());
    }

    @Test
    @DisplayName("probe=true with healthy model reports available:true")
    void forcedProbe_healthyReportsAvailable() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("OK");
        AiStatusService service = new AiStatusService(props(true, "test-key"), model, mock(OllamaChatModelFactory.class), fixedClock);

        AiStatusResponse response = service.status(true);

        assertThat(response.configured()).isTrue();
        assertThat(response.available()).isTrue();
        assertThat(response.message()).contains("OK");
        assertThat(response.checkedAt()).isEqualTo(fixedClock.millis());
    }

    @Test
    @DisplayName("probe=true with quota-exhausted model reports the classified diagnostic")
    void forcedProbe_quotaExhaustedReportsDiagnostic() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException(
                "HTTP error (429): { \"error\": { \"code\": 429, \"status\": \"RESOURCE_EXHAUSTED\", "
                        + "\"message\": \"Quota exceeded for metric: "
                        + "generativelanguage.googleapis.com/generate_content_free_tier_requests\" } }"));
        AiStatusService service = new AiStatusService(props(true, "test-key"), model, mock(OllamaChatModelFactory.class), fixedClock);

        AiStatusResponse response = service.status(true);

        assertThat(response.configured()).isTrue();
        assertThat(response.available()).isFalse();
        assertThat(response.message()).contains("quota").contains("429");
        assertThat(response.checkedAt()).isEqualTo(fixedClock.millis());
    }

    @Test
    @DisplayName("probe result is cached so repeated status calls do not burn quota")
    void probeResult_cachedWithinTtl() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("OK");
        AiStatusService service = new AiStatusService(props(true, "test-key"), model, mock(OllamaChatModelFactory.class), fixedClock);

        AiStatusResponse first = service.status(true);
        AiStatusResponse second = service.status(false);
        AiStatusResponse third = service.status(false);

        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);
        verify(model, times(1)).chat(anyString());
    }
}