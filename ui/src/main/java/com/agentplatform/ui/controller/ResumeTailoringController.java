package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.advisor.ApplicationAdvisorRequest;
import com.agentplatform.orchestrator.document.DocxResumeDocumentGenerator;
import com.agentplatform.orchestrator.document.GeneratedResumeDocument;
import com.agentplatform.orchestrator.document.PdfResumeDocumentGenerator;
import com.agentplatform.orchestrator.document.ResumeDocumentGenerator;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.job.exception.JobNotFoundException;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraftService;
import com.agentplatform.ui.dto.ResumeTailoringResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the existing deterministic ATS resume tailoring analysis.
 *
 * <p>Endpoints: {@code POST /api/v1/resume/tailor} (JSON analysis + draft),
 * {@code POST /api/v1/resume/tailor/pdf} and {@code POST /api/v1/resume/tailor/docx}
 * (downloaded document binaries). This is a thin API boundary over the existing
 * deterministic tailoring pipeline — nothing is re-implemented here.</p>
 *
 * <p>Truthfulness is a property of the underlying services: they only highlight skills,
 * projects and experience that the parsed profile already contains, keep missing
 * requirements in a separate {@code missingRequirements} list, and produce no LLM
 * output, no external calls and no persisted state. This controller never invents
 * skills, employers, projects, certifications or education.</p>
 *
 * <p>Reuses {@link ApplicationAdvisorRequest} as the identifier-only request shape
 * rather than introducing a parallel DTO.</p>
 */
@RestController
@RequestMapping("/api/v1/resume/tailor")
public class ResumeTailoringController {

    private static final Logger log = LoggerFactory.getLogger(ResumeTailoringController.class);

    private final CandidateProfilePersistenceService profilePersistenceService;
    private final JobSearchService jobSearchService;
    private final CareerGapAnalysisService careerGapAnalysisService;
    private final ResumeTailoringAnalysisService tailoringAnalysisService;
    private final TailoredResumeDraftService draftService;
    private final PdfResumeDocumentGenerator pdfGenerator;
    private final DocxResumeDocumentGenerator docxGenerator;

    public ResumeTailoringController(
            CandidateProfilePersistenceService profilePersistenceService,
            JobSearchService jobSearchService,
            CareerGapAnalysisService careerGapAnalysisService,
            ResumeTailoringAnalysisService tailoringAnalysisService,
            TailoredResumeDraftService draftService,
            PdfResumeDocumentGenerator pdfGenerator,
            DocxResumeDocumentGenerator docxGenerator) {
        this.profilePersistenceService = profilePersistenceService;
        this.jobSearchService = jobSearchService;
        this.careerGapAnalysisService = careerGapAnalysisService;
        this.tailoringAnalysisService = tailoringAnalysisService;
        this.draftService = draftService;
        this.pdfGenerator = pdfGenerator;
        this.docxGenerator = docxGenerator;
    }

    /**
     * Tailors the stored candidate profile towards one job.
     *
     * <p>Request body: <pre>{"candidateId": 1, "jobId": "12345"}</pre></p>
     *
     * @param request identifier-only request; resolved server-side
     * @return 200 with the deterministic {@link ResumeTailoringResponse} (analysis + draft)
     */
    @PostMapping
    public ResponseEntity<ResumeTailoringResponse> tailor(
            @RequestBody ApplicationAdvisorRequest request) {

        ResolvedTailoring resolved = resolveAndBuild(request);
        log.info("Resume tailoring complete: candidateId={}, jobId={}, matchedRequired={}, missingRequired={}",
                request.candidateId(), request.jobId(),
                resolved.analysis().matchedRequiredSkills().size(),
                resolved.analysis().missingRequiredSkills().size());

        return ResponseEntity.ok(new ResumeTailoringResponse(resolved.analysis(), resolved.draft()));
    }

    /**
     * Renders the tailored resume into a PDF document.
     *
     * <p>Request body: <pre>{"candidateId": 1, "jobId": "12345"}</pre>. Recomputes the
     * deterministic draft per request and lays it into a real PDF (no persistence,
     * no caching).</p>
     *
     * @return 200 with the rendered PDF as an attached download
     */
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> tailorPdf(@RequestBody ApplicationAdvisorRequest request) {
        return documentResponse(resolveAndBuild(request), pdfGenerator);
    }

    /**
     * Renders the tailored resume into a DOCX document.
     *
     * <p>Request body: <pre>{"candidateId": 1, "jobId": "12345"}</pre>. Recomputes the
     * deterministic draft per request and lays it into a real DOCX (no persistence,
     * no caching).</p>
     *
     * @return 200 with the rendered DOCX as an attached download
     */
    @PostMapping("/docx")
    public ResponseEntity<byte[]> tailorDocx(@RequestBody ApplicationAdvisorRequest request) {
        return documentResponse(resolveAndBuild(request), docxGenerator);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /**
     * The fully resolved, ready-to-render tailoring: the same candidate, job, analysis
     * and draft every tailor endpoint builds, so JSON and both document endpoints can
     * never disagree about the content they return.
     */
    private record ResolvedTailoring(
            CandidateProfile profile,
            Job job,
            ResumeTailoringAnalysis analysis,
            TailoredResumeDraft draft
    ) {
    }

    private ResolvedTailoring resolveAndBuild(ApplicationAdvisorRequest request) {
        ApplicationAdvisorRequest.validate(request);
        log.info("Resume tailoring requested: candidateId={}, jobId={}",
                request.candidateId(), request.jobId());

        CandidateProfile profile = profilePersistenceService
                .getByIdOrThrow(request.candidateId())
                .toDomain();
        Job job = jobSearchService.findById(request.jobId())
                .orElseThrow(() -> new JobNotFoundException(request.jobId()));

        // The gap analysis is the authoritative matched/missing input the tailoring
        // service expects, and is the same one the Application Advisor uses — so the
        // two features can never disagree about which skills the candidate has.
        CareerGapAnalysis gap = careerGapAnalysisService.analyze(profile, job);
        ResumeTailoringAnalysis analysis = tailoringAnalysisService.analyze(profile, job, gap);
        TailoredResumeDraft draft = draftService.generate(profile, job, analysis);

        return new ResolvedTailoring(profile, job, analysis, draft);
    }

    private static ResponseEntity<byte[]> documentResponse(
            ResolvedTailoring resolved, ResumeDocumentGenerator generator) {
        GeneratedResumeDocument document = generator.generate(
                resolved.draft(), resolved.profile(), resolved.job());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header("Content-Disposition", asAttachment(document.filename()))
                .body(document.bytes());
    }

    private static String asAttachment(String filename) {
        String safe = filename == null ? "" : filename;
        return "attachment; filename=\"" + safe.replaceAll("[\"\\\\]", "_") + "\"";
    }
}
