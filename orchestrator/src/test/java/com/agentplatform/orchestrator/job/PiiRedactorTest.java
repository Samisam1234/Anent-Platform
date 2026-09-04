package com.agentplatform.orchestrator.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PiiRedactor — strips PII from text")
class PiiRedactorTest {

    @Nested
    @DisplayName("redact()")
    class RedactTests {

        @Test
        @DisplayName("null and blank → empty string")
        void nullAndBlank() {
            assertEquals("", PiiRedactor.redact(null));
            assertEquals("", PiiRedactor.redact(""));
            assertEquals("", PiiRedactor.redact("   "));
        }

        @Test
        @DisplayName("redacts email addresses")
        void redactsEmail() {
            String input = "Send your resume to hr@company.com or apply via jobs@example.org";
            String result = PiiRedactor.redact(input);
            assertFalse(result.contains("hr@company.com"));
            assertFalse(result.contains("jobs@example.org"));
            assertTrue(result.contains("[REDACTED]"));
            assertTrue(result.contains("Send your resume to"));
        }

        @Test
        @DisplayName("redacts phone numbers — Indian format")
        void redactsPhoneIndian() {
            String input = "Contact us at +91 98765 43210 for more details";
            String result = PiiRedactor.redact(input);
            assertFalse(result.contains("98765 43210"));
            assertTrue(result.contains("[REDACTED]"));
        }

        @Test
        @DisplayName("redacts phone numbers — US format")
        void redactsPhoneUS() {
            String input = "Call (555) 123-4567 or 555.123.4567 for inquiries";
            String result = PiiRedactor.redact(input);
            assertTrue(result.contains("[REDACTED]"));
        }

        @Test
        @DisplayName("clean text → returned unchanged")
        void cleanTextUnchanged() {
            String input = "We are looking for a Java Developer with 5 years of experience";
            assertEquals(input, PiiRedactor.redact(input));
        }

        @Test
        @DisplayName("mixed content — only PII redacted")
        void mixedContent() {
            String input = "Senior Java Dev at Google. Email: apply@google.com. Remote OK.";
            String result = PiiRedactor.redact(input);
            assertTrue(result.contains("Senior Java Dev at Google"));
            assertTrue(result.contains("Remote OK"));
            assertFalse(result.contains("apply@google.com"));
        }

        @Test
        @DisplayName("multiple emails in one string")
        void multipleEmails() {
            String input = "a@b.com and c@d.org and e@f.io";
            String result = PiiRedactor.redact(input);
            assertEquals("[REDACTED] and [REDACTED] and [REDACTED]", result);
        }
    }

    @Nested
    @DisplayName("containsPii()")
    class ContainsPiiTests {

        @Test
        @DisplayName("null and blank → false")
        void nullAndBlank() {
            assertFalse(PiiRedactor.containsPii(null));
            assertFalse(PiiRedactor.containsPii(""));
            assertFalse(PiiRedactor.containsPii("   "));
        }

        @Test
        @DisplayName("text with email → true")
        void withEmail() {
            assertTrue(PiiRedactor.containsPii("Contact hr@company.com"));
        }

        @Test
        @DisplayName("text with phone → true")
        void withPhone() {
            assertTrue(PiiRedactor.containsPii("Call +91 98765 43210"));
        }

        @Test
        @DisplayName("clean text → false")
        void cleanText() {
            assertFalse(PiiRedactor.containsPii("Java Developer with 5 years experience"));
        }

        @Test
        @DisplayName("word containing @ but not an email → false")
        void notEmail() {
            assertFalse(PiiRedactor.containsPii("user@ is not an email"));
        }
    }
}
