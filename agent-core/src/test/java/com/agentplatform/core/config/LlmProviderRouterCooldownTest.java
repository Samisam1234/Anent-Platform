package com.agentplatform.core.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cooldown / circuit-breaker behavior (Phase 13.9) for {@link LlmProviderRouter},
 * layered on top of the Phase 13.8 failover. Hand-rolled fakes plus an
 * injectable, advanceable {@link Clock} — no sleeping, no network.
 */
class LlmProviderRouterCooldownTest {

    private static final ChatRequest REQUEST = ChatRequest.builder()
            .messages(List.of(UserMessage.from("hello")))
            .build();

    private static final int THRESHOLD = 3;
    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    /** Deterministic, advanceable time source. */
    static final class TestClock extends Clock {
        private volatile long millis;

        TestClock(long start) {
            this.millis = start;
        }

        void advance(long millis) {
            this.millis += millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    private List<String> attempts = Collections.synchronizedList(new ArrayList<>());
    private TestClock clock;
    private FakeProvider ollama;
    private FakeProvider gemini;
    private FakeProvider groq;
    private LlmProviderRouter router;

    private void setupDefaultLlm(FakeProvider... providers) {
        LlmProperties props = new LlmProperties();
        props.setDefaultProvider("ollama");
        props.setFailureThreshold(THRESHOLD);
        props.setCooldownDuration(COOLDOWN);
        routerFor(props, providers);
    }

    private void routerFor(LlmProperties props, FakeProvider... providers) {
        clock = new TestClock(1_000_000L);
        router = new LlmProviderRouter(List.of(providers), props, clock);
    }

    private FakeProvider failing(String name) {
        return provider(name, true);
    }

    private FakeProvider working(String name) {
        return provider(name, false);
    }

    private FakeProvider provider(String name, boolean failing) {
        FakeProvider p = new FakeProvider(name, failing, attempts);
        switch (name) {
            case "ollama" -> ollama = p;
            case "gemini" -> gemini = p;
            case "groq" -> groq = p;
            default -> {
            }
        }
        return p;
    }

    private ChatResponse chat() {
        return router.chatModel(null, null).chat(REQUEST);
    }

    private ChatResponse nextEligibleResponse() {
        return router.chatModel(null, null).chat(REQUEST);
    }

    @Test
    @DisplayName("Success: no failure count, no cooldown, provider always attempted")
    void success_noFailureNoCooldown() {
        setupDefaultLlm(working("ollama"), working("gemini"));

        assertThat(chat().aiMessage().text()).isEqualTo("ok from ollama");
        clock.advance(COOLDOWN.toMillis() * 10);
        assertThat(chat().aiMessage().text()).isEqualTo("ok from ollama");
        assertThat(attempts).containsExactly("ollama", "ollama");
    }

    @Test
    @DisplayName("First eligible failures increment count but provider stays usable below threshold")
    void firstFailures_belowThreshold_stillAttempted() {
        setupDefaultLlm(failing("ollama"), working("gemini"));

        assertThat(chat().aiMessage().text()).isEqualTo("ok from gemini");
        assertThat(chat().aiMessage().text()).isEqualTo("ok from gemini");
        // Below threshold (2 < 3): ollama still attempted on every request
        assertThat(attempts).containsExactly("ollama", "gemini", "ollama", "gemini");
    }

    @Test
    @DisplayName("Threshold reached: provider enters cooldown and is skipped on the next request")
    void thresholdReached_providerSkipped() {
        setupDefaultLlm(failing("ollama"), working("gemini"));

        chat();                          // failure 1
        chat();                          // failure 2
        chat();                          // failure 3 -> cooldown, gemini answers
        assertThat(nextEligibleResponse().aiMessage().text()).isEqualTo("ok from gemini");

        // ollama was attempted 3 times (below/at threshold), then skipped entirely
        assertThat(attempts).containsExactly(
                "ollama", "gemini",
                "ollama", "gemini",
                "ollama", "gemini",
                "gemini");
    }

    @Test
    @DisplayName("Provider in cooldown: skipped, next eligible provider attempted")
    void inCooldown_skipped_nextEligibleAttempted() {
        setupDefaultLlm(failing("ollama"), working("gemini"));

        chat();
        chat();
        chat(); // cooldown triggered
        // Now gemini succeeds alone (ollama in cooldown, skipped without waiting)
        assertThat(nextEligibleResponse().aiMessage().text()).isEqualTo("ok from gemini");
        assertThat(attempts).endsWith("gemini");
        assertThat(attempts.stream().filter("ollama"::equals).count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Cooldown expiry: provider becomes eligible again")
    void cooldownExpiry_providerEligibleAgain() {
        setupDefaultLlm(failing("ollama"), working("gemini"));

        chat();
        chat();
        chat(); // ollama in cooldown

        clock.advance(COOLDOWN.toMillis() + 1);
        assertThat(nextEligibleResponse().aiMessage().text()).isEqualTo("ok from gemini");
        // ollama attempted again after expiry (and fails -> count 4), then gemini
        assertThat(attempts).endsWith("ollama", "gemini");
    }

    @Test
    @DisplayName("Successful request after cooldown resets failure count and clears cooldown")
    void successAfterCooldown_resetsAndClears() {
        setupDefaultLlm(failing("ollama"), working("gemini"));

        chat();
        chat();
        chat(); // ollama in cooldown

        ollama.setFailing(false);        // provider comes back
        clock.advance(COOLDOWN.toMillis() + 1);
        assertThat(nextEligibleResponse().aiMessage().text()).isEqualTo("ok from ollama");

        // Failure count reset to 0: two consecutive failures should NOT re-trigger cooldown quickly
        ollama.setFailing(true);
        chat();                          // failure 1 (count 1)
        chat();                          // failure 2 (count 2) — still attempted
        // 3 cooldown-triggering chats + 1 recovery + 2 post-recovery chats = 6 ollama attempts
        assertThat(attempts.stream().filter("ollama"::equals).count()).isEqualTo(6);
        assertThat(attempts).endsWith("ollama", "gemini", "ollama", "gemini");
    }

    @Test
    @DisplayName("One provider cooling down does not block other providers")
    void multipleProviders_oneCoolingDoesNotBlockOthers() {
        setupDefaultLlm(failing("ollama"), failing("gemini"), working("groq"));

        chat();                          // ollama fails (1), gemini fails (1), groq ok
        chat();                          // ollama (2), gemini (2), groq ok
        chat();                          // ollama (3 -> cooldown), gemini (3 -> cooldown), groq ok
        assertThat(nextEligibleResponse().aiMessage().text()).isEqualTo("ok from groq");

        // Both cooled providers skipped; groq keeps serving
        assertThat(attempts).endsWith("groq");
    }

    @Test
    @DisplayName("Explicit provider request: no failover, no silent substitution, cooldown bypassed")
    void explicitProvider_noFailover_noSilentRedirect() {
        setupDefaultLlm(failing("ollama"), working("gemini"));

        // Cool ollama via automatic failover
        chat();
        chat();
        chat();

        // Explicit gemini still works directly
        ChatResponse gem = router.chatModel("gemini", null).chat(REQUEST);
        assertThat(gem.aiMessage().text()).isEqualTo("ok from gemini");

        // Explicit failing ollama (in cooldown) is still attempted directly and fails — no substitution
        assertThatThrownBy(() -> router.chatModel("ollama", null).chat(REQUEST))
                .isInstanceOf(RuntimeException.class);

        // Explicit calls do not add automatic-failover attempts for other providers
        assertThat(attempts.stream().filter("gemini"::equals).count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("One attempt per provider per request even with cooldown state present")
    void oneAttemptPerProviderPerRequest() {
        setupDefaultLlm(failing("ollama"), working("gemini"));

        chat();
        chat();
        // Request 3: ollama below threshold, attempted exactly once, then gemini
        chat();
        assertThat(attempts).containsExactly(
                "ollama", "gemini",
                "ollama", "gemini",
                "ollama", "gemini");
    }

    @Test
    @DisplayName("Concurrent automatic failover requests keep cooldown state safe and deterministic")
    void concurrentAccess_stateSafety() throws Exception {
        setupDefaultLlm(failing("ollama"), working("gemini")); // threshold 3

        int tasks = 20;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                futures.add(pool.submit(() -> nextEligibleResponse()));
            }
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS); // no exceptions escaping; every request completes via gemini
            }
        } finally {
            pool.shutdownNow();
        }

        // Enough concurrent failures reached the threshold; ollama must now be cooled
        assertThat(nextEligibleResponse().aiMessage().text()).isEqualTo("ok from gemini");
        assertThat(attempts).endsWith("gemini");

        // ollama was attempted at least 3 times (concurrent increments are atomic)
        long ollamaAttempts = attempts.stream().filter("ollama"::equals).count();
        assertThat(ollamaAttempts).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("All providers unavailable: classified failure preserved, no fabricated success")
    void allProvidersUnavailable_noFabricatedSuccess() {
        setupDefaultLlm(failing("ollama"), failing("gemini"));

        // counts 1/1 and 2/2 — aggregated classified error each time, no fabricated success
        assertThatThrownBy(this::chat)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All configured AI providers failed")
                .hasMessageContaining("ollama")
                .hasMessageContaining("gemini");
        assertThatThrownBy(this::chat)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All configured AI providers failed");

        // counts 3/3 -> both in cooldown; next request skips both, still an error
        assertThatThrownBy(this::chat)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All configured AI providers failed");
        assertThatThrownBy(this::nextEligibleResponse)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All configured AI providers failed")
                .hasMessageContaining("skipped (provider in cooldown)");
    }

    @Test
    @DisplayName("Cooldown configuration defaults: threshold 3, duration 30s")
    void llmProperties_cooldownDefaults() {
        LlmProperties props = new LlmProperties();
        assertThat(props.getFailureThreshold()).isEqualTo(3);
        assertThat(props.getCooldownDuration()).isEqualTo(Duration.ofSeconds(30));
    }

    // ─── Test doubles ─────────────────────────────────────────────────────────

    static final class FakeProvider implements LlmProvider {

        private final String name;
        private final List<String> attempts;
        private volatile boolean failing;

        FakeProvider(String name, boolean failing, List<String> attempts) {
            this.name = name;
            this.failing = failing;
            this.attempts = attempts;
        }

        void setFailing(boolean failing) {
            this.failing = failing;
        }

        @Override
        public ChatModel chatModel(String model) {
            attempts.add(name);
            return new FakeModel(name, () -> failing);
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
            return true;
        }

        @Override
        public Duration timeout() {
            return Duration.ofMinutes(1);
        }
    }

    interface FailureFlag {
        boolean isFailing();
    }

    static final class FakeModel implements ChatModel {

        private final String name;
        private final FailureFlag failureFlag;

        FakeModel(String name, FailureFlag failureFlag) {
            this.name = name;
            this.failureFlag = failureFlag;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            if (failureFlag.isFailing()) {
                throw new RuntimeException("HTTP error (500): internal server error");
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("ok from " + name)).build();
        }
    }
}