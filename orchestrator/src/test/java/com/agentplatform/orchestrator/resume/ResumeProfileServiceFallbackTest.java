package com.agentplatform.orchestrator.resume;

import com.agentplatform.core.config.OllamaChatModelFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Resume intelligence fallback reliability (Phase 2 Step 2.3 §7).
 * The deterministic parser must still produce a valid CandidateProfile when the
 * LLM (Ollama) is unavailable, returns an empty response, or a malformed response.
 */
@DisplayName("ResumeProfileService — deterministic fallback when AI is unavailable")
class ResumeProfileServiceFallbackTest {

    private static final String RESUME = """
            Alice Johnson
            alice@example.com
            Skills
            Java, Spring Boot, PostgreSQL
            """;

    private ResumeProfileService newService(OllamaChatModelFactory factory) {
        return new ResumeProfileService(factory, new ObjectMapper());
    }

    private OllamaChatModelFactory unavailableFactory() {
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        when(factory.chatModel(any())).thenThrow(new RuntimeException("connection refused"));
        return factory;
    }

    private OllamaChatModelFactory returningFactory(String llmResponse) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString())).thenReturn(llmResponse);
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        when(factory.chatModel(any())).thenReturn(chatModel);
        return factory;
    }

    @Nested
    @DisplayName("Fallback behaviour")
    class FallbackTests {

        @Test
        @DisplayName("Ollama unavailable → deterministic fallback produces a valid profile")
        void ollamaUnavailableFallsBack() {
            ResumeProfileService service = newService(unavailableFactory());
            CandidateProfile profile = service.buildProfile(RESUME);

            assertEquals("Alice Johnson", profile.name());
            assertEquals("alice@example.com", profile.email());
            assertTrue(profile.skills().contains("Java"));
            assertTrue(profile.skills().contains("Spring Boot"));
            assertTrue(profile.skills().contains("PostgreSQL"));
        }

        @Test
        @DisplayName("empty AI response → deterministic fallback")
        void emptyResponseFallsBack() {
            ResumeProfileService service = newService(returningFactory(""));
            CandidateProfile profile = service.buildProfile(RESUME);

            assertEquals("Alice Johnson", profile.name());
            assertTrue(profile.skills().contains("Java"));
        }

        @Test
        @DisplayName("whitespace-only AI response → deterministic fallback")
        void blankResponseFallsBack() {
            ResumeProfileService service = newService(returningFactory("   \n  "));
            CandidateProfile profile = service.buildProfile(RESUME);

            assertEquals("Alice Johnson", profile.name());
        }

        @Test
        @DisplayName("malformed (non-JSON) AI response → deterministic fallback")
        void malformedResponseFallsBack() {
            ResumeProfileService service = newService(returningFactory("I am not JSON, sorry."));
            CandidateProfile profile = service.buildProfile(RESUME);

            assertEquals("Alice Johnson", profile.name());
            assertTrue(profile.skills().contains("Spring Boot"));
        }

        @Test
        @DisplayName("valid AI response is honored and not overwritten by the fallback")
        void validResponseWins() {
            String validJson = """
                    {"name":"Alice Johnson","email":"alice@example.com","skills":["Java","Docker"],"softwareSkills":["Java","Docker"]}""";
            ResumeProfileService service = newService(returningFactory(validJson));
            CandidateProfile profile = service.buildProfile(RESUME);

            assertEquals("Alice Johnson", profile.name());
            assertTrue(profile.skills().contains("Java"));
            assertTrue(profile.skills().contains("Docker"));
        }

        @Test
        @DisplayName("blank resume text still throws IllegalArgumentException (unchanged)")
        void blankResumeStillThrows() {
            ResumeProfileService service = newService(unavailableFactory());
            assertThrows(IllegalArgumentException.class, () -> service.buildProfile("   "));
            assertThrows(IllegalArgumentException.class, () -> service.buildProfile(null));
        }
    }

    @Nested
    @DisplayName("Bounded AI call — the request must never wait indefinitely")
    class DeadlineTests {

        private ChatModel chatModelReturning(String response) {
            ChatModel chatModel = mock(ChatModel.class);
            when(chatModel.chat(anyString())).thenReturn(response);
            return chatModel;
        }

        private OllamaChatModelFactory factoryReturning(ChatModel model, Duration timeout) {
            OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
            when(factory.resolveModelName(any())).thenReturn("gemma3:4b");
            when(factory.timeout()).thenReturn(timeout);
            when(factory.chatModel(any())).thenReturn(model);
            return factory;
        }

        @Test
        @DisplayName("an AI call that never returns is cut off at the deadline and falls back")
        void hangingAiIsCutOffAtDeadline() {
            ChatModel hanging = mock(ChatModel.class);
            when(hanging.chat(anyString())).thenAnswer(invocation -> {
                Thread.sleep(30_000);
                return "{\"name\":\"Never Reached\"}";
            });
            ResumeProfileService service =
                    newService(factoryReturning(hanging, Duration.ofMillis(200)));

            long startNanos = System.nanoTime();
            ResumeProfileService.ProfileOutcome outcome = service.buildProfileOutcome(RESUME);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertTrue(elapsedMillis < 5_000,
                    "must return close to the 200ms deadline, took " + elapsedMillis + "ms");
            assertFalse(outcome.aiUsed());
            assertNotNull(outcome.notice());
            assertTrue(outcome.notice().contains("did not respond within"),
                    "notice should explain the timeout, was: " + outcome.notice());
            assertEquals("Alice Johnson", outcome.profile().name());
        }

        @Test
        @DisplayName("a successful AI parse reports aiUsed=true and carries no notice")
        void successfulParseReportsAiUsed() {
            ResumeProfileService service = newService(factoryReturning(
                    chatModelReturning("{\"name\":\"Alice Johnson\",\"email\":\"alice@example.com\"}"),
                    Duration.ofSeconds(30)));

            ResumeProfileService.ProfileOutcome outcome = service.buildProfileOutcome(RESUME);

            assertTrue(outcome.aiUsed());
            assertNull(outcome.notice());
            assertEquals("Alice Johnson", outcome.profile().name());
        }

        @Test
        @DisplayName("an unavailable provider reports aiUsed=false with an explanatory notice")
        void unavailableProviderReportsNotice() {
            ResumeProfileService service =
                    newService(factoryReturning(chatModelReturning(""), Duration.ofSeconds(30)));

            ResumeProfileService.ProfileOutcome outcome = service.buildProfileOutcome(RESUME);

            assertFalse(outcome.aiUsed());
            assertNotNull(outcome.notice());
            assertEquals("Alice Johnson", outcome.profile().name());
        }

        @Test
        @DisplayName("buildProfile still returns just the profile (ResumeAgent back-compat)")
        void buildProfileStillReturnsProfileOnly() {
            ResumeProfileService service = newService(factoryReturning(
                    chatModelReturning(""), Duration.ofSeconds(30)));

            assertEquals("Alice Johnson", service.buildProfile(RESUME).name());
        }
    }
}
