package com.agentplatform.core.ai;

import com.agentplatform.core.config.GeminiProperties;
import com.agentplatform.core.config.OllamaChatModelFactory;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Drives the {@code GET /api/v1/ai/status} health check.
 *
 * <p>Ollama is the default provider: when {@code gemini.enabled=false} (the
 * default) the injected shared {@link ChatModel} bean is Ollama-backed and this
 * service reports/probes Ollama. Only when Gemini is explicitly enabled does it
 * report/probe Google Gemini.</p>
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>The plain {@link #status(boolean)} call is cheap: it reports the active
 *       provider and replays the last probe if it is still fresh, but does
 *       <em>not</em> call the model on its own.</li>
 *   <li>A live probe only runs when explicitly requested
 *       ({@code GET /api/v1/ai/status?probe=true}) and is short-circuited by a
 *       fresh cache entry.</li>
 * </ul>
 */
@Service
public class AiStatusService {

    private static final Logger log = LoggerFactory.getLogger(AiStatusService.class);

    static final String PROBE_PROMPT = "Reply with the exact single word: OK";
    static final long PROBE_TIMEOUT_SECONDS = 8;
    static final long PROBE_TIMEOUT_SECONDS_OLLAMA = 30;
    static final long CACHE_TTL_SECONDS = 60;

    private static final ExecutorService PROBE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-status-probe");
        thread.setDaemon(true);
        return thread;
    });

    private final GeminiProperties properties;
    private final ChatModel chatModel;
    private final OllamaChatModelFactory ollamaChatModelFactory;
    private final Clock clock;

    private volatile AiStatusResponse cached;

    public AiStatusService(GeminiProperties properties, ChatModel chatModel,
                           OllamaChatModelFactory ollamaChatModelFactory, Clock clock) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.ollamaChatModelFactory = ollamaChatModelFactory;
        this.clock = clock;
    }

    /** See class docs for the cheap/probe dichotomy. */
    public AiStatusResponse status(boolean forceProbe) {
        boolean geminiEnabled = properties.isEnabled();
        String provider = geminiEnabled ? "Google Gemini" : "Ollama";
        String classifierProvider = geminiEnabled ? "Gemini" : "Ollama";
        String model = geminiEnabled ? properties.getChatModel()
                : ollamaChatModelFactory.resolveModelName(null);

        if (!forceProbe) {
            AiStatusResponse snapshot = cached;
            if (snapshot != null && isFresh(snapshot)) {
                return snapshot;
            }
            return geminiEnabled
                    ? AiStatusResponse.unverified(provider, model)
                    : AiStatusResponse.unconfigured(provider, model);
        }

        long timeoutSeconds = geminiEnabled ? PROBE_TIMEOUT_SECONDS : PROBE_TIMEOUT_SECONDS_OLLAMA;
        return probeBounded(provider, classifierProvider, model, timeoutSeconds);
    }

    private AiStatusResponse probeBounded(String provider, String classifierProvider, String model,
                                          long timeoutSeconds) {
        // Executor bounded so a slow/hanging model call cannot stall the endpoint.
        CompletableFuture<AiStatusResponse> future =
                CompletableFuture.supplyAsync(() -> probe(provider, classifierProvider, model), PROBE_EXECUTOR);
        try {
            AiStatusResponse result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            cached = result;
            return result;
        } catch (TimeoutException te) {
            future.cancel(true);
            AiStatusResponse timedOut = AiStatusResponse.verified(provider, model, false,
                    provider + " probe timed out after " + timeoutSeconds
                            + "s (the model may still be loading on first use). Please retry.",
                    clock.millis());
            cached = timedOut;
            return timedOut;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return AiStatusResponse.unverified(provider, model);
        } catch (ExecutionException ee) {
            throw new IllegalStateException("Unexpected probe failure", ee);
        }
    }

    private AiStatusResponse probe(String provider, String classifierProvider, String model) {
        try {
            String answer = chatModel.chat(PROBE_PROMPT);
            boolean ok = answer != null && !answer.isBlank();
            return AiStatusResponse.verified(provider, model, ok,
                    ok ? "OK — " + provider + " reached a model successfully."
                            : provider + " model answered without text.",
                    clock.millis());
        } catch (Exception e) {
            AiErrorClassifier.Failure failure = AiErrorClassifier.classify(e, classifierProvider);
            log.warn("{} status probe failed: {}", classifierProvider, failure.message());
            return AiStatusResponse.verified(provider, model, false, failure.message(), clock.millis());
        }
    }

    private boolean isFresh(AiStatusResponse response) {
        return response.checkedAt() != null
                && (clock.millis() - response.checkedAt()) / 1000L <= CACHE_TTL_SECONDS;
    }
}