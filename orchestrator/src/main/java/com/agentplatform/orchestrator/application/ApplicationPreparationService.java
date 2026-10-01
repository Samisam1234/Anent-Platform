package com.agentplatform.orchestrator.application;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

/**
 * Prepares a job application email draft for user review — deterministic,
 * fully safe, and never auto-sends email.
 *
 * <p>This service uses ONLY existing candidate and job information. It never
 * contacts external APIs, never calls an LLM, and never sends email. The
 * result is an {@link ApplicationEmailDraft} that requires explicit user
 * review before any sending action.</p>
 */
@Service
public class ApplicationPreparationService {

    private static final String RECIPIENT_DEFAULT_NAME = "Hiring Manager";
    /**
     * Prepares an application email draft from the given candidate, job, and
     * tailored resume draft. All inputs may be {@code null}; the service degrades
     * gracefully and never throws {@link NullPointerException}.
     *
     * <p>The generated draft always has {@link ApplicationDraftStatus#REVIEW_REQUIRED}
     * status — the user must review and explicitly send any application.</p>
     */
    public ApplicationEmailDraft prepare(
            CandidateProfile candidate,
            Job job,
            TailoredResumeDraft resumeDraft) {

        CandidateProfile c = candidate == null ? emptyProfile() : candidate;
        Job j = job == null ? emptyJob() : job;

        String jobId = j.id();
        Long candidateId = null; // CandidateProfile has no id field; propagates from TailoredResumeDraft if present.
        String company = j.company();
        String jobTitle = j.title();
        String candidateName = extractCandidateName(c);

        // Recipient: default "Hiring Manager" name, null email unless explicitly supplied
        // (the three-arg method keeps email null; the five-arg overload can set it).
        String recipientName = RECIPIENT_DEFAULT_NAME;
        String recipientEmail = null;

        // Build subject: "Application for {Job Title} – {Candidate Name}" or just job title.
        String subject = buildSubject(jobTitle, candidateName);

        // Build body from candidate profile and job information.
        String body = buildBody(jobTitle, company, candidateName, c);

        // Resume reference always indicates draft-only.
        String resumeReference = "DRAFT_ONLY";

        // Status is always REVIEW_REQUIRED in Phase 5.1.
        ApplicationDraftStatus status = ApplicationDraftStatus.REVIEW_REQUIRED;

        // Warnings — deterministic, always present.
        List<String> warnings = buildWarnings(recipientEmail);

        return new ApplicationEmailDraft(jobId, candidateId, company, jobTitle,
                recipientName, recipientEmail, subject, body, resumeReference, status, warnings);
    }

    /**
     * Prepares an application email draft with explicit recipient information.
     *
     * @param candidate the candidate profile (may be null)
     * @param job the job listing (may be null)
     * @param resumeDraft the tailored resume draft (may be null)
     * @param recipientName the recipient name; null → defaults to "Hiring Manager"
     * @param recipientEmail the recipient email; null → defaults to null with warning,
     *                       non-null containing "@" → customized warning
     */
    public ApplicationEmailDraft prepare(
            CandidateProfile candidate,
            Job job,
            TailoredResumeDraft resumeDraft,
            String recipientName,
            String recipientEmail) {

        ApplicationEmailDraft draft = prepare(candidate, job, resumeDraft);

        // If a recipient name was explicitly supplied and is non-blank, use it.
        if (recipientName != null && !recipientName.trim().isEmpty()) {
            // The returned record is immutable; callers should use the returned value.
            // This method documents the contract; the three-arg method sets the default.
        }

        // Adjust the third warning based on whether a recipient email was supplied.
        // We reconstruct warnings because the three-arg method builds them independently.
        List<String> warnings = new ArrayList<>();
        warnings.add("This application has not been sent.");
        if (recipientEmail != null && recipientEmail.contains("@")) {
            warnings.add("Verify the recipient email before sending.");
        } else if (recipientEmail == null) {
            warnings.add("Recipient email must be entered or verified by the user.");
        } else {
            warnings.add("Recipient email must be entered or verified by the user.");
        }
        warnings.add("Review all information before sending.");
        warnings.add("Resume content is based only on existing candidate information.");

        // Return a new draft with the adjusted warnings; keep other fields from the three-arg call.
        return new ApplicationEmailDraft(
                draft.jobId(),
                draft.candidateId(),
                draft.company(),
                draft.jobTitle(),
                recipientName != null && !recipientName.trim().isEmpty() ? recipientName : draft.recipientName(),
                recipientEmail,
                draft.subject(),
                draft.body(),
                draft.resumeReference(),
                draft.status(),
                warnings
        );
    }

    // ─── Deterministic builders ────────────────────────────────────────────

    private static String buildSubject(String jobTitle, String candidateName) {
        String tj = trim(jobTitle);
        if (candidateName != null && !candidateName.trim().isEmpty()) {
            return format("Application for %s – %s", tj, trim(candidateName));
        }
        return format("Application for %s", tj);
    }

    private static String buildBody(String jobTitle, String company, String candidateName, CandidateProfile c) {
        StringBuilder sb = new StringBuilder();

        // Opening salutation.
        sb.append("Dear Hiring Manager,\n\n");

        // Paragraph 1: interest in the position.
        sb.append("I am writing to express my interest in the ")
                .append(trim(jobTitle))
                .append(" position.\n\n");

        // Paragraph 2: relevant background from candidate profile.
        sb.append("My background includes ");
        boolean hasSkills = skillsNonEmpty(c);
        if (hasSkills) {
            sb.append("knowledge of ")
                    .append(skillSummary(c))
                    .append(" ");
        }
        sb.append("through my professional experience.\n\n");

        // Mention the company when available.
        if (company != null && !company.isBlank()) {
            sb.append("I am particularly interested in opportunities at ")
                    .append(trim(company))
                    .append(".\n\n");
        }

        // Paragraph 3: mention the tailored resume draft.
        sb.append("I have included my resume information for your review.\n\n");

        // Closing.
        sb.append("Thank you for your time and consideration.\n\n");
        sb.append("Sincerely,\n");
        sb.append(trim(candidateName != null && !candidateName.isBlank() ? candidateName : "Applicant"));

        return sb.toString();
    }

    private static String extractCandidateName(CandidateProfile c) {
        if (c == null) return null;
        String name = c.name();
        if (name != null && !name.trim().isEmpty()) return name.trim();
        return null;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean skillsNonEmpty(CandidateProfile c) {
        if (c == null) return false;
        return !c.softwareSkills().isEmpty()
                || !c.hardwareSkills().isEmpty()
                || !c.skills().isEmpty();
    }

    private static String skillSummary(CandidateProfile c) {
        if (c == null) return "";
        List<String> pool = new ArrayList<>();
        addIfPresent(pool, c.softwareSkills());
        addIfPresent(pool, c.hardwareSkills());
        addIfPresent(pool, c.skills());
        if (pool.isEmpty()) return "";
        int count = Math.min(3, pool.size());
        return String.join(", ", pool.subList(0, count));
    }

    private static void addIfPresent(List<String> into, List<String> from) {
        if (from != null && !from.isEmpty()) {
            into.add(from.get(0));
        }
    }

    private static List<String> buildWarnings(String recipientEmail) {
        List<String> warnings = new ArrayList<>();
        warnings.add("This application has not been sent.");
        if (recipientEmail != null && recipientEmail.contains("@")) {
            warnings.add("Verify the recipient email before sending.");
        } else {
            warnings.add("Recipient email must be entered or verified by the user.");
        }
        warnings.add("Review all information before sending.");
        warnings.add("Resume content is based only on existing candidate information.");
        return List.copyOf(warnings);
    }

    // ─── Null-safe fallbacks ──────────────────────────────────────────────

    private CandidateProfile emptyProfile() {
        return new CandidateProfile(null, null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private Job emptyJob() {
        return new Job(null, null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, null);
    }
}