package com.agentplatform.orchestrator.resume;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * Synthetic DOCX resume fixture used by tests and by the browser E2E scripts in
 * {@code scripts/e2e/}. It replaces the personal CV that used to sit at the repository root
 * (Cleanup Batch 5).
 *
 * <p>Every value below is invented: the person, the email address (IANA {@code example.com}),
 * the phone number (the reserved {@code +1 555 01xx} fictional range), the employers, the
 * university and the project names. No personal data was copied from the previous CV.</p>
 *
 * <p>The canonical content is {@link #LINES}. The tracked artifact is
 * {@code orchestrator/src/test/resources/fixtures/sample-resume.docx}; regenerate it after
 * editing {@link #LINES} with {@link #main} (see {@code scripts/e2e/README.md}).
 * {@link SampleResumeFixtureTest} fails if the tracked file drifts from this content.</p>
 */
final class SampleResumeFixture {

    static final String NAME = "Jordan Sample";
    static final String EMAIL = "jordan.sample@example.com";
    static final String PHONE = "+1 555 0100";

    static final String[] LINES = {
        NAME,
        "Remote | " + EMAIL + " | " + PHONE + " | github.com/jordan-sample",
        "",
        "PROFESSIONAL SUMMARY",
        "Synthetic software engineer with 6 years of experience building Java backend services and REST APIs.",
        "",
        "EXPERIENCE",
        "Senior Software Engineer, Northwind Sampleworks Ltd (2021 - Present)",
        "Built Java and Spring Boot services for a fictional payments platform.",
        "Cut average API response time by 40 percent in a synthetic load test.",
        "Software Engineer, Lakeside Sample Systems Inc (2018 - 2021)",
        "Developed Python and Java microservices for a fictional retail catalogue.",
        "",
        "EDUCATION",
        "Bachelor of Science in Computer Science, Example State University (2014 - 2018)",
        "",
        "SKILLS",
        "Java, Spring Boot, Python, SQL, Docker, PostgreSQL, Git, REST API, Maven",
        "",
        "CERTIFICATIONS",
        "AWS Certified Developer Associate (fictional example credential)",
        "",
        "PROJECTS",
        "Sample Task Planner, a Java CLI for tracking fictional tasks."
    };

    private SampleResumeFixture() {
    }

    static byte[] docx() throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : LINES) {
                if (line.isEmpty()) {
                    continue;
                }
                doc.createParagraph().createRun().setText(line);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** Writes the fixture to the given path: {@code SampleResumeFixture <out.docx>}. */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: SampleResumeFixture <output.docx>");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, docx());
        System.out.println("wrote " + out.toAbsolutePath() + " (" + Files.size(out) + " bytes)");
    }
}
