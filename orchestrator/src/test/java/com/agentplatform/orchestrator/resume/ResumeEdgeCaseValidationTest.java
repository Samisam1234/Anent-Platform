package com.agentplatform.orchestrator.resume;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resume parsing edge cases and anti-fabrication validation (Phase 2 Step 2.3 §3–§6).
 *
 * <p>Every test runs the deterministic parser (same layer the extraction feeds), so these
 * cover the complete PDF/DOCX → text → parse pipeline's behaviour on odd inputs.</p>
 */
@DisplayName("Resume edge cases — formatting, fabrication resistance, empty/noisy input")
class ResumeEdgeCaseValidationTest {

    private final DeterministicCandidateProfileBuilder builder = new DeterministicCandidateProfileBuilder();

    private CandidateProfile build(String resume) {
        return builder.build(resume);
    }

    // ─── 3. Formatting edge cases ─────────────────────────────────────────────

    @Nested
    @DisplayName("Heading formatting variants")
    class HeadingVariantTests {

        @Test
        @DisplayName("uppercase heading with trailing colon is recognized")
        void uppercaseHeading() {
            CandidateProfile p = build("""
                    Alex Morgan
                    TECHNICAL SKILLS:
                    Java, Spring Boot
                    """);
            assertTrue(p.skills().contains("Java"));
            assertEquals(ResumeEvidence.SourceSection.SKILLS, evidenceSection(p, "Java"));
        }

        @Test
        @DisplayName("heading with trailing dash is recognized")
        void headingWithDash() {
            CandidateProfile p = build("""
                    Alex Morgan
                    Technical Skills -
                    Java, Spring Boot
                    """);
            assertTrue(p.skills().contains("Spring Boot"));
        }

        @Test
        @DisplayName("numbered heading is recognized")
        void numberedHeading() {
            CandidateProfile p = build("""
                    Alex Morgan
                    1. Technical Skills
                    Java, Spring Boot
                    """);
            assertTrue(p.skills().contains("Java"));
        }

        @Test
        @DisplayName("bulleted heading is recognized")
        void bulletedHeading() {
            CandidateProfile p = build("""
                    Alex Morgan
                    • Technical Skills
                    Java, Spring Boot
                    """);
            assertTrue(p.skills().contains("Spring Boot"));
        }

        @Test
        @DisplayName("heading followed immediately by inline colon content is recognized")
        void inlineHeadingContent() {
            CandidateProfile p = build("""
                    Alex Morgan
                    Skills: Java, Spring Boot, Docker
                    """);
            assertTrue(p.skills().contains("Java"));
            assertTrue(p.skills().contains("Docker"));
            assertEquals(ResumeEvidence.SourceSection.SKILLS, evidenceSection(p, "Java"));
        }

        @Test
        @DisplayName("heading on its own line then blank lines then content")
        void headingThenContent() {
            CandidateProfile p = build("""
                    Alex Morgan

                    Skills


                    Java, Spring Boot
                    """);
            assertTrue(p.skills().contains("Java"));
            assertTrue(p.skills().contains("Spring Boot"));
        }

        @Test
        @DisplayName("mixed-case heading is recognized")
        void mixedCaseHeading() {
            CandidateProfile p = build("""
                    Alex Morgan
                    sKiLlS
                    Java, Spring Boot
                    """);
            assertTrue(p.skills().contains("Java"));
        }

        @Test
        @DisplayName("heading with extra internal spacing still resolves")
        void extraWhitespaceInLine() {
            CandidateProfile p = build("""
                    Alex Morgan
                       Technical    Skills
                    Java, Spring Boot
                    """);
            assertTrue(p.skills().contains("Java"));
        }
    }

    @Nested
    @DisplayName("Skill separators and aliases")
    class SeparatorTests {

        @Test
        @DisplayName("comma-separated skills all captured")
        void commaSeparated() {
            CandidateProfile p = build("""
                    Sam Lee
                    Skills: Java, Spring Boot, Docker
                    """);
            assertTrue(p.skills().contains("Java"));
            assertTrue(p.skills().contains("Spring Boot"));
            assertTrue(p.skills().contains("Docker"));
        }

        @Test
        @DisplayName("semicolon-separated skills all captured")
        void semicolonSeparated() {
            CandidateProfile p = build("""
                    Sam Lee
                    Skills: Java; Spring Boot; Docker
                    """);
            assertTrue(p.skills().contains("Java"));
            assertTrue(p.skills().contains("Spring Boot"));
            assertTrue(p.skills().contains("Docker"));
        }

        @Test
        @DisplayName("bullet-separated skills all captured")
        void bulletSeparated() {
            CandidateProfile p = build("""
                    Sam Lee
                    Skills
                    • Java
                    • Spring Boot
                    • Docker
                    """);
            assertTrue(p.skills().contains("Java"));
            assertTrue(p.skills().contains("Spring Boot"));
            assertTrue(p.skills().contains("Docker"));
        }

        @Test
        @DisplayName("duplicate skills and aliases collapse into canonical entries")
        void duplicatesAndAliases() {
            CandidateProfile p = build("""
                    Sam Lee
                    Skills: Java, Core Java, SPRINGBOOT, spring-boot, PostgreSQL, postgres
                    """);
            assertEquals(1, p.skills().stream().filter(s -> s.equals("Java")).count());
            assertEquals(1, p.skills().stream().filter(s -> s.equals("Spring Boot")).count());
            assertEquals(1, p.skills().stream().filter(s -> s.equals("PostgreSQL")).count());
        }

        @Test
        @DisplayName("single-letter explicit skill C is preserved alongside CSS")
        void singleLetterCAndCss() {
            CandidateProfile p = build("""
                    Sam Lee
                    Skills: C, C++, CSS
                    """);
            assertTrue(p.skills().contains("C"));
            assertTrue(p.skills().contains("C++"));
            assertTrue(p.skills().contains("CSS"));
        }
    }

    // ─── 4. No-fabrication / false-positive protection ───────────────────────

    @Nested
    @DisplayName("Fabrication resistance")
    class FabricationResistanceTests {

        @Test
        @DisplayName("non-taxonomy words never become canonical skills")
        void nonTaxonomyWordsOmitted() {
            CandidateProfile p = build("""
                    Ivy Smith
                    Skills
                    Cooking, Gardening, Painting, Yoga, Cooking recipes
                    """);
            assertFalse(p.skills().contains("Cooking"));
            assertFalse(p.skills().contains("Gardening"));
            assertTrue(p.skills().isEmpty());
        }

        @Test
        @DisplayName("prose sentences do not leak into the skills list")
        void proseDoesNotLeak() {
            CandidateProfile p = build("""
                    Ivy Smith
                    Skills
                    Java
                    Experience
                    Software Engineer at Acme leading cross-functional agile teams
                    """);
            // Only taxonomy-backed skills appear.
            assertTrue(p.skills().contains("Java"));
            assertFalse(p.skills().stream().anyMatch(s -> s.contains("Acme")
                    || s.contains("agile") || s.contains("leading")));
        }

        @Test
        @DisplayName("CAN protocol is not inferred from the word candidate")
        void canVsCandidate() {
            CandidateProfile p = build("""
                    Liam Brown
                    Experience
                    Managed the candidate hiring pipeline and applicant lifecycle
                    """);
            assertFalse(p.skills().contains("CAN"));
        }

        @Test
        @DisplayName("C is not inferred from CSS")
        void cVsCss() {
            CandidateProfile p = build("""
                    Liam Brown
                    Skills: CSS
                    """);
            assertFalse(p.skills().contains("C"));
            assertTrue(p.skills().contains("CSS"));
        }

        @Test
        @DisplayName("Java is not inferred from JavaScript")
        void javaVsJavaScript() {
            CandidateProfile p = build("""
                    Liam Brown
                    Skills: JavaScript
                    """);
            assertFalse(p.skills().contains("Java"));
            assertTrue(p.skills().contains("JavaScript"));
        }

        @Test
        @DisplayName("SQL is not inferred from words that merely contain the substring")
        void sqlNotFromSubstrings() {
            CandidateProfile p = build("""
                    Liam Brown
                    Experience
                    Built a jobsqlservice and constraining constraint engine
                    """);
            assertFalse(p.skills().contains("SQL"));
        }

        @Test
        @DisplayName("FPGA-related skills are not fabricated when absent")
        void noFpgaWhenAbsent() {
            CandidateProfile p = build("""
                    Liam Brown
                    Skills: Java, Spring Boot
                    """);
            assertFalse(p.skills().contains("FPGA"));
            assertFalse(p.skills().contains("Verilog"));
            assertFalse(p.skills().contains("RTL Design"));
        }

        @Test
        @DisplayName("job title and company words never appear as skills")
        void noCompanyTitleFabrication() {
            CandidateProfile p = build("""
                    Maya Patel
                    Experience
                    Senior Software Engineer at Google, worked at Microsoft as an Architect
                    """);
            // Multi-word prose containing a comma is split; only taxonomy skills remain.
            assertFalse(p.skills().stream().anyMatch(s ->
                    s.contains("Google") || s.contains("Microsoft")
                            || s.contains("engineer") || s.contains("architect")));
        }
    }

    // ─── 5. Empty / damaged input ─────────────────────────────────────────────

    @Nested
    @DisplayName("Empty and damaged input")
    class EmptyInputTests {

        @Test
        @DisplayName("null resume text fails safely without NPE")
        void nullText() {
            CandidateProfile p = build(null);
            assertNotNull(p);
            assertNotNull(p.skills());
            assertTrue(p.skills().isEmpty());
        }

        @Test
        @DisplayName("blank and whitespace-only resumes produce a clean empty profile")
        void blankAndWhitespace() {
            for (String text : new String[]{"", "   ", "\n\n\n", " \t \n "}) {
                CandidateProfile p = build(text);
                assertNotNull(p);
                assertTrue(p.skills().isEmpty(), "skills should be empty for blank input: [" + text + "]");
            }
        }

        @Test
        @DisplayName("extremely short text fails safely")
        void extremelyShort() {
            CandidateProfile p = build("Hi");
            assertNotNull(p);
            assertTrue(p.skills().isEmpty());
        }

        @Test
        @DisplayName("headings with no content produce an empty profile without throwing")
        void headingsNoContent() {
            CandidateProfile p = build("""
                    Maya Patel
                    Skills
                    Education
                    Experience
                    Projects
                    """);
            assertNotNull(p);
            assertTrue(p.skills().isEmpty());
        }

        @Test
        @DisplayName("resume with only education still parses")
        void onlyEducation() {
            CandidateProfile p = build("""
                    Maya Patel
                    Education
                    B.Tech in Computer Science
                    """);
            assertNotNull(p);
            assertFalse(p.education().isEmpty());
        }

        @Test
        @DisplayName("resume with only projects still parses")
        void onlyProjects() {
            CandidateProfile p = build("""
                    Maya Patel
                    Projects
                    Built an IoT monitoring system using Arduino and Sensors
                    """);
            assertNotNull(p);
            assertFalse(p.projects().isEmpty());
            assertTrue(p.skills().contains("Arduino"));
        }

        @Test
        @DisplayName("resume with only skills still parses")
        void onlySkills() {
            CandidateProfile p = build("""
                    Maya Patel
                    Skills
                    Java, Spring Boot
                    """);
            assertNotNull(p);
            assertTrue(p.skills().contains("Java"));
            assertTrue(p.skills().contains("Spring Boot"));
        }

        @Test
        @DisplayName("resume with no recognized headings parses without throwing")
        void noRecognizedHeadings() {
            CandidateProfile p = build("""
                    A passionate developer who writes Java and enjoys Spring Boot in free time
                    """);
            assertNotNull(p);
            assertTrue(p.skills().contains("Java"));
            assertTrue(p.skills().contains("Spring Boot"));
        }
    }

    // ─── 6. Large / noisy input ───────────────────────────────────────────────

    @Nested
    @DisplayName("Large and noisy input")
    class NoisyInputTests {

        @Test
        @DisplayName("noisy resume stays deterministic and bounded")
        void noisyResumeIsBounded() {
            String noise = String.join("\n", java.util.Collections.nCopies(30,
                    "Repeated filler sentence about coffee, travel, reading and hobbies everywhere."));
            String resume = "Alex Morgan\n"
                    + "alex@example.com\n"
                    + "+91 98765 43210\n"
                    + "Skills\n"
                    + "Java, Java, Spring Boot, java, Spring Boot\n"
                    + "Technologies: Python, Docker, Docker\n"
                    + "Work Experience\n"
                    + "Built microservices with Spring Boot and PostgreSQL\n"
                    + "Projects\n"
                    + "Containerized a Python app with Docker\n"
                    + "Education\n"
                    + "B.Tech in Computer Science\n"
                    + "https://github.com/alex/morgan\n"
                    + "skype: alex.morgan, alex@example.com, +1 (555) 000-0000\n"
                    + noise + "\n"
                    + noise + "\n"
                    + noise + "\n";

            CandidateProfile p = build(resume);

            // Bounded: repeated skills and noise collapse to a small canonical set.
            assertTrue(p.skills().size() <= 8, "skills should stay bounded, got " + p.skills());
            assertTrue(p.skills().contains("Java"));
            assertTrue(p.skills().contains("Spring Boot"));
            assertTrue(p.skills().contains("Python"));
            assertTrue(p.skills().contains("Docker"));
            assertTrue(p.skills().contains("PostgreSQL"));
            assertEquals(1, p.skills().stream().filter(s -> s.equals("Java")).count());
            assertEquals("alex@example.com", p.email());

            // Evidence stays per-skill unique even with duplicate mentions.
            long springEvidence = p.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals("Spring Boot")).count();
            assertEquals(1, springEvidence);

            // Deterministic ordering.
            List<String> first = p.skills();
            CandidateProfile again = build(resume);
            assertEquals(first, again.skills());
        }

        @Test
        @DisplayName("repeated headings and duplicate sections do not multiply evidence")
        void repeatedSections() {
            String resume = "Sam Lee\n"
                    + "Skills\nJava, Docker\n"
                    + "Skills\nJava, Docker\n"
                    + "Skills\nJava, Docker\n"
                    + "Experience\nused Java\n"
                    + "Experience\nused Java\n";
            CandidateProfile p = build(resume);
            assertEquals(1, p.skills().stream().filter(s -> s.equals("Java")).count());
            assertEquals(1, p.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals("Java")).count());
        }
    }

    private ResumeEvidence.SourceSection evidenceSection(CandidateProfile profile, String canonicalSkill) {
        return profile.resumeEvidence().stream()
                .filter(e -> e.canonicalSkill().equals(canonicalSkill))
                .findFirst()
                .map(ResumeEvidence::sourceSection)
                .orElse(null);
    }
}