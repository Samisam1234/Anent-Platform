package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers profile-driven job discovery: when a search carries a {@code candidateProfileId}
 * and no explicit keywords, {@link JobSearchService} derives its relevance keywords from
 * the stored profile's parsed skills. This is what replaced the removed free-text
 * "Keywords &amp; Skills" field, so the parsed resume keeps driving which jobs surface.
 *
 * <p>Uses the real {@link MockJobSource} catalog and a mocked persistence layer — no DB,
 * no network, no LLM.</p>
 */
@DisplayName("JobSearchService — profile-derived discovery keywords")
class JobSearchServiceProfileKeywordsTest {

    private static CandidateProfile profileWithSkills(List<String> skills, List<String> preferredRoles) {
        return new CandidateProfile(
                "Asha Rao", "asha@example.com", null, "Hyderabad",
                List.of("B.Tech ECE"), skills, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), preferredRoles, List.of());
    }

    private static JobSearchService serviceWithProfile(CandidateProfile profile) {
        CandidateProfilePersistenceService profiles = mock(CandidateProfilePersistenceService.class);
        when(profiles.findById(anyLong()))
                .thenReturn(Optional.of(CandidateProfileEntity.fromDomain(profile)));
        return new JobSearchService(
                List.of(new MockJobSource()), new JobDeduplicationService(), profiles);
    }

    private static List<String> titles(JobSearchResult result) {
        return result.jobs().stream().map(Job::title).toList();
    }

    @Test
    @DisplayName("a hardware profile surfaces hardware roles and hides the Java-only roles")
    void hardwareProfileDiscoversHardwareJobs() {
        JobSearchService service = serviceWithProfile(
                profileWithSkills(List.of("Verilog", "RTL Design"), List.of("VLSI Design Engineer")));

        JobSearchResult result = service.search(new JobSearchRequest(
                List.of(), null, null, null, null, 100, null, 1L));

        assertFalse(result.jobs().isEmpty(), "profile keywords must still discover jobs");
        List<String> found = titles(result);
        assertTrue(found.stream().anyMatch(t -> t.contains("RTL Design")), "found=" + found);
        assertFalse(found.contains("Java Developer"), "unrelated Java-only role leaked in: " + found);
    }

    @Test
    @DisplayName("a Java profile surfaces the Java roles — discovery really follows the profile")
    void javaProfileDiscoversJavaJobs() {
        JobSearchService service = serviceWithProfile(
                profileWithSkills(List.of("Java", "Spring Boot"), List.of("Backend Engineer")));

        JobSearchResult result = service.search(new JobSearchRequest(
                List.of(), null, null, null, null, 100, null, 1L));

        assertTrue(titles(result).contains("Java Developer"), "found=" + titles(result));
    }

    @Test
    @DisplayName("explicit keywords still take precedence over profile-derived ones")
    void explicitKeywordsWin() {
        JobSearchService service = serviceWithProfile(
                profileWithSkills(List.of("Verilog", "RTL Design"), List.of()));

        JobSearchResult result = service.search(new JobSearchRequest(
                List.of("Java"), null, null, null, null, 100, null, 1L));

        assertTrue(titles(result).contains("Java Developer"), "found=" + titles(result));
    }

    @Test
    @DisplayName("no candidateProfileId → nothing is filtered out (previous behaviour preserved)")
    void withoutProfileIdNothingIsFiltered() {
        JobSearchService service = serviceWithProfile(
                profileWithSkills(List.of("Verilog"), List.of()));

        JobSearchResult unfiltered = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

        assertEquals(new MockJobSource().search(JobSearchRequest.of(List.of(), null, null, null, null, 100)).size(),
                unfiltered.jobs().size(),
                "a search without a profile id must not narrow the catalog");
    }

    @Test
    @DisplayName("legacy 2-arg constructor (no persistence) behaves exactly as before")
    void legacyConstructorUnchanged() {
        JobSearchService service =
                new JobSearchService(List.of(new MockJobSource()), new JobDeduplicationService());

        JobSearchResult result = service.search(new JobSearchRequest(
                List.of(), null, null, null, null, 100, null, 1L));

        assertEquals(new MockJobSource().search(JobSearchRequest.of(List.of(), null, null, null, null, 100)).size(),
                result.jobs().size(),
                "without a persistence layer the profile id must be ignored, not fail the search");
    }

    @Test
    @DisplayName("an enabled-but-unreachable live source does not make the result claim to be live")
    void unreachableLiveSourceIsNotReportedAsLive() {
        PublicApiJobProperties props = new PublicApiJobProperties();
        props.setEnabled(true);
        org.springframework.web.client.RestTemplate restTemplate =
                mock(org.springframework.web.client.RestTemplate.class);
        when(restTemplate.exchange(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpMethod.GET),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));
        PublicApiJobSource unreachable = new PublicApiJobSource(
                props, restTemplate, new com.fasterxml.jackson.databind.ObjectMapper());

        JobSearchService service = new JobSearchService(
                List.of(unreachable, new MockJobSource()), new JobDeduplicationService(), null);

        JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

        assertFalse(result.jobs().isEmpty(), "the mock catalog must still serve as a fallback");
        assertFalse(result.live(),
                "live must only be true when a live source actually contributed listings");
        assertTrue(result.message().contains("fall back"), "message=" + result.message());
    }

    @Test
    @DisplayName("unknown candidateProfileId degrades to an unfiltered search instead of erroring")
    void unknownProfileIdDegradesSafely() {
        CandidateProfilePersistenceService profiles = mock(CandidateProfilePersistenceService.class);
        when(profiles.findById(anyLong())).thenReturn(Optional.empty());
        JobSearchService service = new JobSearchService(
                List.of(new MockJobSource()), new JobDeduplicationService(), profiles);

        JobSearchResult result = service.search(new JobSearchRequest(
                List.of(), null, null, null, null, 100, null, 999L));

        assertFalse(result.jobs().isEmpty(), "a missing profile must not empty the result set");
    }
}
