package com.agentplatform.orchestrator.document;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;

/**
 * Renders an existing {@link TailoredResumeDraft} plus its source candidate and job into a
 * downloadable resume document.
 *
 * <p>Generators are pure renderers: they consume the already-computed draft and never
 * recompute any tailoring/matching/scoring logic, never call an LLM or the network, never
 * mutate or persist their inputs, and never invent candidate content. Content is
 * deterministic for identical inputs.</p>
 */
public interface ResumeDocumentGenerator {

    /**
     * Renders a tailored resume document from the already-computed draft and its sources.
     *
     * <p>The callers resolve the {@code profile}/{@code job} through the existing
     * persistence/job-search layers and build the {@code draft} through the existing
     * deterministic tailoring pipeline; this method only lays that content out.</p>
     *
     * @param draft   already-computed tailored resume draft (never recomputed here)
     * @param profile the candidate whose existing content the draft references
     * @param job     the job the draft was tailored towards (for context headings)
     * @return the rendered document with bytes, MIME type and safe attachment filename
     */
    GeneratedResumeDocument generate(TailoredResumeDraft draft, CandidateProfile profile, Job job);
}