package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.application.ApplicationEmailDraft;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerImprovementPlan;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchResult;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight, mutable context that carries references to existing domain
 * models through the orchestration pipeline, and acts as the run-local agent
 * memory for a single orchestration invocation.
 *
 * <p>Run-local memory: typed references to the structured results produced by
 * earlier agents in the same run (candidate profile, job/discovery result,
 * matching result, gap analysis, tailoring, drafts…). Later agents read these
 * from the context instead of re-computing or reconstructing the information.
 * This memory is short-lived — it exists only for the current
 * {@code orchestrateTracked(context)} invocation and is eligible for garbage
 * collection once it returns. Nothing is persisted automatically; there is no
 * cross-run memory, no user memory, and no long-term memory.</p>
 *
 * <p>Authoritative services remain the source of truth; the context only stores
 * their outputs. The context deliberately reuses the existing immutable domain
 * records (no duplication of large strings) and stores no PII beyond what those
 * records already contain.</p>
 *
 * <p>The context is not thread-safe and is intended for a single sequential
 * orchestration run. No concurrent access is expected. Mutable collections are
 * never exposed directly — reads return defensive copies.</p>
 *
 * <p>Run-local bounds: at most {@link #MAX_STORED_AGENT_RESULTS} agent results
 * are retained (there are exactly five agent types), and each stored result's
 * message is capped to {@link #MAX_RESULT_MESSAGE_LENGTH} characters. No
 * unbounded lists and no recursive object graphs are stored.</p>
 */
public class AgentContext {

    /** Upper bound on the number of per-agent results this run may retain. */
    public static final int MAX_STORED_AGENT_RESULTS = 5;

    /** Upper bound on the length of a stored result's message (keeps memory bounded). */
    public static final int MAX_RESULT_MESSAGE_LENGTH = 512;

    private Long candidateId;
    private String jobId;

    /** Raw resume text extracted upstream (for {@code ResumeAgent} to build a profile). */
    private String resumeText;

    private CandidateProfile candidateProfile;
    private Job job;
    private JobSearchResult jobSearchResult;
    private JobMatchResult jobMatchResult;
    private CareerGapAnalysis careerGapAnalysis;
    private CareerImprovementPlan improvementPlan;
    private ResumeTailoringAnalysis tailoringAnalysis;
    private TailoredResumeDraft tailoredDraft;
    private ApplicationEmailDraft applicationDraft;

    private final Map<AgentType, AgentResult> agentResults = new LinkedHashMap<>();

    /**
     * Remaining AI reasoning calls allowed for this run (resource control).
     * Defaults to the orchestrator's per-run budget ({@link CareerAgentOrchestrator#MAX_AI_CALLS}).
     */
    private int aiCallsRemaining = CareerAgentOrchestrator.MAX_AI_CALLS;

    public AgentContext() {
    }

    /** Returns how many AI reasoning calls remain in this run. */
    public int aiCallsRemaining() {
        return aiCallsRemaining;
    }

    /** Sets the AI call budget for this run (used by tests / callers). */
    public void setAiCallsRemaining(int budget) {
        this.aiCallsRemaining = Math.max(0, budget);
    }

    /**
     * Attempts to consume one AI reasoning call from the run budget.
     *
     * @return {@code true} if a call slot was available and consumed,
     *         {@code false} (without decrementing) when the budget is exhausted.
     */
    public synchronized boolean consumeAiCall() {
        if (aiCallsRemaining <= 0) {
            return false;
        }
        aiCallsRemaining--;
        return true;
    }

    /**
     * Remaining controlled tool calls allowed for this run (orchestration-wide
     * budget) and per-agent usage. Defaults to the limits defined on
     * {@link AgentToolOrchestrator}.
     */
    private int toolCallsRemaining = AgentToolOrchestrator.MAX_TOOL_CALLS_PER_ORCHESTRATION;

    private final Map<AgentType, Integer> agentToolUsage = new LinkedHashMap<>();

    /** Returns how many tool calls remain for this orchestration run. */
    public int toolCallsRemaining() {
        return toolCallsRemaining;
    }

    /** Sets the orchestration-wide tool budget (used by tests / callers). */
    public void setToolCallsRemaining(int budget) {
        this.toolCallsRemaining = Math.max(0, budget);
    }

    /** Returns the number of tool calls already used by a given agent in this run. */
    public int agentToolCalls(AgentType agentType) {
        if (agentType == null) {
            return 0;
        }
        return agentToolUsage.getOrDefault(agentType, 0);
    }

    /**
     * Attempts to consume one tool call for {@code agentType}, enforcing BOTH the
     * per-agent limit ({@link AgentToolOrchestrator#MAX_TOOL_CALLS_PER_AGENT}) and
     * the orchestration-wide limit ({@link AgentToolOrchestrator#MAX_TOOL_CALLS_PER_ORCHESTRATION}).
     *
     * @return {@code true} if a call slot was consumed, {@code false} (without
     *         decrementing) when either limit is exhausted.
     */
    public synchronized boolean consumeToolCall(AgentType agentType) {
        if (agentType == null || toolCallsRemaining <= 0) {
            return false;
        }
        int used = agentToolUsage.getOrDefault(agentType, 0);
        if (used >= AgentToolOrchestrator.MAX_TOOL_CALLS_PER_AGENT) {
            return false;
        }
        agentToolUsage.put(agentType, used + 1);
        toolCallsRemaining--;
        return true;
    }

    public Long candidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public String jobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String resumeText() {
        return resumeText;
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

    public CandidateProfile candidateProfile() {
        return candidateProfile;
    }

    public void setCandidateProfile(CandidateProfile candidateProfile) {
        this.candidateProfile = candidateProfile;
    }

    public Job job() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public JobSearchResult jobSearchResult() {
        return jobSearchResult;
    }

    public void setJobSearchResult(JobSearchResult jobSearchResult) {
        this.jobSearchResult = jobSearchResult;
    }

    public JobMatchResult jobMatchResult() {
        return jobMatchResult;
    }

    public void setJobMatchResult(JobMatchResult jobMatchResult) {
        this.jobMatchResult = jobMatchResult;
    }

    public CareerGapAnalysis careerGapAnalysis() {
        return careerGapAnalysis;
    }

    public void setCareerGapAnalysis(CareerGapAnalysis careerGapAnalysis) {
        this.careerGapAnalysis = careerGapAnalysis;
    }

    public CareerImprovementPlan improvementPlan() {
        return improvementPlan;
    }

    public void setImprovementPlan(CareerImprovementPlan improvementPlan) {
        this.improvementPlan = improvementPlan;
    }

    public ResumeTailoringAnalysis tailoringAnalysis() {
        return tailoringAnalysis;
    }

    public void setTailoringAnalysis(ResumeTailoringAnalysis tailoringAnalysis) {
        this.tailoringAnalysis = tailoringAnalysis;
    }

    public TailoredResumeDraft tailoredDraft() {
        return tailoredDraft;
    }

    public void setTailoredDraft(TailoredResumeDraft tailoredDraft) {
        this.tailoredDraft = tailoredDraft;
    }

    public ApplicationEmailDraft applicationDraft() {
        return applicationDraft;
    }

    public void setApplicationDraft(ApplicationEmailDraft applicationDraft) {
        this.applicationDraft = applicationDraft;
    }

    /**
     * Stores the result of an executed agent into the run-local memory for later
     * agents / inspection.
     *
     * <p>Stores a defensive copy whose message is bounded to
     * {@link #MAX_RESULT_MESSAGE_LENGTH}; the structured {@code output} reference
     * is shared (immutable domain records, not duplicated). The count of distinct
     * stored agent types is capped at {@link #MAX_STORED_AGENT_RESULTS}. Recording
     * a result for an already-stored agent type overwrites the previous entry
     * (e.g. the transient RUNNING placeholder).</p>
     *
     * @param result the agent result to store (may be a failure or a skip)
     * @return {@code true} if stored, {@code false} if it was invalid or the store
     *         was already full
     */
    public boolean record(AgentResult result) {
        if (result == null || result.agentType() == null) {
            return false;
        }
        if (!agentResults.containsKey(result.agentType())
                && agentResults.size() >= MAX_STORED_AGENT_RESULTS) {
            return false;
        }
        AgentResult stored = new AgentResult(
                result.agentType(),
                result.status(),
                result.success(),
                boundedMessage(result.message()),
                result.output(),
                result.errorCode(),
                result.startedAt(),
                result.completedAt());
        agentResults.put(result.agentType(), stored);
        return true;
    }

    public AgentResult resultOf(AgentType type) {
        return agentResults.get(type);
    }

    /** Whether the run-local memory already holds a result for the given agent. */
    public boolean hasStoredResult(AgentType type) {
        return type != null && agentResults.containsKey(type);
    }

    /** Number of distinct agent results currently in the run-local memory. */
    public int storedAgentCount() {
        return agentResults.size();
    }

    /** The configured maximum number of stored agent results. */
    public int maxStoredAgentResults() {
        return MAX_STORED_AGENT_RESULTS;
    }

    /** Whether no agent results are currently stored. */
    public boolean hasNoStoredResults() {
        return agentResults.isEmpty();
    }

    /**
     * Returns the recorded agent results as a read-only, defensive copy (ordered
     * by execution). Callers cannot mutate the underlying store. Exposed as a
     * typed {@link AgentType} &rarr; {@link AgentResult} map, never a raw
     * {@code Map&lt;String,Object&gt;}.
     */
    public Map<AgentType, AgentResult> agentResults() {
        return Map.copyOf(agentResults);
    }

    /**
     * Resets the run-local memory so the context can be reused for a fresh run.
     * A new orchestration is expected to start from a clean context (the
     * orchestrator does so); this provides an explicit lifecycle boundary and
     * guards against cross-run leakage.
     */
    public void clearStoredResults() {
        agentResults.clear();
    }

    /**
     * Whether every recorded agent that ran completed successfully.
     */
    public boolean allCompleted() {
        if (agentResults.isEmpty()) {
            return false;
        }
        for (AgentResult r : agentResults.values()) {
            if (r.status() != AgentStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private static String boundedMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MAX_RESULT_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_RESULT_MESSAGE_LENGTH);
    }
}
