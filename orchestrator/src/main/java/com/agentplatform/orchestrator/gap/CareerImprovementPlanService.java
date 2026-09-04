package com.agentplatform.orchestrator.gap;

import com.agentplatform.core.ai.AiErrorClassifier;
import com.agentplatform.core.config.OllamaChatModelFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates an AI-assisted {@link CareerImprovementPlan} on top of the deterministic
 * {@link CareerGapAnalysis} + {@link ImprovementPriority} results (Steps 4.1–4.2).
 *
 * <p>The deterministic layer is authoritative; the LLM may only explain and organize
 * the supplied priorities. A strict hallucination guard reconciles the model's
 * response back onto the deterministic priority list, and any failure (unavailable
 * Ollama, timeout, empty/malformed output, or an invalid/hallucinated response) falls
 * back to a purely deterministic plan so the user always receives useful output.</p>
 *
 * <p>Uses the existing Ollama infrastructure ({@link OllamaChatModelFactory}) with the
 * configured default model (e.g. {@code llama3.2:3b}). No new AI framework, no vector
 * storage, no external learning data.</p>
 */
@Service
public class CareerImprovementPlanService {

    private static final Logger log = LoggerFactory.getLogger(CareerImprovementPlanService.class);

    static final String FALLBACK_SUMMARY =
            "Deterministic career improvement priorities for the target job.";

    static final String NO_GAP_SUMMARY =
            "No improvement priorities — the candidate profile already meets the target job requirements.";

    private static final String PROMPT = """
            You are a career improvement advisor.

            Use ONLY the supplied candidate facts, target-job requirements, and
            deterministic improvement priorities.

            Do not invent facts.
            Do not add skills that are not present in the priority list.
            Do not change priority ordering.
            Do not claim the candidate has experience that is not supplied.
            For each priority, explain:
            - why it matters for the target job
            - what practical capability the candidate should develop
            - a small set of concrete practice actions
            Do not provide external URLs.
            Do not recommend specific paid courses unless explicitly supplied by the application.

            Return ONLY a valid JSON object — no markdown, no code fences, no extra text.
            Use this exact shape:
            {
              "summary": "one short sentence summarizing the overall plan",
              "items": [
                {
                  "rank": <the deterministic 1-based rank>,
                  "focus": "<the exact focus label from the priority>",
                  "explanation": "why it matters and what capability to develop",
                  "suggestedActions": ["a concrete practice action", "another action"]
                }
              ]
            }

            The number of items and every focus must match the deterministic priority
            list exactly, in the same order.

            Deterministic priorities:
            %s
            """;

    private final OllamaChatModelFactory ollamaChatModelFactory;
    private final ObjectMapper objectMapper;

    public CareerImprovementPlanService(OllamaChatModelFactory ollamaChatModelFactory) {
        this.ollamaChatModelFactory = ollamaChatModelFactory;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generates a career improvement plan for the given analysis, following the
     * authoritative-then-AI-then-fallback sequence described in the class docs.
     */
    public CareerImprovementPlan generatePlan(CareerGapAnalysis analysis) {
        if (analysis == null) {
            analysis = emptyAnalysis();
        }
        List<ImprovementPriority> priorities = analysis.improvementPriorities();

        // Nothing to improve → a deterministic, empty plan (no pointless LLM call).
        if (priorities == null || priorities.isEmpty()) {
            return new CareerImprovementPlan(analysis.jobId(), analysis.candidateId(),
                    NO_GAP_SUMMARY, CareerImprovementPlan.ORIGIN_DETERMINISTIC, List.of());
        }

        try {
            ParsedAiPlan aiPlan = parse(callLlm(buildPrompt(priorities)));
            return reconcileToAiPlan(analysis, priorities, aiPlan);
        } catch (Exception e) {
            log.warn("AI career improvement plan unavailable ({}); returning the deterministic fallback",
                    safeMessage(e));
            return fallback(analysis, priorities);
        }
    }

    // ─── LLM call ───────────────────────────────────────────────────────────

    private String callLlm(String prompt) {
        try {
            ChatModel model = ollamaChatModelFactory.chatModel(null);
            return model.chat(prompt);
        } catch (Exception e) {
            AiErrorClassifier.Failure failure = AiErrorClassifier.classify(e, "Ollama");
            log.error("Ollama LLM call failed during career plan generation: {}",
                    safeMessage(failure));
            throw new IllegalArgumentException(failure.message(), e);
        }
    }

    private String buildPrompt(List<ImprovementPriority> priorities) {
        StringBuilder sb = new StringBuilder();
        for (ImprovementPriority p : priorities) {
            sb.append("Priority ").append(p.rank()).append(":\n")
                    .append("focus = ").append(p.focus()).append('\n')
                    .append("type = ").append(p.type()).append('\n')
                    .append("reason = ").append(p.reason()).append('\n').append('\n');
        }
        return String.format(PROMPT, sb.toString().trim());
    }

    // ─── Parse ──────────────────────────────────────────────────────────────

    private ParsedAiPlan parse(String llm) {
        if (llm == null || llm.isBlank()) {
            throw new IllegalArgumentException("AI returned an empty response");
        }
        String json = extractJsonObject(llm);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI returned malformed JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("AI response is not a JSON object");
        }
        String summary = textValue(root.get("summary"));
        JsonNode itemsNode = root.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
            throw new IllegalArgumentException("AI response has no items array");
        }
        List<ParsedItem> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            if (item == null || !item.isObject()) {
                throw new IllegalArgumentException("AI response has a malformed item");
            }
            int rank = item.has("rank") && item.get("rank").canConvertToInt()
                    ? item.get("rank").asInt() : -1;
            String focus = textValue(item.get("focus"));
            if (focus.isEmpty()) {
                throw new IllegalArgumentException("AI item has no focus");
            }
            String type = textValue(item.get("type"));
            String explanation = textValue(item.get("explanation"));
            List<String> actions = new ArrayList<>();
            JsonNode acts = item.get("suggestedActions");
            if (acts != null && acts.isArray()) {
                for (JsonNode a : acts) {
                    String s = textValue(a);
                    if (!s.isEmpty()) {
                        actions.add(s);
                    }
                }
            }
            items.add(new ParsedItem(rank, focus, type, explanation, actions));
        }
        return new ParsedAiPlan(summary, items);
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || start >= end) {
            throw new IllegalArgumentException("AI response does not contain a JSON object");
        }
        return text.substring(start, end + 1);
    }

    // ─── Hallucination guard / reconciliation ───────────────────────────────

    /**
     * Reconciles the AI response onto the authoritative deterministic priorities.
     * Rejects (throws) on: new skills, removed priorities, reordering,
     * type/rank manipulation, or a count mismatch. On rejection the caller falls back.
     */
    private CareerImprovementPlan reconcileToAiPlan(CareerGapAnalysis analysis,
                                                    List<ImprovementPriority> deterministic,
                                                    ParsedAiPlan aiPlan) {
        List<ParsedItem> items = aiPlan.items();

        // No new skills, no removals: the AI item set must exactly equal the
        // deterministic focus set (case-insensitive), and counts must match one-to-one.
        if (items.size() != deterministic.size()) {
            throw new IllegalArgumentException("AI item count does not match the deterministic priorities");
        }
        Map<String, ParsedItem> byFocus = new LinkedHashMap<>();
        for (ParsedItem item : items) {
            byFocus.put(normalize(item.focus()), item);
        }
        for (ImprovementPriority p : deterministic) {
            if (!byFocus.containsKey(normalize(p.focus()))) {
                throw new IllegalArgumentException("AI dropped or renamed a priority: " + p.focus());
            }
        }
        if (byFocus.keySet().size() != deterministic.size()) {
            throw new IllegalArgumentException("AI reordered or duplicated a priority focus");
        }

        // Type manipulation guard: an AI-declared type must agree with the
        // deterministic type (e.g. required must not be relabelled preferred).
        for (ImprovementPriority p : deterministic) {
            ParsedItem ai = byFocus.get(normalize(p.focus()));
            if (!ai.type().isEmpty() && !ai.type().equals(p.type())) {
                throw new IllegalArgumentException(
                        "AI changed the type of a priority: " + p.focus());
            }
        }

        // Reordering guard: the AI must have kept the deterministic order. Traversing
        // the deterministic priorities in order, their AI-provided ranks must be
        // strictly increasing — otherwise the model reordered them.
        int lastAiRank = 0;
        for (ImprovementPriority p : deterministic) {
            ParsedItem ai = byFocus.get(normalize(p.focus()));
            int aiRank = ai.rank();
            if (aiRank > 0 && aiRank <= lastAiRank) {
                throw new IllegalArgumentException("AI reordered the priorities");
            }
            lastAiRank = Math.max(lastAiRank, aiRank);
        }

        // Emit items strictly in deterministic order with deterministic rank/type/
        // focus/reason; only explanation + actions come from the AI.
        List<ImprovementPlanItem> planItems = new ArrayList<>();
        for (ImprovementPriority p : deterministic) {
            ParsedItem ai = byFocus.get(normalize(p.focus()));
            planItems.add(new ImprovementPlanItem(
                    p.rank(), p.focus(), p.type(), p.reason(),
                    ai.explanation(), ai.actions()));
        }

        return new CareerImprovementPlan(analysis.jobId(), analysis.candidateId(),
                aiPlan.summary(), CareerImprovementPlan.ORIGIN_AI, planItems);
    }

    // ─── Deterministic fallback ─────────────────────────────────────────────

    private CareerImprovementPlan fallback(CareerGapAnalysis analysis,
                                           List<ImprovementPriority> priorities) {
        List<ImprovementPlanItem> items = new ArrayList<>();
        for (ImprovementPriority p : priorities) {
            items.add(new ImprovementPlanItem(
                    p.rank(), p.focus(), p.type(), p.reason(),
                    p.description(), deterministicActions(p)));
        }
        return new CareerImprovementPlan(analysis.jobId(), analysis.candidateId(),
                FALLBACK_SUMMARY, CareerImprovementPlan.ORIGIN_DETERMINISTIC, items);
    }

    /**
     * Deterministic, type-aware practice actions. Never invents job-specific
     * requirements, experience numbers, courses, URLs or certifications.
     */
    static List<String> deterministicActions(ImprovementPriority p) {
        if (ImprovementPriority.TYPE_EXPERIENCE.equals(p.type())) {
            return List.of(
                    "Seek professional or internship roles that grow total professional experience.",
                    "Express prior scope with explicit durations so the experience level is verifiable.");
        }
        String f = p.focus();
        boolean preferred = ImprovementPriority.TYPE_PREFERRED_SKILL.equals(p.type());
        List<String> actions = new ArrayList<>();
        actions.add("Review the fundamentals of " + f + ".");
        actions.add("Practice by building a small project that uses " + f + ".");
        actions.add(preferred
                ? "Add " + f + " to the candidate profile once it is demonstrable."
                : "Build a demonstrable example of " + f + " to close the gap for the target job.");
        return List.copyOf(actions);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private static String textValue(JsonNode node) {
        return node != null && node.isValueNode() ? node.asText().trim() : "";
    }

    private static String safeMessage(Object value) {
        if (value == null) {
            return "unknown";
        }
        String s = value.toString();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }

    private static CareerGapAnalysis emptyAnalysis() {
        return new CareerGapAnalysis(null, null, List.of(), List.of(), List.of(), List.of(),
                new ExperienceGap(null, null, null, false),
                com.agentplatform.orchestrator.matching.CareerTrack.UNKNOWN,
                com.agentplatform.orchestrator.matching.CareerTrack.UNKNOWN,
                false, GapSeverity.NO_GAP, List.of());
    }

    /** Lightweight parsed shape of the AI response. */
    private record ParsedAiPlan(String summary, List<ParsedItem> items) {
    }

    /** One parsed item from the AI response. */
    private record ParsedItem(int rank, String focus, String type,
                              String explanation, List<String> actions) {
    }
}