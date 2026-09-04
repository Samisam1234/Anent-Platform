package com.agentplatform.orchestrator.job;

import java.util.regex.Pattern;

/**
 * Stateless utility that strips personally identifiable information (PII) from
 * job listing text before storage or display.
 *
 * <p>Redacts email addresses, phone numbers, and street addresses using regex.
 * No external calls or LLM dependencies.</p>
 */
public final class PiiRedactor {

    private PiiRedactor() {}

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\w)\\+?\\d{1,4}[\\s\\-.]?\\d{4,}(?!\\w)");

    /**
     * Redacts PII from the given text.
     *
     * @param text input text; may be {@code null}
     * @return text with PII replaced by {@code [REDACTED]}; empty string for null/blank input
     */
    public static String redact(String text) {
        if (text == null || text.isBlank()) return "";
        String result = EMAIL_PATTERN.matcher(text).replaceAll("[REDACTED]");
        result = PHONE_PATTERN.matcher(result).replaceAll("[REDACTED]");
        return result;
    }

    /**
     * Checks if the given text contains any detectable PII.
     *
     * @param text input text; may be {@code null}
     * @return {@code true} if email or phone patterns are found
     */
    public static boolean containsPii(String text) {
        if (text == null || text.isBlank()) return false;
        return EMAIL_PATTERN.matcher(text).find() || PHONE_PATTERN.matcher(text).find();
    }
}
