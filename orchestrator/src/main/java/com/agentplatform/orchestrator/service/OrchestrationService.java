package com.agentplatform.orchestrator.service;

import com.agentplatform.orchestrator.agent.AgentContext;
import com.agentplatform.orchestrator.agent.CareerAgentOrchestrator;
import com.agentplatform.orchestrator.agent.OrchestrationRun;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.job.exception.JobNotFoundException;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import org.springframework.stereotype.Service;

/**
 * API-boundary service that resolves candidate/job input through the existing
 * persistence and job-source layers and then runs the career-agent pipeline.
 *
 * <p>The client only ever supplies safe identifiers ({@code candidateId},
 * {@code jobId}); it can never fabricate a {@link CandidateProfile} or
 * {@link Job}. All orchestration logic stays in
 * {@link CareerAgentOrchestrator} — this service only builds the
 * {@link AgentContext} and delegates. It performs no LLM calls, no email
 * sending, and no autonomous work of its own.</p>
 */
@Service
public class OrchestrationService {

    private final CareerAgentOrchestrator orchestrator;
    private final CandidateProfilePersistenceService profilePersistenceService;
    private final JobSearchService jobSearchService;

    public OrchestrationService(CareerAgentOrchestrator orchestrator,
                                CandidateProfilePersistenceService profilePersistenceService,
                                JobSearchService jobSearchService) {
        this.orchestrator = orchestrator;
        this.profilePersistenceService = profilePersistenceService;
        this.jobSearchService = jobSearchService;
    }

    /**
     * Resolves the candidate and job by id and runs the controlled pipeline.
     *
     * @param candidateId the stored candidate profile id (resolved, never trusted
     *                    as a fabricated profile)
     * @param jobId       the job id (resolved through job sources)
     * @return the immutable {@link OrchestrationRun} tracking snapshot
     */
    public OrchestrationRun orchestrate(Long candidateId, String jobId) {
        CandidateProfile profile = profilePersistenceService.getByIdOrThrow(candidateId).toDomain();
        Job job = resolveJob(jobId);

        AgentContext context = new AgentContext();
        context.setCandidateId(candidateId);
        context.setJobId(jobId);
        context.setCandidateProfile(profile);
        context.setJob(job);

        return orchestrator.orchestrateTracked(context);
    }

    private Job resolveJob(String jobId) {
        return jobSearchService.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }
}
