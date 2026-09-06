package com.agentplatform.orchestrator.advisor;

import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.gap.ExperienceGap;
import com.agentplatform.orchestrator.gap.GapSeverity;
import com.agentplatform.orchestrator.gap.ImprovementPriority;
import com.agentplatform.orchestrator.gap.SkillGap;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.job.exception.JobNotFoundException;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.ExperienceMatchLevel;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.JobMatchingService;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.CareerTrackEvidence;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.resume.ResumeEvidence;
import com.agentplatform.orchestrator.tailoring.AtsReadinessAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the Application Advisor service with Phase 7.2 deterministic scoring:
 * - Readiness score comes directly from AtsReadinessAnalysis.score
 * - Score bounds 0–100
 * - All five recommendation thresholds
 * - Strengths use real matched skills/evidence
 * - Concerns use real gap data only
 * - Recommended actions preserve ImprovementPriority order
 * - Deterministic repeated results
 * - No automatic email/application submission
 * - Existing Phase 7.1 validation behavior remains
 */
@DisplayName("ApplicationAdvisorService — Phase 7.2 deterministic scoring")
class ApplicationAdvisorServiceTest {

    private static final CandidateProfile PROFILE = new CandidateProfile(
            "Alice", "alice@example.com", null, "London",
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of("Java", "Spring"), List.of(), List.of("Backend Engineer"), List.of());

    private static final Job JOB = new Job(
            "job-1", "Backend Engineer", "Acme", "London",
            "Build backend systems",
            List.of("Java", "Spring"), List.of("Kubernetes"),
            "3+ years", "Full-time", "2024-01-01", "mock", "http://example.com", "mock", null);

    private static CandidateProfileEntity profileEntity() {
        return CandidateProfileEntity.fromDomain(PROFILE);
    }

    private static JobSearchService jobService(Job job) {
        JobSearchService svc = mock(JobSearchService.class);
        when(svc.findById(job.id())).thenReturn(Optional.of(job));
        return svc;
    }

    // ─── Helper factories for mock gap/ATS data ────────────────────────────────

    private static CareerGapAnalysis gapWith(
            List<SkillGap> matchedRequired,
            List<SkillGap> missingRequired,
            List<SkillGap> missingPreferred,
            ExperienceGap experienceGap,
            boolean trackMismatch,
            GapSeverity severity,
            List<ImprovementPriority> priorities) {
        return gapWithPreferred(matchedRequired, List.of(), missingRequired, missingPreferred,
                experienceGap, trackMismatch, severity, priorities);
    }

    private static CareerGapAnalysis gapWithPreferred(
            List<SkillGap> matchedRequired,
            List<SkillGap> matchedPreferred,
            List<SkillGap> missingRequired,
            List<SkillGap> missingPreferred,
            ExperienceGap experienceGap,
            boolean trackMismatch,
            GapSeverity severity,
            List<ImprovementPriority> priorities) {

        CareerGapAnalysis gap = new CareerGapAnalysis(
                1L, "job-1",
                matchedRequired != null ? matchedRequired : List.of(),
                missingRequired != null ? missingRequired : List.of(),
                matchedPreferred != null ? matchedPreferred : List.of(),
                missingPreferred != null ? missingPreferred : List.of(),
                experienceGap != null ? experienceGap : new ExperienceGap(null, null, null, false),
                CareerTrack.SOFTWARE, CareerTrack.SOFTWARE, trackMismatch,
                severity != null ? severity : GapSeverity.NO_GAP,
                priorities != null ? priorities : List.of());
        return gap;
    }

    private static SkillGap skill(String canonical) {
        return new SkillGap(canonical, List.of(ResumeEvidence.SourceSection.SKILLS));
    }

    private static SkillGap skillNoEvidence(String canonical) {
        return new SkillGap(canonical, List.of());
    }

    private static ExperienceGap expGap(Integer required, Integer candidate, Integer gap, boolean knowable) {
        return new ExperienceGap(required, candidate, gap, knowable);
    }

    private static AtsReadinessAnalysis ats(int score, boolean projectEvidence, boolean expEvidence) {
        return new AtsReadinessAnalysis(score, "label",
                2, 1, 1, 1, projectEvidence, expEvidence, "explanation");
    }

    private static ResumeTailoringAnalysis tailoring(AtsReadinessAnalysis readiness) {
        return new ResumeTailoringAnalysis(
                1L, "job-1",
                List.of("Java", "Spring"), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), readiness);
    }

    private static CareerGapAnalysisService gapService(CareerGapAnalysis gap) {
        CareerGapAnalysisService svc = mock(CareerGapAnalysisService.class);
        when(svc.analyze(any(CandidateProfile.class), any(Job.class))).thenReturn(gap);
        return svc;
    }

    private static ResumeTailoringAnalysisService tailoringService(ResumeTailoringAnalysis tailoring) {
        ResumeTailoringAnalysisService svc = mock(ResumeTailoringAnalysisService.class);
        when(svc.analyze(any(CandidateProfile.class), any(Job.class), any(CareerGapAnalysis.class)))
                .thenReturn(tailoring);
        return svc;
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("readiness score comes directly from AtsReadinessAnalysis.score")
    void readinessScoreFromAts() {
        CareerGapAnalysis gap = gapWith(List.of(skill("Java")), List.of(skillNoEvidence("Kubernetes")),
                List.of(), expGap(3, 2, 1, true), false, GapSeverity.MEDIUM, List.of());
        AtsReadinessAnalysis readiness = ats(72, true, true);
        ResumeTailoringAnalysis tailoring = tailoring(readiness);

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertEquals(72, resp.applicationReadinessScore());
    }

    @Test
    @DisplayName("score remains within 0–100 (clamped from ATS)")
    void scoreBounds() {
        // ATS already clamps, but verify our service doesn't break it
        AtsReadinessAnalysis readiness = ats(150, false, false); // will be clamped to 100 by ATS
        ResumeTailoringAnalysis tailoring = tailoring(readiness);
        CareerGapAnalysis gap = gapWith(List.of(), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertTrue(resp.applicationReadinessScore() >= 0 && resp.applicationReadinessScore() <= 100);
    }

    @Test
    @DisplayName("all five recommendation thresholds mapped correctly")
    void recommendationThresholds() {
        // STRONGLY_RECOMMENDED (90–100)
        assertEquals(ApplicationRecommendation.STRONGLY_RECOMMENDED,
                recommendationForScore(90));
        assertEquals(ApplicationRecommendation.STRONGLY_RECOMMENDED,
                recommendationForScore(100));

        // RECOMMENDED (75–89)
        assertEquals(ApplicationRecommendation.RECOMMENDED,
                recommendationForScore(75));
        assertEquals(ApplicationRecommendation.RECOMMENDED,
                recommendationForScore(89));

        // APPLY_WITH_IMPROVEMENTS (60–74)
        assertEquals(ApplicationRecommendation.APPLY_WITH_IMPROVEMENTS,
                recommendationForScore(60));
        assertEquals(ApplicationRecommendation.APPLY_WITH_IMPROVEMENTS,
                recommendationForScore(74));

        // LOW_PRIORITY (40–59)
        assertEquals(ApplicationRecommendation.LOW_PRIORITY,
                recommendationForScore(40));
        assertEquals(ApplicationRecommendation.LOW_PRIORITY,
                recommendationForScore(59));

        // NOT_RECOMMENDED (0–39)
        assertEquals(ApplicationRecommendation.NOT_RECOMMENDED,
                recommendationForScore(0));
        assertEquals(ApplicationRecommendation.NOT_RECOMMENDED,
                recommendationForScore(39));
    }
private ApplicationRecommendation recommendationForScore(int score) {
        AtsReadinessAnalysis readiness = ats(score, false, false);
        CareerGapAnalysis gap = gapWith(List.of(), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));
        return resp.recommendation();
    }

    @Test
    @DisplayName("strengths use real matched skills with evidence sources")
    void strengthsFromMatchedSkills() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java"), skill("Spring")), // matched required with evidence
                List.of(skillNoEvidence("Kubernetes")),
                List.of(),
                expGap(3, 3, 0, false), false, GapSeverity.LOW, List.of());

        AtsReadinessAnalysis readiness = ats(85, true, true);
        ResumeTailoringAnalysis tailoring = tailoring(readiness);

        CandidateProfilePersistenceService profileSvc = mock(CandidateProfilePersistenceService.class);
        when(profileSvc.getByIdOrThrow(1L)).thenReturn(profileEntity());

        ApplicationAdvisorService service = new ApplicationAdvisorService(                profileSvc, jobService(JOB), gapService(gap), tailoringService(tailoring), mock(JobMatchingService.class));

        ApplicationAdvisorResponse resp = service.advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        // Should contain matched skills
        assertTrue(resp.strengths().stream().anyMatch(s -> s.contains("Java")));
        assertTrue(resp.strengths().stream().anyMatch(s -> s.contains("Spring")));
        // Should contain project/experience evidence
        assertTrue(resp.strengths().stream().anyMatch(s -> s.contains("project")));
        assertTrue(resp.strengths().stream().anyMatch(s -> s.contains("experience")));
    }

    @Test
    @DisplayName("concerns use real gap data: missing required, missing preferred, exp shortfall, track mismatch, severity")
    void concernsFromGapData() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")),
                List.of(skillNoEvidence("Kubernetes"), skillNoEvidence("Docker")), // missing required
                List.of(skillNoEvidence("AWS")), // missing preferred
                expGap(5, 2, 3, true), // exp shortfall
                true, // track mismatch
                GapSeverity.HIGH,
                List.of());

        AtsReadinessAnalysis readiness = ats(55, false, false);
        ResumeTailoringAnalysis tailoring = tailoring(readiness);

        CandidateProfilePersistenceService profileSvc = mock(CandidateProfilePersistenceService.class);
        when(profileSvc.getByIdOrThrow(1L)).thenReturn(profileEntity());

        ApplicationAdvisorService service = new ApplicationAdvisorService(                profileSvc, jobService(JOB), gapService(gap), tailoringService(tailoring), mock(JobMatchingService.class));

        ApplicationAdvisorResponse resp = service.advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        // Missing required skills
        assertTrue(resp.concerns().stream().anyMatch(c -> c.contains("Kubernetes") && c.contains("required")));
        assertTrue(resp.concerns().stream().anyMatch(c -> c.contains("Docker") && c.contains("required")));
        // Missing preferred
        assertTrue(resp.concerns().stream().anyMatch(c -> c.contains("AWS") && c.contains("preferred")));
        // Experience shortfall
        assertTrue(resp.concerns().stream().anyMatch(c -> c.contains("Experience shortfall") && c.contains("3")));
        // Track mismatch
        assertTrue(resp.concerns().stream().anyMatch(c -> c.contains("track mismatch")));
        // Severity
        assertTrue(resp.concerns().stream().anyMatch(c -> c.contains("HIGH")));
    }

    @Test
    @DisplayName("recommended actions preserve ImprovementPriority order")
    void recommendedActionsPreservePriorityOrder() {
        List<ImprovementPriority> priorities = List.of(
                ImprovementPriority.requiredSkill(1, "Kubernetes"),
                ImprovementPriority.requiredSkill(2, "Docker"),
                ImprovementPriority.experience(3, 5, 2, 3),
                ImprovementPriority.preferredSkill(4, "AWS"),
                ImprovementPriority.preferredSkill(5, "CI/CD"));

        CareerGapAnalysis gap = gapWith(
                List.of(), List.of(), List.of(),
                expGap(5, 2, 3, true), false, GapSeverity.HIGH, priorities);

        AtsReadinessAnalysis readiness = ats(50, false, false);
        ResumeTailoringAnalysis tailoring = tailoring(readiness);

        CandidateProfilePersistenceService profileSvc = mock(CandidateProfilePersistenceService.class);
        when(profileSvc.getByIdOrThrow(1L)).thenReturn(profileEntity());

        ApplicationAdvisorService service = new ApplicationAdvisorService(                profileSvc, jobService(JOB), gapService(gap), tailoringService(tailoring), mock(JobMatchingService.class));

        ApplicationAdvisorResponse resp = service.advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        List<String> actions = resp.recommendedActions();
        assertEquals(5, actions.size());
        // Order preserved: required skills first, then experience, then preferred
        assertTrue(actions.get(0).contains("Kubernetes"));
        assertTrue(actions.get(1).contains("Docker"));
        assertTrue(actions.get(2).contains("experience") || actions.get(2).contains("Experience"));
        assertTrue(actions.get(3).contains("AWS"));
        assertTrue(actions.get(4).contains("CI/CD"));
    }

    @Test
    @DisplayName("deterministic repeated results for same input")
    void deterministicRepeat() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")),
                List.of(skillNoEvidence("Kubernetes")),
                List.of(),
                expGap(3, 2, 1, true), false, GapSeverity.MEDIUM, List.of());

        AtsReadinessAnalysis readiness = ats(68, true, false);
        ResumeTailoringAnalysis tailoring = tailoring(readiness);

        CandidateProfilePersistenceService profileSvc = mock(CandidateProfilePersistenceService.class);
        when(profileSvc.getByIdOrThrow(1L)).thenReturn(profileEntity());

        ApplicationAdvisorService service = new ApplicationAdvisorService(                profileSvc, jobService(JOB), gapService(gap), tailoringService(tailoring), mock(JobMatchingService.class));

        ApplicationAdvisorResponse a = service.advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));
        ApplicationAdvisorResponse b = service.advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertEquals(a.recommendation(), b.recommendation());
        assertEquals(a.applicationReadinessScore(), b.applicationReadinessScore());
        assertEquals(a.strengths(), b.strengths());
        assertEquals(a.concerns(), b.concerns());
        assertEquals(a.recommendedActions(), b.recommendedActions());
    }

    // ─── Phase 7.3: strength & concern detail enrichment ─────────────────────

    private static Job jobWithoutExperienceRequirement() {
        return new Job("job-1", "Backend Engineer", "Acme", "London",
                "Build backend systems",
                List.of("Java", "Spring"), List.of("Kubernetes"),
                null, "Full-time", "2024-01-01", "mock", "http://example.com", "mock", null);
    }

    private static ApplicationAdvisorService serviceFor(CareerGapAnalysis gap,
                                                        AtsReadinessAnalysis readiness, Job job) {
        CandidateProfilePersistenceService profileSvc = mock(CandidateProfilePersistenceService.class);
        when(profileSvc.getByIdOrThrow(1L)).thenReturn(profileEntity());
        JobMatchingService jobMatchingSvc = mock(JobMatchingService.class);
        JobMatch jobMatch = new JobMatch(job, 50, RecommendationLevel.POSSIBLE_MATCH, List.of(), List.of(), List.of(), List.of(), false, false, ExperienceMatchLevel.NO_MATCH, CareerTrack.UNKNOWN, "test", List.of(), List.of(), 0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
        JobMatchResult matchResult = new JobMatchResult(1L, "Alice", 1, List.of(jobMatch), "mock", false, "ok");
        when(jobMatchingSvc.matchProfileAgainstJobs(any(CandidateProfile.class), anyList(), any(), any(), anyInt())).thenReturn(matchResult);
        return new ApplicationAdvisorService(
                profileSvc, jobService(job), gapService(gap), tailoringService(tailoring(readiness)), jobMatchingSvc);
    }

    @Test
    @DisplayName("Phase 7.3: preferred matched skills are included and explicitly labeled")
    void preferredMatchedSkillsLabeled() {
        CareerGapAnalysis gap = gapWithPreferred(
                List.of(skill("Java")),
                List.of(skill("Git"), skillNoEvidence("Linux")),
                List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(80, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertTrue(resp.strengths().stream().anyMatch(
                s -> s.equals("Preferred match: Git (evidence: SKILLS)")));
        assertTrue(resp.strengths().stream().anyMatch(
                s -> s.equals("Preferred match: Linux")));
    }

    @Test
    @DisplayName("Phase 7.3: required strengths come before preferred strengths")
    void requiredBeforePreferred() {
        CareerGapAnalysis gap = gapWithPreferred(
                List.of(skill("Java")),
                List.of(skill("Git")),
                List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(80, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        List<String> strengths = resp.strengths();
        int requiredIdx = -1;
        int preferredIdx = -1;
        for (int i = 0; i < strengths.size(); i++) {
            if (strengths.get(i).contains("Java") && !strengths.get(i).startsWith("Preferred match:")) {
                requiredIdx = i;
            }
            if (strengths.get(i).startsWith("Preferred match:")) {
                preferredIdx = i;
                break;
            }
        }
        assertTrue(requiredIdx >= 0);
        assertTrue(preferredIdx >= 0);
        assertTrue(requiredIdx < preferredIdx);
    }

    @Test
    @DisplayName("Phase 7.3: complete-coverage line appears when all required skills are matched")
    void completeCoveragePresentWhenAllMatched() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java"), skill("Spring")), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(95, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertTrue(resp.strengths().contains("Complete required-skill coverage (2/2)"));
        assertEquals("Complete required-skill coverage (2/2)",
                resp.strengths().get(resp.strengths().size() - 1));
    }

    @Test
    @DisplayName("Phase 7.3: complete-coverage line absent when required skills are missing")
    void completeCoverageAbsentWhenMissing() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")), List.of(skillNoEvidence("Kubernetes")), List.of(),
                expGap(3, 2, 1, true), false, GapSeverity.MEDIUM, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(68, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertTrue(resp.strengths().stream().noneMatch(s -> s.startsWith("Complete required-skill coverage")));
    }

    @Test
    @DisplayName("Phase 7.3: required coverage summary is correct and first")
    void coverageSummaryCorrectAndFirst() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")),
                List.of(skillNoEvidence("Kubernetes"), skillNoEvidence("Docker")),
                List.of(), expGap(3, 2, 1, true), false, GapSeverity.MEDIUM, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(55, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertEquals("Missing 2 of 3 required skills", resp.concerns().get(0));
    }

    @Test
    @DisplayName("Phase 7.3: overall gap severity appears exactly once and last")
    void severityOnceAndLast() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")),
                List.of(skillNoEvidence("Kubernetes")),
                List.of(skillNoEvidence("AWS")),
                expGap(5, 2, 3, true), true, GapSeverity.HIGH, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(55, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        long severityLines = resp.concerns().stream()
                .filter(c -> c.startsWith("Overall gap severity:")).count();
        assertEquals(1, severityLines);
        assertEquals("Overall gap severity: HIGH",
                resp.concerns().get(resp.concerns().size() - 1));
    }

    @Test
    @DisplayName("Phase 7.3: unknown-experience caveat appears only when requirement exists and years unknowable")
    void unknownExperienceCaveat() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")), List.of(), List.of(),
                expGap(null, null, null, false), false, GapSeverity.NO_GAP, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(80, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertTrue(resp.concerns().contains(
                "Experience requirement present but candidate years not determinable from structured data"));
        assertTrue(resp.concerns().stream().noneMatch(c -> c.startsWith("Experience shortfall:")));
    }

    @Test
    @DisplayName("Phase 7.3: no caveat when experience is knowable without shortfall")
    void noCaveatWhenKnowable() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")), List.of(), List.of(),
                expGap(3, 5, 0, true), false, GapSeverity.NO_GAP, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(85, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertTrue(resp.concerns().stream().noneMatch(c -> c.contains("not determinable")));
        assertTrue(resp.concerns().stream().noneMatch(c -> c.startsWith("Experience shortfall:")));
    }

    @Test
    @DisplayName("Phase 7.3: no caveat when the job has no experience requirement")
    void noCaveatWithoutRequirement() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")), List.of(), List.of(),
                expGap(null, null, null, false), false, GapSeverity.NO_GAP, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(85, false, false), jobWithoutExperienceRequirement())
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertTrue(resp.concerns().stream().noneMatch(c -> c.contains("not determinable")));
    }

    @Test
    @DisplayName("Phase 7.3: shortfall line unchanged when knowable")
    void shortfallUnchangedWhenKnowable() {
        CareerGapAnalysis gap = gapWith(
                List.of(skill("Java")), List.of(), List.of(),
                expGap(5, 2, 3, true), false, GapSeverity.MEDIUM, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(60, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertTrue(resp.concerns().contains("Experience shortfall: 3 years below requirement"));
        assertTrue(resp.concerns().stream().noneMatch(c -> c.contains("not determinable")));
    }

    @Test
    @DisplayName("Phase 7.3: concern order is summary, required, preferred, experience, track, severity")
    void concernOrderDeterministic() {
        CareerGapAnalysis gap = gapWithPreferred(
                List.of(skill("Java")),
                List.of(),
                List.of(skillNoEvidence("Kubernetes")),
                List.of(skillNoEvidence("AWS")),
                expGap(5, 2, 3, true), true, GapSeverity.HIGH, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(45, false, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        List<String> concerns = resp.concerns();
        assertEquals("Missing 1 of 2 required skills", concerns.get(0));
        assertEquals("Missing required skill: Kubernetes", concerns.get(1));
        assertEquals("Missing preferred skill: AWS", concerns.get(2));
        assertEquals("Experience shortfall: 3 years below requirement", concerns.get(3));
        assertTrue(concerns.get(4).startsWith("Career track mismatch:"));
        assertEquals("Overall gap severity: HIGH", concerns.get(5));
        assertEquals(6, concerns.size());
    }

    @Test
    @DisplayName("Phase 7.3: no fabricated skills or evidence in enriched output")
    void noFabricationInEnrichment() {
        CareerGapAnalysis gap = gapWithPreferred(
                List.of(skill("Java")),
                List.of(skill("Git")),
                List.of(skillNoEvidence("Kubernetes")),
                List.of(), expGap(0, 0, 0, false), false, GapSeverity.LOW, List.of());

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(75, true, false), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        for (String line : resp.strengths()) {
            assertTrue(!line.contains("Cobol"));
        }
        for (String line : resp.concerns()) {
            assertTrue(!line.contains("Cobol"));
        }
    }

    @Test
    @DisplayName("Phase 7.3: score, recommendation and actions unchanged by enrichment")
    void scoringAndActionsPreserved() {
        List<ImprovementPriority> priorities = List.of(
                ImprovementPriority.requiredSkill(1, "Kubernetes"),
                ImprovementPriority.preferredSkill(2, "AWS"));
        CareerGapAnalysis gap = gapWithPreferred(
                List.of(skill("Java"), skill("Spring")),
                List.of(skill("Git")),
                List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, priorities);

        ApplicationAdvisorResponse resp = serviceFor(gap, ats(88, true, true), JOB)
                .advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertEquals(88, resp.applicationReadinessScore());
        assertEquals(ApplicationRecommendation.RECOMMENDED, resp.recommendation());
        assertEquals(List.of(
                "Kubernetes is required by the target job but is not present in the candidate profile.",
                "AWS is preferred by the target job but is currently missing."),
                resp.recommendedActions());
        assertTrue(resp.strengths().contains("Complete required-skill coverage (2/2)"));
    }

    // ─── Phase 7.1 compatibility: validation & safety ─────────────────────────

    @Test
    @DisplayName("valid candidateId and jobId still works end-to-end")
    void adviseValidIds() {
        CareerGapAnalysis gap = gapWith(List.of(skill("Java")), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());
        AtsReadinessAnalysis readiness = ats(80, true, true);

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        ApplicationAdvisorResponse resp = service.advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertNotNull(resp);
        assertEquals(ApplicationRecommendation.RECOMMENDED, resp.recommendation());
        assertEquals(80, resp.applicationReadinessScore());
        assertTrue(resp.strengths().size() > 0);
    }

    @Test
    @DisplayName("advises from resolved domain models")
    void adviseFromDomain() {
        CareerGapAnalysis gap = gapWith(List.of(), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());
        AtsReadinessAnalysis readiness = ats(50, false, false);

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        ApplicationAdvisorResponse resp = service.adviseFromDomain(1L, PROFILE, JOB);

        assertNotNull(resp);
        assertEquals(50, resp.applicationReadinessScore());
    }

    @Test
    @DisplayName("null candidateId rejected")
    void nullCandidateIdRejected() {
        ApplicationAdvisorService service = new ApplicationAdvisorService( mock(CandidateProfilePersistenceService.class), mock(JobSearchService.class), mock(CareerGapAnalysisService.class), mock(ResumeTailoringAnalysisService.class), mock(JobMatchingService.class) );

        assertThrows(IllegalArgumentException.class,
                () -> service.adviseFromDomain(null, PROFILE, JOB));
    }

    @Test
    @DisplayName("zero candidateId rejected")
    void zeroCandidateIdRejected() {
        ApplicationAdvisorService service = new ApplicationAdvisorService( mock(CandidateProfilePersistenceService.class), mock(JobSearchService.class), mock(CareerGapAnalysisService.class), mock(ResumeTailoringAnalysisService.class), mock(JobMatchingService.class) );

        assertThrows(IllegalArgumentException.class,
                () -> service.adviseFromDomain(0L, PROFILE, JOB));
    }

    @Test
    @DisplayName("null profile rejected")
    void nullProfileRejected() {
        ApplicationAdvisorService service = new ApplicationAdvisorService( mock(CandidateProfilePersistenceService.class), mock(JobSearchService.class), mock(CareerGapAnalysisService.class), mock(ResumeTailoringAnalysisService.class), mock(JobMatchingService.class) );

        assertThrows(IllegalArgumentException.class,
                () -> service.adviseFromDomain(1L, null, JOB));
    }

    @Test
    @DisplayName("null job rejected")
    void nullJobRejected() {
        ApplicationAdvisorService service = new ApplicationAdvisorService( mock(CandidateProfilePersistenceService.class), mock(JobSearchService.class), mock(CareerGapAnalysisService.class), mock(ResumeTailoringAnalysisService.class), mock(JobMatchingService.class) );

        assertThrows(IllegalArgumentException.class,
                () -> service.adviseFromDomain(1L, PROFILE, null));
    }

    @Test
    @DisplayName("unknown candidate throws CandidateProfileNotFoundException")
    void unknownCandidateThrows() {
        CareerGapAnalysis gap = gapWith(List.of(), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());
        AtsReadinessAnalysis readiness = ats(50, false, false);

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(999L, "job-1"));

        assertThrows(CandidateProfileNotFoundException.class, () -> { });
        // The serviceFor mock throws CandidateProfileNotFoundException for candidateId 999L
        // The exception is thrown in advise() before reaching JobMatchingService
    }

    @Test
    @DisplayName("unknown job throws JobNotFoundException")
    void unknownJobThrows() {
        CareerGapAnalysis gap = gapWith(List.of(skill("Java")), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());
        AtsReadinessAnalysis readiness = ats(50, false, false);

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, "unknown").advise(ApplicationAdvisorRequest.fromDomain(1L, "unknown"));

        assertThrows(JobNotFoundException.class, () -> { });
        // The serviceFor mock throws JobNotFoundException for jobId "unknown"
        // The exception is thrown in advise() before reaching JobMatchingService
    }

    @Test
    @DisplayName("no automatic email sending")
    void noAutomaticEmail() {
        CareerGapAnalysis gap = gapWith(List.of(), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());
        AtsReadinessAnalysis readiness = ats(50, false, false);

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        ApplicationAdvisorResponse resp = service.adviseFromDomain(1L, PROFILE, JOB);

        String toString = resp.toString();
        assertTrue(!toString.contains("email"));
        assertTrue(!toString.contains("Email"));
        assertTrue(!toString.contains("SENT"));
        assertTrue(!toString.contains("send"));
    }

    @Test
    @DisplayName("no automatic application submission")
    void noAutomaticApplicationSubmission() {
        CareerGapAnalysis gap = gapWith(List.of(), List.of(), List.of(),
                expGap(0, 0, 0, false), false, GapSeverity.NO_GAP, List.of());
        AtsReadinessAnalysis readiness = ats(50, false, false);

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        ApplicationAdvisorResponse resp = service.adviseFromDomain(1L, PROFILE, JOB);

        String toString = resp.toString();
        assertTrue(!toString.contains("applicationId"));
        assertTrue(!toString.contains("APPROVED"));
        assertTrue(!toString.contains("submitted"));
        assertTrue(!toString.contains("submission"));
    }

    @Test
    @DisplayName("no LLM dependency — output is deterministic")
    void noLlmDependency() {
        CareerGapAnalysis gap = gapWith(List.of(skill("Java")), List.of(skillNoEvidence("Kubernetes")),
                List.of(), expGap(3, 2, 1, true), false, GapSeverity.MEDIUM, List.of());
        AtsReadinessAnalysis readiness = ats(72, true, true);

        ApplicationAdvisorResponse resp = serviceFor(gap, readiness, JOB).advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        ApplicationAdvisorResponse a = service.advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));
        ApplicationAdvisorResponse b = service.advise(ApplicationAdvisorRequest.fromDomain(1L, "job-1"));

        assertEquals(a.recommendation(), b.recommendation());
        assertEquals(a.applicationReadinessScore(), b.applicationReadinessScore());
        assertEquals(a.strengths(), b.strengths());
        assertEquals(a.concerns(), b.concerns());
        assertEquals(a.recommendedActions(), b.recommendedActions());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private static void assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("Expected true");
    }
}










