package com.agentplatform.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Phase 8.3 redaction contract: emails and phones are masked,
 * long prompt/LLM/body text is never logged in full, and safe non-sensitive
 * metadata (lengths, masked identifiers) stays usable for diagnostics.
 */
@DisplayName("PiiSanitizer — centralized pre-log redaction")
class PiiSanitizerTest {

    @Test
    @DisplayName("email addresses are redacted")
    void emailAddressIsRedacted() {
        String input = "Contact alice@example.com or bob.smith@acme.co for details.";
        String sanitized = PiiSanitizer.sanitize(input);

        assertFalse(sanitized.contains("alice@example.com"));
        assertFalse(sanitized.contains("bob.smith@acme.co"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("phone numbers are redacted")
    void phoneNumberIsRedacted() {
        String input = "Call me at +1 (555) 123-4567 today.";
        String sanitized = PiiSanitizer.sanitize(input);

        assertFalse(sanitized.contains("123-4567"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("long prompt/LLM content is truncated so it never leaks in full")
    void longContentIsTruncated() {
        String longText = "sensitive-llm-output-".repeat(50);
        String sanitized = PiiSanitizer.sanitize(longText);

        assertNotEquals(longText, sanitized);
        assertTrue(sanitized.length() <= PiiSanitizer.MAX_LOG_LENGTH + 1,
                "sanitized text must respect the length cap");
        assertTrue(sanitized.endsWith("…"), "truncation must be visible");
    }

    @Test
    @DisplayName("explicit max length cap is honoured")
    void sanitizeWithExplicitMaxLength() {
        assertEquals("abcd…", PiiSanitizer.sanitize("abcdefghij", 4));
    }

    @Test
    @DisplayName("null input becomes a safe placeholder")
    void nullBecomesPlaceholder() {
        assertEquals("(null)", PiiSanitizer.sanitize(null));
    }

    @Test
    @DisplayName("safeEmail masks the local part but keeps the domain for diagnostics")
    void safeEmailMasksLocalPartButKeepsDomain() {
        String masked = PiiSanitizer.safeEmail("alice@example.com");

        assertFalse(masked.contains("alice"));
        assertTrue(masked.endsWith("@example.com"));
        assertTrue(masked.startsWith("a***@"));
    }

    @Test
    @DisplayName("safeEmail rejects invalid input instead of leaking it")
    void safeEmailInvalidReturnsRedacted() {
        assertEquals("[REDACTED]", PiiSanitizer.safeEmail("not-an-email"));
        assertEquals("[REDACTED]", PiiSanitizer.safeEmail(null));
    }

    @Test
    @DisplayName("safePhone keeps only trailing digits")
    void safePhoneKeepsOnlyTrailingDigits() {
        String masked = PiiSanitizer.safePhone("+1 (555) 123-4567");

        assertFalse(masked.contains("123"));
        assertTrue(masked.endsWith("4567"));
    }

    @Test
    @DisplayName("safeLength exposes length as metadata without the content")
    void safeLengthReturnsMetadataLength() {
        assertEquals(4, PiiSanitizer.safeLength("abcd"));
        assertEquals(0, PiiSanitizer.safeLength(null));
    }
}