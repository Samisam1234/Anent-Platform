package com.agentplatform.orchestrator.document;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.DraftOrigin;
import com.agentplatform.orchestrator.tailoring.ResumeSection;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Plain JUnit tests for {@link PdfResumeDocumentGenerator} — real PDFBox rendering, no Spring. */
class PdfResumeDocumentGeneratorTest {

    private final PdfResumeDocumentGenerator generator = new PdfResumeDocumentGenerator();

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
                "Experienced Java developer — Spring backends.",
                List.of("Java", "Spring", "Docker"),
                List.of("Order management system"),
                List.of("Java Developer at Acme (2020-2023)"),
                List.of("Software Intern at Beta (2019)"),
                List.of(ResumeSection.SKILLS, ResumeSection.EXPERIENCE,
                        ResumeSection.PROJECTS, ResumeSection.INTERNSHIPS),
                DraftOrigin.DETERMINISTIC,
                List.of("Kafka is required but was not found in the resume."));
    }

    // ─── Tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("generates a real PDF with the expected filename")
    void generate_producesPdfWithFilename() {
        GeneratedResumeDocument doc = generator.generate(draft(), profile(), job());

        assertEquals("application/pdf", doc.contentType());
        assertEquals("senior-java-developer-tailored-resume.pdf", doc.filename());
        byte[] head = new byte[]{doc.bytes()[0], doc.bytes()[1], doc.bytes()[2], doc.bytes()[3], doc.bytes()[4]};
        assertArrayEquals("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII), head,
                "PDF magic header expected");
    }

    @Test
    @DisplayName("rendered PDF text contains the name, summary, skills, sections and warnings")
    void generate_textSurvivesRoundTrip() throws Exception {
        GeneratedResumeDocument doc = generator.generate(draft(), profile(), job());

        String text = new PDFTextStripper().getText(Loader.loadPDF(doc.bytes()));

        assertTrue(text.contains("Jane Doe"));
        assertTrue(text.contains("Tailored for Senior Java Developer | Acme GmbH"));
        assertTrue(text.contains("Professional Summary"));
        assertTrue(text.contains("Experienced Java developer"));
        assertTrue(text.contains("Skills"));
        assertTrue(text.contains("Java"));
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
    @DisplayName("non-ASCII glyphs are stripped to spaces so rendering never fails")
    void generate_rendersUnicodeSafely() throws Exception {
        CandidateProfile unicode = new CandidateProfile(
                "Zoë Müller", "zoe@example.com", null, "München",
                List.of("M.Sc. Électronique"), List.of("Java"),
                List.of(), List.of(), List.of(), List.of(),
                List.of("Java"), List.of(), List.of(), List.of());
        Job simpleJob = new Job("j1", "Java Dev", "Acme", null, "x",
                List.of("Java"), List.of(), "2y", "FULL_TIME", "2026-01-01",
                "mock", "u", "MOCK", Instant.parse("2026-01-01T00:00:00Z"), null);

        GeneratedResumeDocument doc = generator.generate(draft(), unicode, simpleJob);

        String text = new PDFTextStripper().getText(Loader.loadPDF(doc.bytes()));
        // É / Ü / ö are outside WinAnsi/ASCII and must be dropped, not crash the render.
        assertFalse(text.contains("Électronique"));
        assertTrue(text.contains("Java Dev | Acme"));
    }

    @Test
    @DisplayName("long content flows onto additional pages")
    void generate_paginatesLongContent() throws Exception {
        List<String> plenty = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            plenty.add("Long experience entry number " + i + " with a deliberately padded "
                    + "description that repeats words to ensure the line wraps more than once.");
        }
        TailoredResumeDraft longDraft = new TailoredResumeDraft(
                "job-1", 1L, "Summary",
                List.of("Java"),
                List.of(), plenty, List.of(),
                List.of(ResumeSection.EXPERIENCE),
                DraftOrigin.DETERMINISTIC, List.of());

        GeneratedResumeDocument doc = generator.generate(longDraft, profile(), job());

        String text = new PDFTextStripper().getText(Loader.loadPDF(doc.bytes()));
        assertTrue(text.contains(
                        "Long experience entry number 69"),
                "tail of the content must be present");
    }

    @Test
    @DisplayName("same inputs produce byte-identical PDFs (deterministic, no timestamps)")
    void generate_isByteDeterministic() {
        byte[] first = generator.generate(draft(), profile(), job()).bytes();
        byte[] second = generator.generate(draft(), profile(), job()).bytes();

        assertArrayEquals(first, second, "identical inputs must render identical PDF bytes");
    }

    @Test
    @DisplayName("null inputs are tolerated and produce a valid PDF")
    void generate_nullInputsStillRender() throws Exception {
        GeneratedResumeDocument doc = generator.generate(null, null, null);

        assertTrue(doc.bytes().length > 0);
        assertEquals("tailored-resume.pdf", doc.filename());
        assertEquals("application/pdf", doc.contentType());
        // smoke check: the produced bytes really are a parseable PDF
        try (PDDocument ignored = Loader.loadPDF(doc.bytes())) {
            assertEquals(1, ignored.getNumberOfPages());
        }
    }

    // ─── Filename slug ───────────────────────────────────────────────────

    @Test
    @DisplayName("slugify builds a safe lowercase ASCII filename slug")
    void slugify_producesSafeSlugs() {
        assertEquals("senior-java-developer", PdfResumeDocumentGenerator.slugify("Senior Java Developer"));
        assertEquals("c-react", PdfResumeDocumentGenerator.slugify("  C++ / React "));
        assertEquals("n-code", PdfResumeDocumentGenerator.slugify("Ünïcode"));
        assertEquals("java", PdfResumeDocumentGenerator.slugify("-java-"));

        String longTitle = "Senior ".repeat(30); // 210 chars
        String slug = PdfResumeDocumentGenerator.slugify(longTitle);
        assertTrue(slug.length() <= 60, "slug must be capped");
    }
}
