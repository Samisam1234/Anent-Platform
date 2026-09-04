package com.agentplatform.ui.model;

/**
 * Task types supported by {@code POST /api/v1/custom/process}.
 *
 * <p>Each task type carries a LangChain4j system prompt that instructs the
 * Gemini model to format its answer as a strict JSON object matching
 * {@link StructuredAgentResult}.</p>
 */
public enum TaskType {

    /** General assistant: answers are returned in the {@code summary} field. */
    GENERAL(
            """
            You are a helpful AI assistant. Answer the user's request concisely and directly.
            Always answer in a single JSON object with exactly this shape:
            {"summary": "<your answer>"}
            Output only the JSON object — no prose, no code fences, no markdown."""),

    /** Distills the user's text into a short summary. */
    SUMMARIZE(
            """
            You are a summarization engine. Distill the user's text into a concise \
            summary of one or two sentences.
            Always answer in a single JSON object with exactly this shape:
            {"summary": "<the concise summary>"}
            Output only the JSON object — no prose, no code fences, no markdown."""),

    /** Extracts the most important points from the user's text. */
    EXTRACT_KEY_POINTS(
            """
            You are a key-point extractor. Extract the most important points from the \
            user's text into a small list of standalone items.
            Always answer in a single JSON object with exactly this shape:
            {"keyPoints": ["<point 1>", "<point 2>", ...]}
            Output only the JSON object — no prose, no code fences, no markdown."""),

    /** Classifies the user's text into one short category label. */
    CLASSIFY(
            """
            You are a text classifier. Classify the user's text into one short category \
            label and rate your confidence.
            Always answer in a single JSON object with exactly this shape:
            {"category": "<short label>", "confidence": <number between 0 and 1>}
            Output only the JSON object — no prose, no code fences, no markdown.""");

    private final String systemPrompt;

    TaskType(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    /** The LangChain4j {@code SystemMessage} content used for this task type. */
    public String systemPrompt() {
        return systemPrompt;
    }
}