package com.agentplatform.orchestrator.gap;

import com.agentplatform.core.config.OllamaChatModelFactory;
import com.agentplatform.orchestrator.matching.CareerTrack;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 4 Step 4.3 — AI-assisted career improvement plan.
 *
 * <p>Covers the authoritative-then-AI-then-fallback sequence, the hallucination guard
 * (new skills, reordering, type/rank manipulation, invented experience) and the
 * deterministic fallback. The {@link ChatModel} is mocked — no real Ollama is ever
 * called from these unit tests.</p>
 */
@DisplayName("CareerImprovementPlanService — AI-assisted plan with deterministic fallback (Step 4.3)")
class CareerImprovementPlanServiceTest {

    // ─── Factory / model stubs ──────────────────────────────────────────────

    private CareerImprovementPlanService newService(OllamaChatModelFactory factory) {
        return new CareerImprovementPlanService(factory);
    }

    private OllamaChatModelFactory factoryReturning(String llmResponse) {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn(llmResponse);
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        when(factory.chatModel(any())).thenReturn(model);
        return factory;
    }

    private OllamaChatModelFactory factoryFailing() {
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        when(factory.chatModel(any())).thenThrow(new RuntimeException("connection refused"));
        return factory;
    }

    // ─── Analysis fixtures ─────────────────────────────────────────────────-

    private CareerGapAnalysis analysisOf(ImprovementPriority... priorities) {
        return new CareerGapAnalysis(null, "j1",
                List.of(), List.of(), List.of(), List.of(),
                new ExperienceGap(null, null, null, false),
                CareerTrack.UNKNOWN, CareerTrack.UNKNOWN, false,
                GapSeverity.MEDIUM, List.of(priorities));
    }

    private ImprovementPriority required(String skill) {
        return ImprovementPriority.requiredSkill(1, skill);
    }

    private ImprovementPriority preferred(int rank, String skill) {
        return ImprovementPriority.preferredSkill(rank, skill);
    }

    // ─── 1–4. Fallback & success paths ──────────────────────────────────────

    @Nested
    @DisplayName("Core paths")
    class CoreTests {

        @Test
        @DisplayName("Ollama unavailable → deterministic fallback with typed actions")
        void ollamaUnavailableFallback() {
            CareerImprovementPlan plan = newService(factoryFailing())
                    .generatePlan(analysisOf(required("Docker")));

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
            assertEquals(1, plan.priorityItems().size());
            ImprovementPlanItem item = plan.priorityItems().get(0);
            assertEquals(1, item.rank());
            assertEquals("Docker", item.focus());
            assertEquals(ImprovementPriority.TYPE_REQUIRED_SKILL, item.type());
            assertEquals("required by target job", item.reason());
            assertTrue(item.suggestedActions().contains("Review the fundamentals of Docker."));
        }

        @Test
        @DisplayName("valid AI response → AI plan with deterministic structured fields preserved")
        void validAiResponse() {
            String json = """
                    {"summary":"Focus on Docker skills.",
                     "items":[{"rank":1,"focus":"Docker","type":"REQUIRED_SKILL",
                        "explanation":"Docker matters for deploying the app.",
                        "suggestedActions":["Build a compose stack","Write a Dockerfile"]}]}""";
            CareerImprovementPlan plan = newService(factoryReturning(json))
                    .generatePlan(analysisOf(required("Docker")));

            assertEquals(CareerImprovementPlan.ORIGIN_AI, plan.origin());
            assertEquals("Focus on Docker skills.", plan.summary());
            assertEquals(1, plan.priorityItems().size());
            ImprovementPlanItem item = plan.priorityItems().get(0);
            // Authoritative fields always come from the deterministic layer.
            assertEquals(1, item.rank());
            assertEquals("Docker", item.focus());
            assertEquals(ImprovementPriority.TYPE_REQUIRED_SKILL, item.type());
            assertEquals("required by target job", item.reason());
            // Advisory fields come from the AI.
            assertEquals("Docker matters for deploying the app.", item.explanation());
            assertEquals(List.of("Build a compose stack", "Write a Dockerfile"),
                    item.suggestedActions());
        }

        @Test
        @DisplayName("empty AI response → deterministic fallback")
        void emptyAiResponseFallback() {
            CareerImprovementPlan plan = newService(factoryReturning(""))
                    .generatePlan(analysisOf(required("Docker")));

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
            assertEquals(1, plan.priorityItems().size());
        }

        @Test
        @DisplayName("malformed (non-JSON) AI response → deterministic fallback")
        void malformedAiResponseFallback() {
            CareerImprovementPlan plan = newService(factoryReturning("I am not JSON."))
                    .generatePlan(analysisOf(required("Docker")));

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
            assertEquals(1, plan.priorityItems().size());
        }

        @Test
        @DisplayName("AI wraps JSON in markdown fences yet is still parsed")
        void aiResponseWrappedInFences() {
            String json = """
                    ```json
                    {"summary":"Plan","items":[{"rank":1,"focus":"Docker",
                       "explanation":"e","suggestedActions":["a"]}]}
                    ```""";
            CareerImprovementPlan plan = newService(factoryReturning(json))
                    .generatePlan(analysisOf(required("Docker")));

            assertEquals(CareerImprovementPlan.ORIGIN_AI, plan.origin());
        }
    }

    // ─── 6–9. Hallucination guard ───────────────────────────────────────────

    @Nested
    @DisplayName("Hallucination guard")
    class GuardTests {

        @Test
        @DisplayName("AI adding a skill not in the deterministic list → rejected → fallback")
        void inventedSkillRejected() {
            // Deterministic list has only Docker; AI invents Kubernetes.
            String json = """
                    {"summary":"x","items":[
                      {"rank":1,"focus":"Docker","explanation":"e","suggestedActions":["a"]},
                      {"rank":2,"focus":"Kubernetes","explanation":"kube","suggestedActions":["b"]}]}""";
            CareerImprovementPlan plan = newService(factoryReturning(json))
                    .generatePlan(analysisOf(required("Docker")));

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
            assertEquals(List.of("Docker"), focusList(plan));
        }

        @Test
        @DisplayName("AI reordering priorities → rejected → fallback")
        void reorderRejected() {
            ImprovementPriority java =
                    ImprovementPriority.requiredSkill(1, "Java");
            ImprovementPriority docker =
                    ImprovementPriority.preferredSkill(2, "Docker");
            // Deterministic order: Java (required), Docker (preferred).
            // AI flips them: Docker first (rank 1), Java second (rank 2).
            String json = """
                    {"summary":"x","items":[
                      {"rank":1,"focus":"Docker","type":"PREFERRED_SKILL","explanation":"e","suggestedActions":["a"]},
                      {"rank":2,"focus":"Java","type":"REQUIRED_SKILL","explanation":"e","suggestedActions":["b"]}]}""";
            CareerImprovementPlan plan = newService(factoryReturning(json))
                    .generatePlan(analysisOf(java, docker));

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
        }

        @Test
        @DisplayName("AI relabelling required as preferred → rejected → fallback")
        void typeManipulationRejected() {
            // Docker is REQUIRED deterministically; AI declares it PREFERRED.
            String json = """
                    {"summary":"x","items":[{"rank":1,"focus":"Docker","type":"PREFERRED_SKILL",
                       "explanation":"e","suggestedActions":["a"]}]}""";
            CareerImprovementPlan plan = newService(factoryReturning(json))
                    .generatePlan(analysisOf(required("Docker")));

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
            assertEquals(ImprovementPriority.TYPE_REQUIRED_SKILL,
                    plan.priorityItems().get(0).type());
        }

        @Test
        @DisplayName("AI removing a required priority → rejected → fallback")
        void removedRequiredRejected() {
            ImprovementPriority java = ImprovementPriority.requiredSkill(1, "Java");
            ImprovementPriority docker = ImprovementPriority.preferredSkill(2, "Docker");
            // AI omits Java (a required priority).
            String json = """
                    {"summary":"x","items":[{"rank":2,"focus":"Docker","type":"PREFERRED_SKILL",
                       "explanation":"e","suggestedActions":["a"]}]}""";
            CareerImprovementPlan plan = newService(factoryReturning(json))
                    .generatePlan(analysisOf(java, docker));

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
        }

        @Test
        @DisplayName("AI inventing an experience priority when none is a gap → rejected → fallback")
        void inventedExperienceRejected() {
            // Deterministic list has only a required skill; AI injects a fake experience item.
            String json = """
                    {"summary":"x","items":[
                      {"rank":1,"focus":"Docker","type":"REQUIRED_SKILL","explanation":"e","suggestedActions":["a"]},
                      {"rank":2,"focus":"Experience","type":"EXPERIENCE","explanation":"needs 10 years","suggestedActions":["b"]}]}""";
            CareerImprovementPlan plan = newService(factoryReturning(json))
                    .generatePlan(analysisOf(required("Docker")));

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
            assertTrue(plan.priorityItems().stream()
                    .noneMatch(i -> ImprovementPriority.TYPE_EXPERIENCE.equals(i.type())));
        }
    }

    // ─── 10–12. Edge cases ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeTests {

        @Test
        @DisplayName("no-gap analysis → empty deterministic plan, no LLM call needed")
        void noGapAnalysis() {
            CareerImprovementPlanService s =
                    new CareerImprovementPlanService(factoryReturning("{invalid}"));

            CareerGapAnalysis empty = analysisOf(); // no priorities
            CareerImprovementPlan plan = s.generatePlan(empty);

            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
            assertTrue(plan.priorityItems().isEmpty());
            assertTrue(plan.summary().contains("No improvement priorities"));
        }

        @Test
        @DisplayName("exact priority preservation: same focuses, same order, same types in the plan")
        void exactPreservation() {
            String json = """
                    {"summary":"Plan","items":[
                      {"rank":1,"focus":"Java","type":"REQUIRED_SKILL","explanation":"j","suggestedActions":["a"]},
                      {"rank":2,"focus":"Docker","type":"PREFERRED_SKILL","explanation":"d","suggestedActions":["b"]}]}""";
            ImprovementPriority java = ImprovementPriority.requiredSkill(1, "Java");
            ImprovementPriority docker = ImprovementPriority.preferredSkill(2, "Docker");
            CareerImprovementPlan plan = newService(factoryReturning(json))
                    .generatePlan(analysisOf(java, docker));

            assertEquals(CareerImprovementPlan.ORIGIN_AI, plan.origin());
            assertEquals(List.of("Java", "Docker"), focusList(plan));
            assertEquals(List.of(1, 2), plan.priorityItems().stream().map(ImprovementPlanItem::rank).toList());
        }

        @Test
        @DisplayName("repeated generation under fallback is deterministic (identical plans)")
        void repeatedFallbackIsDeterministic() {
            CareerImprovementPlan first = newService(factoryFailing())
                    .generatePlan(analysisOf(required("Docker"), preferred(2, "Microservices")));
            for (int i = 0; i < 10; i++) {
                CareerImprovementPlan again = newService(factoryFailing())
                        .generatePlan(analysisOf(required("Docker"), preferred(2, "Microservices")));
                assertEquals(first, again);
            }
        }

        @Test
        @DisplayName("null analysis → sensible empty deterministic plan")
        void nullAnalysis() {
            CareerImprovementPlanService s = newService(factoryReturning("{invalid}"));

            CareerImprovementPlan plan = s.generatePlan(null);
            assertEquals(CareerImprovementPlan.ORIGIN_DETERMINISTIC, plan.origin());
            assertTrue(plan.priorityItems().isEmpty());
        }
    }

    private List<String> focusList(CareerImprovementPlan plan) {
        return plan.priorityItems().stream().map(ImprovementPlanItem::focus).toList();
    }
}