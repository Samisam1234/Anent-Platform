package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.advisor.ApplicationAdvisorRequest;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.job.exception.JobNotFoundException;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the existing deterministic ATS resume tailoring analysis.
 *
 * <p>Endpoint: {@code POST /api/v1/resume/tailor}. This is a thin API boundary over
 * {@link ResumeTailoringAnalysisService}, which already existed and was previously only
 * reachable from inside the Application Advisor — so the tailoring workflow had no
 * user-facing entry point. Nothing is re-implemented here.</p>
 *
 * <p>Truthfulness is a property of the underlying service: it only highlights skills,
 * projects and experience that the parsed profile already contains, keeps missing
 * requirements in a separate {@code missingRequirements} list, and produces no LLM
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

    public ResumeTailoringController(
            CandidateProfilePersistenceService profilePersistenceService,
            JobSearchService jobSearchService,
            CareerGapAnalysisService careerGapAnalysisService,
            ResumeTailoringAnalysisService tailoringAnalysisService) {
        this.profilePersistenceService = profilePersistenceService;
        this.jobSearchService = jobSearchService;
        this.careerGapAnalysisService = careerGapAnalysisService;
        this.tailoringAnalysisService = tailoringAnalysisService;
    }

    /**
     * Tailors the stored candidate profile towards one job.
     *
     * <p>Request body: <pre>{"candidateId": 1, "jobId": "12345"}</pre></p>
     *
     * @param request identifier-only request; resolved server-side
     * @return 200 with the deterministic {@link ResumeTailoringAnalysis}
     */
    @PostMapping
    public ResponseEntity<ResumeTailoringAnalysis> tailor(
            @RequestBody ApplicationAdvisorRequest request) {

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

        log.info("Resume tailoring complete: candidateId={}, jobId={}, matchedRequired={}, missingRequired={}",
                request.candidateId(), request.jobId(),
                analysis.matchedRequiredSkills().size(), analysis.missingRequiredSkills().size());

        return ResponseEntity.ok(analysis);
    }
}
