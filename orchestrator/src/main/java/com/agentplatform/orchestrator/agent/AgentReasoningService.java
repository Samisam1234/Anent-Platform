package com.agentplatform.orchestrator.agent;

import com.agentplatform.core.ai.AiErrorClassifier;
import com.agentplatform.core.config.OllamaChatModelFactory;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.ImprovementPriority;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Central, controlled AI reasoning layer for the career agents.
 *
 * <p>LEGAL REASONING ONLY: the LLM may explain/summarize structured deterministic
 * facts, never compute or invent them. Deterministic services remain the source of
 * truth (matching, gap calculation, ATS readiness, filtering, identity, approval).
 * This service is the single point of LLM invocation for agents — no raw
 * {@link ChatModel} is exposed throughout the agent layer.</p>
 *
 * <p>A single shared Ollama model is used (the factory's configured default,
 * {@code llama3.2:3b}) via the existing {@link OllamaChatModelFactory}. One AI call
 * at a time, bounded by the per-run budget in {@link AgentContext} (max
 * {@link CareerAgentOrchestrator#MAX_AI_CALLS}).</p>
 *
 * <p>Every model response is validated against authoritative topics; if the model
 * invents skills/employers/experience/years/URLs or anything unsupported, or if
 * Ollama is unavailable, the response times out, or is empty/malformed, a
 * deterministic fallback is returned instead.</p>
 */
@Service
public class AgentReasoningService {

    private static final Logger log = LoggerFactory.getLogger(AgentReasoningService.class);

    static final String SYSTEM_PROMPT = """
            You are a careful career analysis assistant.
            Use ONLY the supplied deterministic facts. Never invent anything.
            Rules (mandatory):
            - Use only supplied candidate facts.
            - Never invent skills, experience, projects, employers, certifications, years.
            - Never invent job requirements, salary, recruiter/contact details, or URLs.
            - Never change the deterministic priority order.
            - Never claim an application was submitted or an email was sent.
            - You may only explain the listed authoritative topics.
            Return ONLY a valid JSON object, no markdown, no code fences:
            {
              "summary": "one or two sentences",
              "explanations": [
                { "topic": "<one authoritative topic exactly>", "explanation": "...", }
              ]
            }
            """;

    private final OllamaChatModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    public AgentReasoningService(OllamaChatModelFactory modelFactory, ObjectMapper objectMapper) {
        this.modelFactory = modelFactory;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Produces controlled reasoning for an agent based on the deterministic facts
     * in {@code context}. Falls back deterministically when LLM is unavailable or
     * its output is invalid, and never throws.
     */
    public AgentReasoningResult reason(AgentType agentType, AgentContext context, String task) {
        if (agentType == null || context == null) {
            return fallback(agentType, context, task);
        }

        // Resource control: no call slot → deterministic behavior only.
        if (!context.consumeAiCall()) {
            log.info("AI reasoning budget exhausted for {}; using deterministic reasoning", agentType);
            return fallback(agentType, context, task);
        }

        ReasonInput input;
        try {
            input = buildInput(agentType, context);
        } catch (Exception e) {
            return fallback(agentType, context, task);
        }
        if (input.topics().isEmpty()) {
            return fallback(agentType, context, task);
        }

        String prompt = buildPrompt(input, task);
        String raw;
        try {
            ChatModel model = modelFactory.chatModel(null); // single shared default model
            raw = model.chat(prompt);
        } catch (Exception e) {
            AiErrorClassifier.Failure failure = AiErrorClassifier.classify(e, "Ollama");
            log.warn("Agent AI reasoning unavailable for {} ({}); deterministic fallback", agentType, failure.message());
            return fallback(agentType, context, task);
        }

        if (raw == null || raw.isBlank()) {
            log.warn("Agent AI reasoning returned an empty response for {}; fallback", agentType);
            return fallback(agentType, context, task);
        }

        try {
            return validateAndParse(agentType, raw, input);
        } catch (Exception e) {
            log.warn("Agent AI reasoning output invalid for {} ({}); fallback", agentType, safeMessage(e));
            return fallback(agentType, context, task);
        }
    }

    // ─── Authoritative input construction ─────────────────────────────────────

    private ReasonInput buildInput(AgentType agentType, AgentContext context) {
        Set<String> topics = new LinkedHashSet<>();
        StringBuilder facts = new StringBuilder();

        switch (agentType) {
            case RESUME -> {
                CandidateProfile p = context.candidateProfile();
                if (p == null) {
                    return new ReasonInput(List.of(), "");
                }
                if (p.name() != null && !p.name().isBlank()) {
                    facts.append("Candidate name: ").append(p.name()).append('\n');
                }
                facts.append("Skills: ").append(topics(facts, p.softwareSkills())).append('\n');
                topics.addAll(normalizedNonNull(p.softwareSkills()));
                topics.addAll(normalizedNonNull(p.hardwareSkills()));
                topics.addAll(normalizedNonNull(p.skills()));
                if (!p.projects().isEmpty()) {
                    facts.append("Projects present: yes\n");
                }
                if (!p.experience().isEmpty()) {
                    facts.append("Experience entries present: yes\n");
                }
            }
            case CAREER_ADVISOR -> {
                CareerGapAnalysis gap = context.careerGapAnalysis();
                if (gap == null) {
                    return new ReasonInput(List.of(), "");
                }
                facts.append("Overall gap severity: ").append(gap.overallGapSeverity()).append('\n');
                facts.append("Matched required skills: ").append(join(gap.matchedRequiredSkills().stream()
                        .map(s -> s.canonicalSkill()).toList())).append('\n');
                facts.append("Missing required skills: ").append(join(gap.missingRequiredSkills().stream()
                        .map(s -> s.canonicalSkill()).toList())).append('\n');
                facts.append("Missing preferred skills: ").append(join(gap.missingPreferredSkills().stream()
                        .map(s -> s.canonicalSkill()).toList())).append('\n');
                topics.addAll(gap.matchedRequiredSkills().stream().map(s -> s.canonicalSkill()).toList());
                topics.addAll(gap.missingRequiredSkills().stream().map(s -> s.canonicalSkill()).toList());
                topics.addAll(gap.missingPreferredSkills().stream().map(s -> s.canonicalSkill()).toList());
                if (gap.improvementPriorities() != null) {
                    facts.append("Improvement priorities in order: ");
                    List<ImprovementPriority> list = new ArrayList<>(gap.improvementPriorities());
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) {
                            facts.append(", ");
                        }
                        facts.append(list.get(i).rank()).append(". ").append(list.get(i).focus());
                        topics.add(list.get(i).focus());
                    }
                    facts.append('\n');
                }
            }
            case APPLICATION_ADVISOR -> {
                TailoredResumeDraft draft = context.tailoredDraft();
                if (draft == null) {
                    return new ReasonInput(List.of(), "");
                }
                CandidateProfile p = context.candidateProfile();
                topics.addAll(draft.orderedSkills());
                topics.addAll(context.careerGapAnalysis() != null
                        ? context.careerGapAnalysis().missingRequiredSkills().stream()
                        .map(s -> s.canonicalSkill()).toList() : List.of());
                if (draft.professionalSummary() != null && !draft.professionalSummary().isBlank()) {
                    facts.append("Professional summary: ").append(draft.professionalSummary()).append('\n');
                }
                facts.append("Ordered skills: ").append(join(draft.orderedSkills())).append('\n');
                if (p != null && !p.projects().isEmpty()) {
                    facts.append("Highlighted projects present: yes\n");
                }
            }
            default -> {
                // Job discovery / matching never request AI reasoning.
                return new ReasonInput(List.of(), "");
            }
        }

        return new ReasonInput(List.copyOf(topics), facts.toString());
    }

    private static String topics(StringBuilder facts, List<String> list) {
        return join(list);
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(none)";
        }
        return String.join(", ", values);
    }

    private static List<String> normalizedNonNull(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim());
            }
        }
        return out;
    }

    // ─── Prompt building ──────────────────────────────────────────────────────

    private String buildPrompt(ReasonInput input, String task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(task == null || task.isBlank() ? "Explain the career analysis." : task).append('\n');
        sb.append("Authoritative facts (only these may be used):\n").append(input.facts());
        sb.append("\nAuthoritative topics (you may ONLY explain these, using these exact labels):\n");
        for (String t : input.topics()) {
            sb.append("- ").append(t).append('\n');
        }
        sb.append('\n').append(SYSTEM_PROMPT);
        return sb.toString();
    }

    // ─── Validation + parse ───────────────────────────────────────────────────

    /**
     * Parses the JSON response and rejects it if any explanation topics (or the
     * summary) introduce content outside the authoritative topic set.
     */
    private AgentReasoningResult validateAndParse(AgentType agentType, String raw, ReasonInput input) {
        String json = extractJsonObject(raw);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI response could not be parsed as JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("AI response is not a JSON object");
        }
        String summary = text(root.get("summary"));
        JsonNode explanationsNode = root.get("explanations");
        List<Explanation> explanations = new ArrayList<>();
        if (explanationsNode != null && explanationsNode.isArray()) {
            for (JsonNode node : explanationsNode) {
                if (node == null || !node.isObject()) {
                    throw new IllegalArgumentException("AI explanation is malformed");
                }
                String topic = text(node.get("topic"));
                String explanation = text(node.get("explanation"));
                if (topic.isEmpty()) {
                    throw new IllegalArgumentException("AI explanation has no topic");
                }
                if (!containsTopic(input.topics(), topic)) {
                    throw new IllegalArgumentException("AI introduced unsupported topic: " + topic);
                }
                explanations.add(new Explanation(trimTo(topic, 200), description(explanation)));
            }
        }
        if (explanations.isEmpty()) {
            throw new IllegalArgumentException("AI produced no valid explanations");
        }
        return new AgentReasoningResult(agentType,
                trimTo(summary.isEmpty() ? defaultSummary(agentType, input) : summary, 400),
                explanations, AgentReasoningResult.ORIGIN_AI);
    }

    private static boolean containsTopic(List<String> topics, String candidate) {
        String c = normalize(candidate);
        if (c.isEmpty()) {
            return false;
        }
        for (String t : topics) {
            if (normalize(t).equals(c)) {
                return true;
            }
        }
        return false;
    }

    private String defaultSummary(AgentType agentType, ReasonInput input) {
        return AgencyDefaults.summary(agentType, input.topics());
    }

    private static String description(String s) {
        if (s == null || s.isBlank()) {
            return "Based on the supplied deterministic information.";
        }
        return trimTo(s, 700);
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || start >= end) {
            throw new IllegalArgumentException("AI response does not contain a JSON object");
        }
        return text.substring(start, end + 1);
    }

    private static String text(JsonNode node) {
        return node != null && node.isValueNode() ? node.asText().trim() : "";
    }

    private static String trimTo(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int keep = Math.max(0, max - 1);
        return s.substring(0, keep) + "…";
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private static String safeMessage(Object value) {
        String s = value == null ? "unknown" : value.toString();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }

    // ─── Deterministic fallback ───────────────────────────────────────────────

    private AgentReasoningResult fallback(AgentType agentType, AgentContext context, String task) {
        return new AgentReasoningResult(agentType,
                AgencyDefaults.summary(agentType, new ArrayList<>()),
                AgencyDefaults.explanations(agentType, context),
                AgentReasoningResult.ORIGIN_DETERMINISTIC);
    }

    /** Immutable structured input for a reasoning call. */
    private record ReasonInput(List<String> topics, String facts) {
    }

    /** Package-private helper so deterministic fallbacks are shared and tested. */
    private static final class AgencyDefaults {
        static String summary(AgentType agentType, List<String> topics) {
            if (agentType == null) {
                return "";
            }
            switch (agentType) {
                case RESUME:
                    return "Deterministic resume profile captured from the supplied document.";
                case CAREER_ADVISOR:
                    return "Deterministic career-gap analysis and improvement priorities for the target job.";
                case APPLICATION_ADVISOR:
                    return "Deterministic ATS tailoring and application draft prepared from the candidate profile.";
                default:
                    return "";
            }
        }

        static List<Explanation> explanations(AgentType agentType, AgentContext context) {
            List<Explanation> out = new ArrayList<>();
            if (context == null || agentType == null) {
                return List.copyOf(out);
            }
            switch (agentType) {
                case RESUME -> {
                    CandidateProfile p = context.candidateProfile();
                    if (p != null && !p.skills().isEmpty()) {
                        out.add(new Explanation("Skills", "The candidate profile lists "
                                + p.skills().size() + " canonical skills for review."));
                    }
                }
                case CAREER_ADVISOR -> {
                    CareerGapAnalysis gap = context.careerGapAnalysis();
                    if (gap != null) {
                        if (!gap.missingRequiredSkills().isEmpty()) {
                            out.add(new Explanation("Missing required skills",
                                    "Deterministic career-gap analysis identified required skills missing from the profile."));
                        }
                        if (!gap.improvementPriorities().isEmpty()) {
                            out.add(new Explanation("Improvement priorities",
                                    "Deterministic improvement priorities are ordered by the career-gap analysis."));
                        }
                    }
                }
                case APPLICATION_ADVISOR -> {
                    TailoredResumeDraft draft = context.tailoredDraft();
                    if (draft != null && !draft.orderedSkills().isEmpty()) {
                        out.add(new Explanation("Tailored resume",
                                "The tailored draft orders existing candidate skills by job relevance."));
                    }
                }
                default -> { /* job discovery / matching never reason via AI */ }
            }
            return List.copyOf(out);
        }
    }
}
