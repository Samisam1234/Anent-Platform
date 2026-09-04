package com.agentplatform.orchestrator.resume;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates PDF and DOCX text extraction and confirms that extracted text flows
 * through the same deterministic parsing layer as raw text (Phase 2 Step 2.3 §2).
 */
@DisplayName("ResumeParserService — PDF/DOCX extraction reaching the deterministic parser")
class ResumeParserServiceTest {

    private static final String RESUME = """
            Alice Johnson
            alice@example.com
            +1 555 123 4567
            Skills
            Java, Spring Boot, PostgreSQL, Git, Docker, REST API
            Experience
            Software Engineer at Acme (2 years): built microservices with Spring Boot and PostgreSQL
            Education
            B.Tech in Computer Science
            """;

    private final ResumeParserService parser = new ResumeParserService();

    private final DeterministicCandidateProfileBuilder builder = new DeterministicCandidateProfileBuilder();

    private byte[] buildPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(40, 720);
                for (String line : text.split("\n")) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -16);
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildBlankPdf() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildDocx(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String para : text.split("\n")) {
                if (para.isBlank()) continue;
                doc.createParagraph().createRun().setText(para);
            }
            XWPFTable table = doc.createTable(1, 1);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).setText("Table Cell: Docker");
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Nested
    @DisplayName("PDF extraction")
    class PdfTests {

        @Test
        @DisplayName("extracts text from a PDF and feeds it to the deterministic parser")
        void pdfExtractionReachesDeterministicParser() throws IOException {
            byte[] pdf = buildPdf(RESUME);
            String extracted = parser.extractText(pdf);

            assertFalse(extracted.isBlank());
            assertTrue(extracted.toLowerCase().contains("skills"));
            assertTrue(extracted.contains("Java"));

            CandidateProfile profile = builder.build(extracted);
            assertTrue(profile.skills().contains("Java"));
            assertTrue(profile.skills().contains("Spring Boot"));
            assertTrue(profile.skills().contains("PostgreSQL"));
            assertEquals("Alice Johnson", profile.name());
            assertEquals("alice@example.com", profile.email());
        }

        @Test
        @DisplayName("rejects null and empty PDF bytes")
        void rejectsBadInput() {
            assertThrows(IllegalArgumentException.class, () -> parser.extractText(null));
            assertThrows(IllegalArgumentException.class, () -> parser.extractText(new byte[0]));
        }

        @Test
        @DisplayName("throws ResumeException when extracted text is too short (image-like PDF)")
        void imageLikePdfThrows() throws IOException {
            assertThrows(ResumeException.class, () -> parser.extractText(buildBlankPdf()));
        }
    }

    @Nested
    @DisplayName("DOCX extraction")
    class DocxTests {

        @Test
        @DisplayName("extracts text from a DOCX (paragraphs + tables) and feeds it to the deterministic parser")
        void docxExtractionReachesDeterministicParser() throws IOException {
            byte[] docx = buildDocx(RESUME);
            String extracted = parser.extractText(docx);

            assertFalse(extracted.isBlank());
            assertTrue(extracted.contains("Java"));

            CandidateProfile profile = builder.build(extracted);
            assertTrue(profile.skills().contains("Spring Boot"));
            assertTrue(profile.skills().contains("PostgreSQL"));
            assertTrue(profile.skills().contains("Docker")); // from the table cell
            assertEquals("Alice Johnson", profile.name());
        }

        @Test
        @DisplayName("DOCX text is split into lines for the deterministic parser")
        void docxNormalizedLines() throws IOException {
            byte[] docx = buildDocx(RESUME);
            String extracted = parser.extractText(docx);
            assertTrue(extracted.split("\n").length > 1);
        }
    }

    @Nested
    @DisplayName("Content-type detection")
    class ContentTypeTests {

        @Test
        @DisplayName("corrupt bytes produce a ResumeException, not an NPE")
        void corruptBytesThrowResumeException() {
            byte[] junk = new byte[]{'n', 'o', 't', 'a', 'f', 'i', 'l', 'e'};
            ResumeException ex = assertThrows(ResumeException.class, () -> parser.extractText(junk));
            assertTrue(ex.getMessage().contains("Failed to parse") || ex.getMessage().contains("No useful text"));
        }
    }
}