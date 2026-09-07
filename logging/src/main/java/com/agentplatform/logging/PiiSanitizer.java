package com.agentplatform.logging;

import java.util.regex.Pattern;

/**
 * Centralized utility that sanitizes values before they reach a log line.
 *
 * <p>Protects email addresses and phone numbers (regex redaction) and caps the
 * length of arbitrary text so user prompts, resume/LLM text, and message bodies
 * cannot be logged in full. Safe, non-sensitive metadata — identifier, subject
 * length, prompt character count — is preserved via the {@code safe*} helpers so
 * diagnostics stay useful without exposing PII.</p>
 */
public final class PiiSanitizer {

    /** Default maximum length for sanitized text. */
    public static final int MAX_LOG_LENGTH = 220;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\w)\\+?\\d{1,4}[\\s\\-.]?\\d{4,}(?!\\w)");

    private static final String REDACTED = "[REDACTED]";

    private PiiSanitizer() {
    }

    /**
     * Redacts emails and phones from {@code text} and truncates the result to
     * {@value MAX_LOG_LENGTH} characters. {@code null} becomes {@code "(null)"}.
     */
    public static String sanitize(String text) {
        return sanitize(text, MAX_LOG_LENGTH);
    }

    /**
     * As {@link #sanitize(String)} but with an explicit length cap.
     */
    public static String sanitize(String text, int maxLength) {
        if (text == null) {
            return "(null)";
        }
        String redacted = PHONE_PATTERN.matcher(
                EMAIL_PATTERN.matcher(text).replaceAll(REDACTED)).replaceAll(REDACTED);
        if (maxLength > 0 && redacted.length() > maxLength) {
            return redacted.substring(0, maxLength) + "…";
        }
        return redacted;
    }

    /**
     * Returns a masked form of an email that keeps its domain for diagnostics
     * but never exposes the full local part. Invalid or blank input → {@code [REDACTED]}.
     */
    public static String safeEmail(String email) {
        if (email == null || email.isBlank()) {
            return REDACTED;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return REDACTED;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String head = local.length() <= 1 ? "*" : local.substring(0, 1);
        return head + "***@" + domain;
    }

    /**
     * Returns a masked phone number (keeps only the trailing four digits).
     * Invalid or blank input → {@code [REDACTED]}.
     */
    public static String safePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return REDACTED;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return REDACTED;
        }
        return "***" + digits.substring(digits.length() - 4);
    }

    /**
     * Returns the character length of {@code text} (or {@code 0} for null)
     * so consumers can log "how much" content was processed without logging the
     * content itself.
     */
    public static int safeLength(String text) {
        return text == null ? 0 : text.length();
    }
}