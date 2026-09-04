package com.agentplatform.orchestrator.resume;

import com.agentplatform.core.config.OllamaChatModelFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}