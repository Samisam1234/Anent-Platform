package com.agentplatform.orchestrator.document;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.DraftOrigin;
import com.agentplatform.orchestrator.tailoring.ResumeSection;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Renders a {@link TailoredResumeDraft} into a real DOCX using Apache POI 5.2.5
 * ({@link XWPFDocument}).
 *
 * <p>Pure renderer over the already-computed draft — no tailoring logic is recomputed, no
 * LLM/network, no persistence, no invented content. POI embeds creation metadata in the
 * package, so DOCX bytes are content-identical but not byte-identical across runs; byte
 * equality is deliberately NOT a property of this generator.</p>
 */
@Component
public class DocxResumeDocumentGenerator implements ResumeDocumentGenerator {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String FALLBACK_FILENAME = "tailored-resume.docx";
    private static final int MAX_FILENAME_SLUG = 60;
    private static final String BULLET = "\u2022 ";

    @Override
    public GeneratedResumeDocument generate(TailoredResumeDraft draft, CandidateProfile profile, Job job) {
        String slug = slugify(jobTitle(job));
        String filename = slug.isEmpty() ? FALLBACK_FILENAME : slug + "-tailored-resume.docx";
        TailoredResumeDraft d = draft == null ? emptyDraft() : draft;
        CandidateProfile p = profile == null ? emptyProfile() : profile;

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            build(document, d, p, job);
            document.write(out);
            return new GeneratedResumeDocument(out.toByteArray(), CONTENT_TYPE, filename);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render the tailored resume DOCX", e);
        }
    }

    // ─── Layout ──────────────────────────────────────────────────────────

    private static void build(XWPFDocument doc, TailoredResumeDraft d, CandidateProfile p, Job job) {
        String name = nvl(p.name());
        if (!name.isEmpty()) {
            heading(doc, name, 16);
        }
        String contact = joinNonBlank(" | ", p.email(), p.phone(), p.location());
        if (!contact.isEmpty()) {
            body(doc, contact);
        }
        String context = tailoringContext(job);
        if (!context.isEmpty()) {
            body(doc, context);
        }

        String summary = nvl(d.professionalSummary());
        if (!summary.isEmpty()) {
            heading(doc, "Professional Summary", 13);
            body(doc, summary);
        }

        for (ResumeSection section : d.sectionOrder()) {
            List<String> content = sectionContent(section, d, p);
            if (content.isEmpty()) {
                continue;
            }
            heading(doc, sectionHeading(section), 13);
            for (String item : content) {
                body(doc, BULLET + item);
            }
        }

        List<String> warnings = d.warnings();
        if (!warnings.isEmpty()) {
            heading(doc, "Notes", 13);
            for (String warning : warnings) {
                body(doc, "Note: " + warning);
            }
        }
    }

    private static void heading(XWPFDocument doc, String text, int size) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(size);
        run.setText(text);
    }

    private static void body(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(11);
        run.setText(text);
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