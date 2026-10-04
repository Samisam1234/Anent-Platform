package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused Phase 12.3 coverage: the effective {@link JobSearchRequest} each active
 * {@link JobSource} actually receives. Profile-derived discovery must forward a real,
 * job-oriented query (not twelve raw skills, and never a blank keyword), and every
 * caller-supplied filter must travel with it.
 */
@DisplayName("JobSearchService — effective request propagation to sources")
class JobSearchServicePropagationTest {

    private static final class CapturingSource implements JobSource {
        private JobSearchRequest lastRequest;

        @Override public String getSourceName() { return "CAPTURE"; }
        @Override public boolean isLive() { return true; }
        @Override public boolean isAvailable() { return true; }
        @Override public List<Job> search(JobSearchRequest request) {
            this.lastRequest = request;
            return List.of();
        }
    }

    private static CandidateProfile profile(String skills, String preferredRole) {
        return new CandidateProfile(
                "Asha Rao", "asha@example.com", null, "Hyderabad",
                List.of("B.Tech ECE"), List.of(skills), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(preferredRole), List.of());
    }

    private static JobSearchService serviceWithProfile(CandidateProfile profile,
                                                       JobSource source) {
        CandidateProfilePersistenceService profiles = mock(CandidateProfilePersistenceService.class);
        when(profiles.findById(anyLong()))
                .thenReturn(Optional.of(CandidateProfileEntity.fromDomain(profile)));
        return new JobSearchService(List.of(source), new JobDeduplicationService(), profiles);
    }

    @Test
    @DisplayName("a profile-driven search forwards one job-oriented query to the source")
    void derivedQueryReachesSource() {
        CapturingSource source = new CapturingSource();
        JobSearchService service = serviceWithProfile(
                profile("Java", "Backend Engineer"), source);

        service.search(JobSearchRequest.of(List.of(), "Hyderabad", "Mid", "Full-time", null, 25,
                null, 1L));

        JobSearchRequest received = source.lastRequest;
        assertEquals(List.of("Software Engineer"), received.keywords(),
                "the source must receive the derived job-oriented query, not twelve raw skills");
        assertEquals("Hyderabad", received.location());
        assertEquals("Mid", received.experience());
        assertEquals("Full-time", received.employmentType());
        assertEquals(25, received.limit());
        assertEquals(1L, received.candidateProfileId());
    }

    @Test
    @DisplayName("an anonymous search forwards the original empty request — no blank keyword leaks upstream")
    void anonymousSearchDoesNotLeakBlankKeyword() {
        CapturingSource source = new CapturingSource();
        JobSearchService service = new JobSearchService(List.of(source), new JobDeduplicationService());

        service.search(JobSearchRequest.of(List.of(), null, null, null, null, 20));

        assertTrue(source.lastRequest.keywords().isEmpty(),
                "nothing to derive must mean no keyword at all, never a single blank one");
        assertEquals(20, source.lastRequest.limit());
    }

    @Test
    @DisplayName("explicit caller keywords are forwarded verbatim and win over the profile")
    void explicitKeywordsForwardedVerbatim() {
        CapturingSource source = new CapturingSource();
        JobSearchService service = serviceWithProfile(profile("Verilog", "VLSI Design Engineer"), source);

        service.search(JobSearchRequest.of(List.of("Kubernetes"), null, null, null, null, 20,
                null, 1L));

        assertEquals(List.of("Kubernetes"), source.lastRequest.keywords(),
                "explicit keywords supersede the profile-derived query");
    }

    @Test
    @DisplayName("a missing profile id still forwards the caller's own keywords")
    void missingProfileForwardsExplicitKeywords() {
        CapturingSource source = new CapturingSource();
        JobSearchService service = serviceWithProfile(profile("Java", "Backend Engineer"), source);

        service.search(JobSearchRequest.of(List.of("Python"), null, null, null, null, 20, null, 999L));

        assertEquals(List.of("Python"), source.lastRequest.keywords());
    }

    @Test
    @DisplayName("the job-oriented query is the track's role phrase alone — no invented or appended tokens")
    void derivedQueryUsesNothingOutsideProfileEvidence() {
        CandidateProfile profile = profile("Java", "Backend Engineer");
        String query = JobSearchService.buildJobOrientedQuery(
                profile, Set.of(CareerTrack.SOFTWARE), List.of("Java"));

        // Exactly the role phrase. The live tools match `keyword` as a phrase, so appending a
        // skill ("Software Engineer Java") retrieves nothing upstream.
        assertEquals("Software Engineer", query);
        assertFalse(query.contains("Kubernetes"), "a skill absent from the profile must never appear");
        assertFalse(query.contains("Blockchain"), "a skill absent from the profile must never appear");

        assertEquals("", JobSearchService.buildJobOrientedQuery(null, Set.of(), List.of()),
                "an anonymous derivation stays blank so the caller forwards no keyword");
    }
}