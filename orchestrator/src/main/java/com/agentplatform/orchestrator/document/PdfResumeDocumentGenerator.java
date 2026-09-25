package com.agentplatform.orchestrator.document;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.DraftOrigin;
import com.agentplatform.orchestrator.tailoring.ResumeSection;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link TailoredResumeDraft} into a real PDF using PDFBox 3.0.3.
 *
 * <p>Pure renderer over the already-computed draft — no tailoring logic is recomputed, no
 * LLM/network, no persistence, no timestamps, no randomness. The produced bytes are
 * deterministic for identical inputs.</p>
 *
 * <p>Text layout is a deliberate, naive max-width word split sized for ASCII Helvetica
 * (Standard-14); CJK/special glyphs are out of scope for the current parse pipeline.
 * Extra pages are created when content runs past the bottom margin.</p>
 * ponytail: naive width split + ASCII-safe glyph stripping; upgrade to a loaded font and a
 * real text layout engine only if a real case needs it.
 */
@Component
public class PdfResumeDocumentGenerator implements ResumeDocumentGenerator {

    private static final String CONTENT_TYPE = "application/pdf";
    private static final String FALLBACK_FILENAME = "tailored-resume.pdf";
    private static final int MAX_FILENAME_SLUG = 60;

    private static final float LEFT_MARGIN = 40;
    private static final float TOP_Y = 740;
    private static final float BOTTOM_Y = 40;
    private static final int MAX_LINE_CHARS = 90;

    /** Pinned xref-trailer file ID so identical input renders byte-identical PDFs. */
    private static final long FILE_ID = 42;

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    @Override
    public GeneratedResumeDocument generate(TailoredResumeDraft draft, CandidateProfile profile, Job job) {
        String slug = slugify(jobTitle(job));
        String filename = slug.isEmpty() ? FALLBACK_FILENAME : slug + "-tailored-resume.pdf";
        byte[] bytes = render(toLines(draft, profile, job));
        return new GeneratedResumeDocument(bytes, CONTENT_TYPE, filename);
    }

    // ─── Layout ──────────────────────────────────────────────────────────

    /** One rendered line: text, font size, bold flag. */
    private record Line(String text, float size, boolean bold) {
    }

    private static List<Line> toLines(TailoredResumeDraft draft, CandidateProfile profile, Job job) {
        TailoredResumeDraft d = draft == null ? emptyDraft() : draft;
        CandidateProfile p = profile == null ? emptyProfile() : profile;

        List<Line> lines = new ArrayList<>();

        String name = nvl(p.name());
        if (!name.isEmpty()) {
            lines.add(new Line(name, 16, true));
        }
        String contact = joinNonBlank(" | ", p.email(), p.phone(), p.location());
        if (!contact.isEmpty()) {
            lines.add(new Line(contact, 10, false));
        }
        String context = tailoringContext(job);
        if (!context.isEmpty()) {
            lines.add(new Line(context, 10, false));
        }

        String summary = nvl(d.professionalSummary());
        if (!summary.isEmpty()) {
            lines.add(new Line("Professional Summary", 12, true));
            lines.addAll(wrap(summary));
        }

        for (ResumeSection section : d.sectionOrder()) {
            List<String> content = sectionContent(section, d, p);
            if (content.isEmpty()) {
                continue;
            }
            lines.add(new Line(sectionHeading(section), 12, true));
            for (String item : content) {
                lines.addAll(wrap(item));
            }
        }

        List<String> warnings = d.warnings();
        if (!warnings.isEmpty()) {
            lines.add(new Line("Notes", 12, true));
            for (String warning : warnings) {
                lines.addAll(wrap("Note: " + warning));
            }
        }
        return lines;
    }

    private static List<Line> wrap(String text) {
        List<Line> lines = new ArrayList<>();
        for (String piece : wrappedStrings(text, MAX_LINE_CHARS)) {
            lines.add(new Line(piece, 10, false));
        }
        return lines;
    }

    private static List<String> sectionContent(ResumeSection section, TailoredResumeDraft d,
                                               CandidateProfile p) {
        return switch (section) {
            case SKILLS -> d.orderedSkills();
            case PROJECTS -> d.highlightedProjects();
            case EXPERIENCE -> d.highlightedExperience();
            case INTERNSHIPS -> d.highlightedInternships();
            case CERTIFICATIONS -> safe(p.certifications());
            case EDUCATION -> safe(p.education());
            case SUMMARY -> List.of(); // rendered as the intro paragraph above
        };
    }

    private static String sectionHeading(ResumeSection section) {
        return switch (section) {
            case SKILLS -> "Skills";
            case PROJECTS -> "Projects";
            case EXPERIENCE -> "Experience";
            case INTERNSHIPS -> "Internships";
            case CERTIFICATIONS -> "Certifications";
            case EDUCATION -> "Education";
            case SUMMARY -> "Professional Summary";
        };
    }

    // ─── PDF rendering ───────────────────────────────────────────────────

    private static byte[] render(List<Line> lines) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.setDocumentId(FILE_ID);
            PDPage page = new PDPage();
            document.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(document, page);
            float y = TOP_Y;
            for (Line line : lines) {
                boolean pageBreak = true;
                for (String text : wrappedStrings(line.text(), MAX_LINE_CHARS)) {
                    if (y < BOTTOM_Y) {
                        cs.close();
                        page = new PDPage();
                        document.addPage(page);
                        cs = new PDPageContentStream(document, page);
                        y = TOP_Y;
                    }
                    cs.beginText();
                    cs.setFont(line.bold() ? BOLD : REGULAR, line.size());
                    cs.newLineAtOffset(LEFT_MARGIN, y);
                    cs.showText(text);
                    cs.endText();
                    y -= line.size() + 6;
                    pageBreak = false;
                }
                // Blank line spacing between logical lines that wrapped to nothing.
                if (pageBreak) {
                    y -= 12;
                }
            }
            cs.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render the tailored resume PDF", e);
        }
    }

    // ─── Text handling ───────────────────────────────────────────────────

    /**
     * Greedy word wrap at {@code maxChars}, with over-long words hard-split. Applies
     * ASCII-safe cleanup first so {@code showText} never hits a non-WinAnsi glyph.
     */
    private static List<String> wrappedStrings(String text, int maxChars) {
        List<String> out = new ArrayList<>();
        String clean = asciiSafe(text).trim();
        if (clean.isEmpty()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : clean.split("\\s+")) {
            if (word.length() > maxChars) {
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (rest.length() > maxChars) {
                    out.add(rest.substring(0, maxChars));
                    rest = rest.substring(maxChars);
                }
                line.append(rest);
                continue;
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= maxChars) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** Maps every character outside printable ASCII to a space (WinAnsi safety). */
    private static String asciiSafe(String text) {
        String t = text == null ? "" : text;
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            sb.append((c >= 0x20 && c <= 0x7E) ? c : ' ');
        }
        return sb.toString();
    }

    // ─── Shared helpers ──────────────────────────────────────────────────

    /** Lowercase ASCII slug: keeps {@code [a-z0-9]}, others collapse to {@code -}, capped. */
    static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_FILENAME_SLUG) {
            slug = slug.substring(0, MAX_FILENAME_SLUG).replaceAll("-+$", "");
        }
        return slug;
    }

    private static String jobTitle(Job job) {
        return job == null ? "" : nvl(job.title());
    }

    private static String tailoringContext(Job job) {
        if (job == null) {
            return "";
        }
        String title = nvl(job.title());
        String company = nvl(job.company());
        if (title.isEmpty() && company.isEmpty()) {
            return "";
        }
        return title.isEmpty()
                ? "Tailored for " + company
                : (company.isEmpty() ? "Tailored for " + title : "Tailored for " + title + " | " + company);
    }

    private static String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String p = nvl(part).trim();
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private static List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static TailoredResumeDraft emptyDraft() {
        return new TailoredResumeDraft(null, null, "", List.of(), List.of(), List.of(), List.of(),
                List.of(), DraftOrigin.DETERMINISTIC, List.of());
    }

    private static CandidateProfile emptyProfile() {
        return new CandidateProfile(null, null, null, null, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}