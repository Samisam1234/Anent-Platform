package com.agentplatform.orchestrator.document;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.DraftOrigin;
import com.agentplatform.orchestrator.tailoring.ResumeSection;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Plain JUnit tests for {@link DocxResumeDocumentGenerator} — real POI rendering, no Spring. */
class DocxResumeDocumentGeneratorTest {

    private final DocxResumeDocumentGenerator generator = new DocxResumeDocumentGenerator();

    // ─── Fixtures ────────────────────────────────────────────────────────

    private static CandidateProfile profile() {
        return new CandidateProfile(
                "Jane Doe", "jane@example.com", "555-1234", "Berlin",
                List.of("B.Sc. Computer Science"),
                List.of("Java", "Spring", "Docker"),
                List.of("Java Developer at Acme (2020-2023)"),
                List.of("Software Intern at Beta (2019)"),
                List.of("Order management system"),
                List.of("Oracle Certified Java SE 17"),
                List.of("Java", "Spring"),
                List.of("Embedded C"),
                List.of("Software Engineer"),
                List.of("Berlin", "Remote"));
    }

    private static Job job() {
        return new Job(
                "job-1", "Senior Java Developer", "Acme GmbH", "Berlin, Germany",
                "Backend services in Java and Spring.",
                List.of("Java", "Spring"), List.of("Docker"),
                "5 years", "FULL_TIME", "2026-09-01", "mock",
                "https://example.test/jobs/job-1", "MOCK",
                Instant.parse("2026-09-01T08:00:00Z"), "https://example.test/apply");
    }

    private static TailoredResumeDraft draft() {
        return new TailoredResumeDraft(
                "job-1", 1L,
                "Experienced Java developer, Spring backends.",
                List.of("Java", "Spring", "Docker"),
                List.of("Order management system"),
                List.of("Java Developer at Acme (2020-2023)"),
                List.of("Software Intern at Beta (2019)"),
                List.of(ResumeSection.SKILLS, ResumeSection.EXPERIENCE,
                        ResumeSection.PROJECTS, ResumeSection.INTERNSHIPS),
                DraftOrigin.DETERMINISTIC,
                List.of("Kafka is required but was not found in the resume."));
    }

    /** Every body paragraph's full text, joined. */
    private static String renderedText(GeneratedResumeDocument doc) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument parsed = new XWPFDocument(new ByteArrayInputStream(doc.bytes()))) {
            parsed.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
        }
        return text.toString();
    }

    // ─── Tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("generates a real DOCX with the expected filename")
    void generate_producesDocxWithFilename() {
        GeneratedResumeDocument doc = generator.generate(draft(), profile(), job());

        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                doc.contentType());
        assertEquals("senior-java-developer-tailored-resume.docx", doc.filename());
        assertEquals(0x50, doc.bytes()[0] & 0xFF, "ZIP magic 'P'");
        assertEquals(0x4B, doc.bytes()[1] & 0xFF, "ZIP magic 'K'");
    }

    @Test
    @DisplayName("rendered document contains the name, summary, sections, bullets and warnings")
    void generate_textSurvivesRoundTrip() throws Exception {
        GeneratedResumeDocument doc = generator.generate(draft(), profile(), job());

        String text = renderedText(doc);

        assertTrue(text.contains("Jane Doe"));
        assertTrue(text.contains("jane@example.com | 555-1234 | Berlin"));
        assertTrue(text.contains("Tailored for Senior Java Developer | Acme GmbH"));
        assertTrue(text.contains("Professional Summary"));
        assertTrue(text.contains("Experienced Java developer"));
        assertTrue(text.contains("Skills"));
        assertTrue(text.contains("\u2022 Java"));
        assertTrue(text.contains("Experience"));
        assertTrue(text.contains("Java Developer at Acme (2020-2023)"));
        assertTrue(text.contains("Projects"));
        assertTrue(text.contains("Order management system"));
        assertTrue(text.contains("Internships"));
        assertTrue(text.contains("Software Intern at Beta (2019)"));
        assertTrue(text.contains("Notes"));
        assertTrue(text.contains("Note: Kafka is required"));
    }

    @Test
    @DisplayName("certification and education sections render when present in the order")
    void generate_rendersCertificationsAndEducation() throws Exception {
        TailoredResumeDraft full = new TailoredResumeDraft(
                "job-1", 1L, "Summary",
                List.of("Java"), List.of(), List.of(), List.of(),
                List.of(ResumeSection.CERTIFICATIONS, ResumeSection.EDUCATION),
                DraftOrigin.DETERMINISTIC, List.of());

        GeneratedResumeDocument doc = generator.generate(full, profile(), job());

        String text = renderedText(doc);
        assertTrue(text.contains("Certifications"));
        assertTrue(text.contains("Oracle Certified Java SE 17"));
        assertTrue(text.contains("Education"));
        assertTrue(text.contains("B.Sc. Computer Science"));
    }

    @Test
    @DisplayName("null inputs are tolerated and produce a valid DOCX")
    void generate_nullInputsStillRender() throws Exception {
        GeneratedResumeDocument doc = generator.generate(null, null, null);

        assertTrue(doc.bytes().length > 0);
        assertEquals("tailored-resume.docx", doc.filename());
        // structural validity proof: POI must be able to re-parse the empty draft
        renderedText(doc);
    }

    @Test
    @DisplayName("content is deterministic even though bytes are not asserted equal")
    void generate_contentIsStable() throws Exception {
        String first = renderedText(generator.generate(draft(), profile(), job()));
        String second = renderedText(generator.generate(draft(), profile(), job()));

        assertEquals(first, second, "identical inputs must render identical text content");
    }

    @Test
    @DisplayName("slugify builds a safe lowercase ASCII filename slug")
    void slugify_producesSafeSlugs() {
        assertEquals("senior-java-developer", DocxResumeDocumentGenerator.slugify("Senior Java Developer"));
        assertEquals("c-react", DocxResumeDocumentGenerator.slugify("  C++ / React "));
        assertEquals("n-code", DocxResumeDocumentGenerator.slugify("Ünïcode"));
        assertEquals("", DocxResumeDocumentGenerator.slugify("!!!"));

        String longTitle = "Senior ".repeat(30); // 210 chars
        String slug = DocxResumeDocumentGenerator.slugify(longTitle);
        assertTrue(slug.length() <= 60, "slug must be capped");
        assertEquals(60, slug.length(), "long slugs hit the cap head-on");
    }
}