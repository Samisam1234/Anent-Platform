package com.agentplatform.orchestrator.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobNormalizer — textual normalization utilities")
class JobNormalizerTest {

    // ─── normalizeTitle ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("normalizeTitle()")
    class NormalizeTitleTests {

        @Test
        @DisplayName("null and blank input → empty string")
        void nullAndBlank() {
            assertEquals("", JobNormalizer.normalizeTitle(null));
            assertEquals("", JobNormalizer.normalizeTitle(""));
            assertEquals("", JobNormalizer.normalizeTitle("   "));
        }

        @Test
        @DisplayName("preserves letters, digits, + and #")
        void preservesSpecialChars() {
            assertEquals("c++", JobNormalizer.normalizeTitle("C++"));
            assertEquals("c#", JobNormalizer.normalizeTitle("C#"));
            assertEquals("c++ developer", JobNormalizer.normalizeTitle("C++ Developer"));
            assertEquals("senior c# engineer", JobNormalizer.normalizeTitle("Senior C# Engineer"));
        }

        @Test
        @DisplayName("strips all other non-alphanumeric characters (preserves only + and #)")
        void stripsOtherSpecial() {
            assertEquals("senior java developer", JobNormalizer.normalizeTitle("Senior Java Developer!"));
            assertEquals("full stack engineer", JobNormalizer.normalizeTitle("Full-Stack Engineer"));
            assertEquals("react js developer", JobNormalizer.normalizeTitle("React.js Developer"));
            assertEquals("mern stack developer", JobNormalizer.normalizeTitle("MERN  Stack  Developer"));
        }

        @Test
        @DisplayName("lowercases and collapses whitespace")
        void lowercasesAndCollapses() {
            assertEquals("java developer", JobNormalizer.normalizeTitle("  Java   Developer  "));
            assertEquals("spring boot engineer", JobNormalizer.normalizeTitle("Spring Boot Engineer"));
        }

        @Test
        @DisplayName("real-world job titles normalize consistently")
        void realWorldTitles() {
            assertEquals("senior software engineer java", JobNormalizer.normalizeTitle("Senior Software Engineer (Java)"));
            assertEquals("frontend developer react typescript", JobNormalizer.normalizeTitle("Frontend Developer - React/TypeScript"));
            assertEquals("devops engineer aws kubernetes", JobNormalizer.normalizeTitle("DevOps Engineer | AWS | Kubernetes"));
            assertEquals("rtl design engineer vlsi", JobNormalizer.normalizeTitle("RTL Design Engineer (VLSI)"));
            assertEquals("senior java developer", JobNormalizer.normalizeTitle("Senior Java Developer!"));
            assertEquals("full stack engineer", JobNormalizer.normalizeTitle("Full-Stack Engineer"));
            assertEquals("c++ developer", JobNormalizer.normalizeTitle("C++ Developer!"));
        }

        @Test
        @DisplayName("idempotent — normalizing twice yields same result")
        void idempotent() {
            String title = "Senior C++ Engineer (Remote)";
            String once = JobNormalizer.normalizeTitle(title);
            String twice = JobNormalizer.normalizeTitle(once);
            assertEquals(once, twice);
        }
    }

    // ─── normalizeCompany ───────────────────────────────────────────────────

    @Nested
    @DisplayName("normalizeCompany()")
    class NormalizeCompanyTests {

        @Test
        @DisplayName("null and blank → empty string")
        void nullAndBlank() {
            assertEquals("", JobNormalizer.normalizeCompany(null));
            assertEquals("", JobNormalizer.normalizeCompany(""));
            assertEquals("", JobNormalizer.normalizeCompany("   "));
        }

        @Test
        @DisplayName("lowercases, trims, collapses whitespace")
        void basicNormalization() {
            assertEquals("google", JobNormalizer.normalizeCompany("Google"));
            assertEquals("amazon web services", JobNormalizer.normalizeCompany("  Amazon   Web   Services  "));
        }

        @Test
        @DisplayName("preserves punctuation for display")
        void preservesPunctuation() {
            assertEquals("dell technologies, inc.", JobNormalizer.normalizeCompany("Dell Technologies, Inc."));
        }
    }

    // ─── normalizeLocation ──────────────────────────────────────────────────

    @Nested
    @DisplayName("normalizeLocation()")
    class NormalizeLocationTests {

        @Test
        @DisplayName("null and blank → empty string")
        void nullAndBlank() {
            assertEquals("", JobNormalizer.normalizeLocation(null));
            assertEquals("", JobNormalizer.normalizeLocation(""));
        }

        @Test
        @DisplayName("lowercases, trims, collapses whitespace")
        void basicNormalization() {
            assertEquals("hyderabad, india", JobNormalizer.normalizeLocation("Hyderabad, India"));
            assertEquals("remote", JobNormalizer.normalizeLocation("Remote"));
        }

        @Test
        @DisplayName("remote variants normalize the same")
        void remoteVariants() {
            String remote1 = JobNormalizer.normalizeLocation("Remote");
            String remote2 = JobNormalizer.normalizeLocation("remote");
            String remote3 = JobNormalizer.normalizeLocation("  Remote  ");
            assertEquals(remote1, remote2);
            assertEquals(remote2, remote3);
        }
    }

    // ─── normalizeDescription ───────────────────────────────────────────────

    @Nested
    @DisplayName("normalizeDescription()")
    class NormalizeDescriptionTests {

        @Test
        @DisplayName("null and blank → empty string")
        void nullAndBlank() {
            assertEquals("", JobNormalizer.normalizeDescription(null));
            assertEquals("", JobNormalizer.normalizeDescription(""));
        }

        @Test
        @DisplayName("strips HTML tags")
        void stripsHtml() {
            String raw = "<p>We are looking for a <strong>Java Developer</strong> to join our <em>team</em>.</p>";
            String result = JobNormalizer.normalizeDescription(raw);
            assertTrue(result.contains("Java Developer"));
            assertTrue(result.contains("team"));
            assertFalse(result.contains("<"));
        }

        @Test
        @DisplayName("collapses whitespace after tag removal")
        void collapsesWhitespace() {
            String raw = "<div>  Hello   <span>world</span>  </div>";
            assertEquals("Hello world", JobNormalizer.normalizeDescription(raw));
        }

        @Test
        @DisplayName("truncates at 2000 characters")
        void truncates() {
            String longHtml = "<p>" + "a".repeat(3000) + "</p>";
            String result = JobNormalizer.normalizeDescription(longHtml);
            assertTrue(result.length() <= 2000);
        }
    }

    // ─── buildDeduplicationKey ──────────────────────────────────────────────

    @Nested
    @DisplayName("buildDeduplicationKey()")
    class DeduplicationKeyTests {

        @Test
        @DisplayName("null job → empty string")
        void nullJob() {
            assertEquals("", JobNormalizer.buildDeduplicationKey((Job) null));
        }

        @Test
        @DisplayName("null fields treated as empty")
        void nullFields() {
            String key = JobNormalizer.buildDeduplicationKey(null, null, null);
            assertEquals("||", key);
        }

        @Test
        @DisplayName("composite key from raw fields")
        void compositeKey() {
            String key = JobNormalizer.buildDeduplicationKey("Java Developer", "Google", "Hyderabad");
            assertEquals("java developer|google|hyderabad", key);
        }

        @Test
        @DisplayName("case-insensitive — same key for different casing")
        void caseInsensitive() {
            String key1 = JobNormalizer.buildDeduplicationKey("Java Developer", "Google", "Hyderabad");
            String key2 = JobNormalizer.buildDeduplicationKey("java developer", "GOOGLE", "HYDERABAD");
            assertEquals(key1, key2);
        }

        @Test
        @DisplayName("whitespace-insensitive — same key for extra spaces")
        void whitespaceInsensitive() {
            String key1 = JobNormalizer.buildDeduplicationKey("Java Developer", "Google", "Hyderabad");
            String key2 = JobNormalizer.buildDeduplicationKey("  Java   Developer  ", "  Google  ", "  Hyderabad  ");
            assertEquals(key1, key2);
        }

        @Test
        @DisplayName("from Job record")
        void fromJobRecord() {
            Job job = new Job(
                    "mock-001",
                    "Java Developer",
                    "TechCorp",
                    "Hyderabad",
                    "Build things",
                    List.of("Java"),
                    List.of(),
                    "2-4 years",
                    "FULL_TIME",
                    "2026-01-15",
                    "MOCK_SOURCE",
                    null,
                    "MOCK",
                    null
            );
            String key = JobNormalizer.buildDeduplicationKey(job);
            assertEquals("java developer|techcorp|hyderabad", key);
        }

        @Test
        @DisplayName("different titles → different keys")
        void differentTitles() {
            String key1 = JobNormalizer.buildDeduplicationKey("Java Developer", "Google", "Remote");
            String key2 = JobNormalizer.buildDeduplicationKey("Python Developer", "Google", "Remote");
            assertNotEquals(key1, key2);
        }

        @Test
        @DisplayName("idempotent — building key twice yields same result")
        void idempotent() {
            Job job = new Job(
                    "mock-001", "Senior C++ Engineer", "Meta", "Menlo Park, CA",
                    "desc", List.of("C++"), List.of(), "5+ years", "FULL_TIME",
                    "2026-01-01", "MOCK", null, "MOCK", null
            );
            String once = JobNormalizer.buildDeduplicationKey(job);
            String twice = JobNormalizer.buildDeduplicationKey(job);
            assertEquals(once, twice);
        }
    }
}
