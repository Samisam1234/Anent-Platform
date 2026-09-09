package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchRequest;
import com.agentplatform.orchestrator.job.JobSearchResult;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deterministic unit tests for the candidate-to-job matching pipeline.
 *
 * <p>Pure JUnit 5 + Mockito: no Spring container, no database, no LLM. Most tests exercise the
 * inline {@link JobMatchingService#matchProfileAgainstJobs} path with real engines and default
 * weights (Skills .40, Role .20, Experience .15, Track .10, Location .10, Education .05).</p>
 */
class JobMatchingServiceTest {

    private JobMatchingService service;

    @BeforeEach
    void setUp() {
        service = new JobMatchingService(
                null,
                null,
                new SkillMatchingEngine(),
                new RoleMatchingEngine(),
                new LocationMatchingEngine(),
                new ExperienceMatchingEngine(),
                new EducationMatchingEngine(),
                new CareerTrackEngine(),
                new ExplanationGenerator(),
                new JobMatchingConfig()
        );
    }

    // ─── Test fixtures ────────────────────────────────────────────────────────

    private CandidateProfile softwareCandidate() {
        return profile("Alice", "Hyderabad", List.of("B.Tech Computer Science"),
                List.of("Java", "Spring Boot", "PostgreSQL", "Git"),
                List.of(), List.of("Backend Engineer"), List.of("Hyderabad"));
    }

    private CandidateProfile hardwareCandidate() {
        return profile("Bob", "Hyderabad",
                List.of("B.Tech Electronics and Communication Engineering"),
                List.of(), List.of("Verilog", "VLSI", "UVM", "RTL Design", "SystemVerilog"),
                List.of("VLSI Design Engineer"), List.of("Hyderabad"));
    }

    private CandidateProfile mixedCandidate() {
        return profile("Chloe", "Hyderabad", List.of("B.Tech Electronics and Communication Engineering"),
                List.of("Java"), List.of("Verilog", "Embedded C"),
                List.of("Embedded Systems Engineer"), List.of("Hyderabad"));
    }

    private CandidateProfile profile(String name, String location, List<String> education,
                                     List<String> softwareSkills, List<String> hardwareSkills,
                                     List<String> preferredRoles, List<String> preferredLocations) {
        return new CandidateProfile(name, name.toLowerCase() + "@example.com", "100-200",
                location, education, List.of(), List.of(), List.of(), List.of(), List.of(),
                softwareSkills, hardwareSkills, preferredRoles, preferredLocations);
    }

    private Job javaBackendFresherJob() {
        return job("j1", "Java Backend Engineer", "TechNova", "Hyderabad",
                "Build REST microservices with Java and Spring Boot.",
                List.of("Java", "Spring Boot", "PostgreSQL", "Git"),
                List.of("Docker", "Kafka"), "Fresher / 0-1 years", "Full-time");
    }

    private Job vlsiFresherJob() {
        return job("j2", "VLSI Design Engineer", "Semicon Corp", "Hyderabad",
                "RTL design and verification of SoCs using Verilog and UVM.",
                List.of("Verilog", "VLSI", "UVM", "RTL Design"),
                List.of("SystemVerilog"), "Fresher / 0-1 years", "Full-time");
    }

    private Job job(String id, String title, String company, String location, String description,
                    List<String> required, List<String> preferred, String expReq, String type) {
        return new Job(id, title, company, location, description, required, preferred, expReq, type,
                null, "mock", null, null, null);
    }

    private JobMatch singleMatch(CandidateProfile candidate, Job j) {
        JobMatchResult result = service.matchProfileAgainstJobs(candidate, List.of(j), null, null, 10);
        assertEquals(1, result.matches().size());
        return result.matches().get(0);
    }

    // ─── 1. Software full match ───────────────────────────────────────────────

    @Test
    @DisplayName("Full software match scores 100 with EXCELLENT_MATCH and all components aligned")
    void fullSoftwareMatch_shouldScore100() {
        JobMatch match = singleMatch(softwareCandidate(), javaBackendFresherJob());

        assertEquals(100, match.matchScore());
        assertEquals(RecommendationLevel.EXCELLENT_MATCH, match.recommendation());
        assertEquals(CareerTrack.SOFTWARE, match.careerTrack());
        assertEquals(List.of("Java", "Spring Boot", "PostgreSQL", "Git"), match.matchedSkills());
        assertTrue(match.missingSkills().isEmpty());
        assertTrue(match.roleMatch());
        assertTrue(match.locationMatch());
        assertFalse(match.strengths().isEmpty());
        assertTrue(match.strengths().get(0).contains("key requirements"));
        assertTrue(match.concerns().isEmpty());
        assertEquals(1.0, match.educationScore(), 0.001);
    }

    // ─── 2. Partial required skills ───────────────────────────────────────────

    @Test
    @DisplayName("Partial required skills produce the correct matched/missing split and a reduced score")
    void partialSkillMatch_splitsListsAndLowersScore() {
        CandidateProfile partial = profile("Alice", "Hyderabad", List.of("B.Tech Computer Science"),
                List.of("Java", "Spring Boot"), List.of(),
                List.of("Backend Engineer"), List.of("Hyderabad"));

        JobMatch match = singleMatch(partial, javaBackendFresherJob());

        assertEquals(List.of("Java", "Spring Boot"), match.matchedSkills());
        assertEquals(List.of("PostgreSQL", "Git"), match.missingSkills());
        assertEquals(80, match.matchScore());
        assertEquals(RecommendationLevel.STRONG_MATCH, match.recommendation());
        assertTrue(match.concerns().stream().anyMatch(c -> c.contains("Missing required")));
    }

    // ─── 3. No required skills → skill component is neutral max ──────────────

    @Test
    @DisplayName("Job with no required skills yields a full skill score without false gaps")
    void noRequiredSkills_skillScoreIsFull() {
        Job job = job("j3", "Java Platform Engineer", "Nova", "Hyderabad",
                "Platform work", List.of(), List.of("Java"),
                "1-3 years", "Full-time");

        JobMatch match = singleMatch(softwareCandidate(), job);

        assertEquals(1.0, match.skillScore(), 0.001);
        assertTrue(match.missingSkills().isEmpty());
    }

    // ─── 4. Preferred-skill bonus never exceeds 1.0 ───────────────────────────

    @Test
    @DisplayName("Preferred-skill bonus is capped so the skill score never exceeds 1.0")
    void preferredBonus_cappedAtOnePointZero() {
        Job job = job("j4", "Java Developer", "Nova", "Hyderabad",
                "Java backend",
                List.of("Java"),
                List.of("Java", "Spring Boot", "Docker"), "Fresher / 0-1 years", "Full-time");

        JobMatch match = singleMatch(softwareCandidate(), job);

        assertEquals(1.0, match.skillScore(), 0.001);
    }

    // ─── 5. Case / punctuation / alias insensitive skills ────────────────────

    @Test
    @DisplayName("Skill matching is case-, punctuation-, and alias-insensitive")
    void skillMatching_isNormalized() {
        CandidateProfile profile = profile("Alice", "Hyderabad", List.of("B.Tech Computer Science"),
                List.of("Java / J2EE", "spring-boot", "PostgreSQL"), List.of(),
                List.of("Backend Engineer"), List.of("Hyderabad"));

        Job job = job("j5", "Java Developer", "Nova", "Hyderabad",
                "Java", List.of("Java", "Spring Boot", "Postgres"),
                List.of(), "Fresher / 0-1 years", "Full-time");

        JobMatch match = singleMatch(profile, job);

        assertEquals(List.of("Java", "Spring Boot", "Postgres"), match.matchedSkills());
        assertTrue(match.missingSkills().isEmpty());
        assertEquals(1.0, match.skillScore(), 0.001);
    }

    // ─── 6. Duplicate-safe matching ───────────────────────────────────────────

    @Test
    @DisplayName("Duplicate candidate skills match once without inflating counts")
    void duplicateCandidateSkills_matchOnce() {
        CandidateProfile profile = profile("Alice", "Hyderabad", List.of("B.Tech Computer Science"),
                List.of("Java", "Core Java"), List.of(),
                List.of("Backend Engineer"), List.of("Hyderabad"));

        Job job = job("j6", "Java Developer", "Nova", "Hyderabad",
                "Java", List.of("Java"), List.of(),
                "Fresher / 0-1 years", "Full-time");

        JobMatch match = singleMatch(profile, job);
        assertEquals(List.of("Java"), match.matchedSkills());
    }

    // ─── 7. Substring guard: short/token-abuse skills do not false-match ─────

    @Test
    @DisplayName("Short single-letter tokens do not substring-match longer skills")
    void shortTokens_doNotFalseMatch() {
        CandidateProfile profile = profile("Bob", "Hyderabad", List.of("B.Tech ECE"),
                List.of(), List.of("u"), List.of(),
                List.of("Hyderabad"));

        Job job = job("j7", "Verification Engineer", "Semicon", "Hyderabad",
                "UVM", List.of("UVM"), List.of(),
                "Fresher / 0-1 years", "Full-time");

        JobMatch match = singleMatch(profile, job);
        assertEquals(List.of("UVM"), match.missingSkills());
    }

    // ─── 8. Hardware full match ───────────────────────────────────────────────

    @Test
    @DisplayName("VLSI profile matches a VLSI job at 100 with HARDWARE track")
    void hardwareProfile_fullMatch() {
        JobMatch match = singleMatch(hardwareCandidate(), vlsiFresherJob());

        assertEquals(100, match.matchScore());
        assertEquals(CareerTrack.HARDWARE, match.careerTrack());
        assertEquals(List.of("Verilog", "VLSI", "UVM", "RTL Design"), match.matchedSkills());
        assertTrue(match.roleMatch());
        assertEquals(1.0, match.educationScore(), 0.001);
        assertTrue(match.strengths().stream()
                .anyMatch(s -> s.contains("Educational background")));
    }

    // ─── 9. Cross-domain discrimination ───────────────────────────────────────

    @Test
    @DisplayName("Hardware candidate is not penalized harshly but is correctly scored low for a software role")
    void hardwareProfile_againstSoftwareJob_lowScore() {
        JobMatch match = singleMatch(hardwareCandidate(), javaBackendFresherJob());

        assertEquals(CareerTrack.SOFTWARE, match.careerTrack());
        assertFalse(match.roleMatch());
        assertTrue(match.missingSkills().containsAll(List.of("Java", "Spring Boot", "PostgreSQL", "Git")));
        assertTrue(match.matchScore() < 40);
        assertTrue(match.concerns().stream().anyMatch(c -> c.contains("Role doesn't match")));
    }

    // ─── 10. MIXED track ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Profile with software + hardware skills maps to MIXED track")
    void mixedProfile_detectsMixedTrack() {
        Job job = job("m1", "Embedded Systems Engineer", "Mechatronics", "Hyderabad",
                "embedded C and Java microservices running on ARM",
                List.of("Java", "Embedded C"), List.of(),
                "1-3 years", "Full-time");

        JobMatch match = singleMatch(mixedCandidate(), job);

        assertEquals(CareerTrack.MIXED, match.careerTrack());
        assertTrue(match.matchScore() >= 90);
    }

    // ─── 11. Fresher-friendly experience ─────────────────────────────────────

    @Test
    @DisplayName("Fresher or entry-level jobs get STRONG_MATCH experience compatibility")
    void fresherJob_experienceStrongMatch() {
        JobMatch match = singleMatch(softwareCandidate(), javaBackendFresherJob());
        assertEquals(ExperienceMatchLevel.STRONG_MATCH, match.experienceMatch());
        assertEquals(1.0, match.experienceScore(), 0.001);
    }

    // ─── 12. Senior job vs entry-level profile ───────────────────────────────

    @Test
    @DisplayName("Senior job flags NO_MATCH experience and surfaces a concern")
    void seniorJob_entryLevelProfile_raisesConcern() {
        Job seniorJob = job("s1", "Senior Java Architect", "Nova", "Hyderabad",
                "Lead architecture",
                List.of("Java", "Spring Boot", "PostgreSQL", "Git", "Kafka"),
                List.of(), "6+ years", "Full-time");

        JobMatch match = singleMatch(softwareCandidate(), seniorJob);

        assertEquals(ExperienceMatchLevel.NO_MATCH, match.experienceMatch());
        assertTrue(match.concerns().stream().anyMatch(c -> c.contains("senior-level experience")));
    }

    // ─── 13. Location matching (incl. aliases) ────────────────────────────────

    @Test
    @DisplayName("Bengaluru / Bangalore alias counts as a location match")
    void locationAlias_bengaluruBangalore() {
        CandidateProfile profile = profile("Alice", "Bengaluru", List.of("B.Tech Computer Science"),
                List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                List.of("Backend Engineer"), List.of("Bangalore"));

        Job job = job("l1", "Java Backend Engineer", "TechNova", "Bengaluru",
                "Java", List.of("Java", "Spring Boot", "PostgreSQL", "Git"),
                List.of(), "Fresher / 0-1 years", "Full-time");

        JobMatch match = singleMatch(profile, job);
        assertTrue(match.locationMatch());
        assertEquals(1.0, match.locationScore(), 0.001);
    }

    @Test
    @DisplayName("Non-matching location lowers the score and appears as a concern")
    void locationMismatch_lowersScoreAndConcern() {
        CandidateProfile profile = profile("Alice", "Hyderabad", List.of("B.Tech Computer Science"),
                List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                List.of("Backend Engineer"), List.of("Hyderabad"));

        Job job = job("l2", "Java Backend Engineer", "TechNova", "Pune",
                "Java", List.of("Java", "Spring Boot", "PostgreSQL", "Git"),
                List.of(), "Fresher / 0-1 years", "Full-time");

        JobMatch match = singleMatch(profile, job);
        assertFalse(match.locationMatch());
        assertTrue(match.concerns().stream().anyMatch(c -> c.contains("Location listed as Pune")));
    }

    @Test
    @DisplayName("Remote jobs are treated as location-compatible")
    void remoteJob_isLocationCompatible() {
        CandidateProfile profile = profile("Alice", "Hyderabad", List.of("B.Tech Computer Science"),
                List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                List.of("Backend Engineer"), List.of("Remote"));

        Job job = job("l3", "Java Backend Engineer", "TechNova", "Remote",
                "Java", List.of("Java", "Spring Boot", "PostgreSQL", "Git"),
                List.of(), "Fresher / 0-1 years", "Full-time");

        JobMatch match = singleMatch(profile, job);
        assertTrue(match.locationMatch());
        assertEquals(1.0, match.locationScore(), 0.001);
    }

    // ─── 14. Role-family matching ─────────────────────────────────────────────

    @Test
    @DisplayName("Preferred role from the same family matches a different job title")
    void roleFamilyMatch_recognized() {
        CandidateProfile profile = profile("Alice", "Hyderabad", List.of("B.Tech Computer Science"),
                List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                List.of("Software Developer"), List.of("Hyderabad"));

        Job job = job("r1", "Full Stack Developer", "Nova", "Hyderabad",
                "Web", List.of("Java", "Spring Boot", "PostgreSQL", "Git"),
                List.of(), "Fresher / 0-1 years", "Full-time");

        JobMatch match = singleMatch(profile, job);
        assertTrue(match.roleMatch());
    }

    // ─── 15. Filtering: minScore ──────────────────────────────────────────────

    @Test
    @DisplayName("minScore filter removes low-scoring matches from the result")
    void minScoreFilter_excludesLowScores() {
        JobMatchResult result = service.matchProfileAgainstJobs(
                hardwareCandidate(),
                List.of(javaBackendFresherJob(), vlsiFresherJob()),
                50, null, 10);

        assertEquals(1, result.matches().size());
        assertEquals("VLSI Design Engineer", result.matches().get(0).job().title());
    }

    // ─── 16. Filtering: track filter ──────────────────────────────────────────

    @Test
    @DisplayName("careerTrack filter keeps only the requested track (and mixed)")
    void trackFilter_keepsRequestedTrack() {
        JobMatchResult result = service.matchProfileAgainstJobs(
                hardwareCandidate(),
                List.of(javaBackendFresherJob(), vlsiFresherJob()),
                null, CareerTrack.HARDWARE, 10);

        assertEquals(1, result.matches().size());
        assertEquals(CareerTrack.HARDWARE, result.matches().get(0).careerTrack());
    }

    // ─── 17. Ranking + limit ──────────────────────────────────────────────────

    @Test
    @DisplayName("Results are ranked by score DESC and the limit is honored")
    void ranking_sortDescAndLimit() {
        Job weakJob = job("w1", "VLSI Design Engineer", "Semicon", "Pune",
                "RTL", List.of("Verilog", "VLSI", "UVM", "RTL Design", "Physical Design"),
                List.of(), "5+ years", "Full-time");
        JobMatchResult result = service.matchProfileAgainstJobs(
                hardwareCandidate(),
                List.of(weakJob, vlsiFresherJob(), javaBackendFresherJob()),
                null, null, 2);

        assertEquals(2, result.matches().size());
        assertEquals("VLSI Design Engineer", result.matches().get(0).job().title());
        assertTrue(result.matches().get(0).matchScore() >= result.matches().get(1).matchScore());
    }

    @Test
    @DisplayName("Equal-score jobs are tie-broken deterministically by title")
    void ranking_tieBreakByTitle() {
        Job matchA = job("t1", "Alpha Java Developer", "CompanyA", "Hyderabad",
                "Java", List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                "Fresher / 0-1 years", "Full-time");
        Job matchB = job("t2", "Beta Java Developer", "CompanyB", "Hyderabad",
                "Java", List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                "Fresher / 0-1 years", "Full-time");
        CandidateProfile profile = profile("Alice", "Hyderabad", List.of("B.Tech Computer Science"),
                List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                List.of("Java Developer"), List.of("Hyderabad"));

        JobMatchResult result = service.matchProfileAgainstJobs(
                profile, List.of(matchB, matchA), null, null, 10);

        assertEquals("Alpha Java Developer", result.matches().get(0).job().title());
        assertEquals("Beta Java Developer", result.matches().get(1).job().title());
    }

    // ─── 18. Empty input ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Matching against an empty job list returns an empty result without error")
    void emptyJobs_returnsEmptyResult() {
        JobMatchResult result = service.matchProfileAgainstJobs(
                softwareCandidate(), List.of(), null, null, 10);

        assertEquals(0, result.matches().size());
        assertEquals(0, result.totalJobs());
    }

    @Test
    @DisplayName("Null candidate is rejected with a 400-class IllegalArgumentException")
    void nullCandidate_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.matchProfileAgainstJobs(null, List.of(), null, null, 10));
    }

    // ─── 19. Null safety ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Profiles and jobs with null fields evaluate without exceptions")
    void nullFields_doNotThrow() {
        CandidateProfile blank = new CandidateProfile(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        Job blankJob = new Job(null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        JobMatch match = singleMatch(blank, blankJob);

        assertNotNull(match);
        assertEquals(CareerTrack.UNKNOWN, match.careerTrack());
    }

    // ─── 20. Education factor ─────────────────────────────────────────────────

    @Test
    @DisplayName("ECE education yields a full education score for hardware jobs")
    void educationEce_matchesHardwareJobs() {
        JobMatch match = singleMatch(hardwareCandidate(), vlsiFresherJob());
        assertEquals(1.0, match.educationScore(), 0.001);
    }

    @Test
    @DisplayName("Computer Science education yields a full education score for software jobs")
    void educationCs_matchesSoftwareJobs() {
        JobMatch match = singleMatch(softwareCandidate(), javaBackendFresherJob());
        assertEquals(1.0, match.educationScore(), 0.001);
    }

    @Test
    @DisplayName("Non-technical education scores low and is neutral toward the overall score")
    void educationNonTechnical_scoresLow() {
        CandidateProfile profile = profile("Dana", "Hyderabad", List.of("B.Com Commerce"),
                List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                List.of("Backend Engineer"), List.of("Hyderabad"));

        JobMatch match = singleMatch(profile, javaBackendFresherJob());

        assertEquals(0.5, match.educationScore(), 0.001);
        assertEquals(98, match.matchScore());
    }

    // ─── 21. Recommended-level thresholds ─────────────────────────────────────

    @Test
    @DisplayName("Recommendation levels map deterministically from the score band")
    void recommendationThresholds() {
        assertEquals(RecommendationLevel.EXCELLENT_MATCH, RecommendationLevel.fromScore(95));
        assertEquals(RecommendationLevel.STRONG_MATCH, RecommendationLevel.fromScore(75));
        assertEquals(RecommendationLevel.POSSIBLE_MATCH, RecommendationLevel.fromScore(62));
        assertEquals(RecommendationLevel.WEAK_MATCH, RecommendationLevel.fromScore(45));
        assertEquals(RecommendationLevel.POOR_MATCH, RecommendationLevel.fromScore(10));
    }

    // ─── 22. Configurable weights ─────────────────────────────────────────────

    @Test
    @DisplayName("JobMatchingConfig exposes spec default weights and allows overrides")
    void configDefaults_andSetters() {
        JobMatchingConfig config = new JobMatchingConfig();
        assertEquals(0.40, config.getSkillWeight(), 0.001);
        assertEquals(0.20, config.getRoleWeight(), 0.001);
        assertEquals(0.15, config.getExperienceWeight(), 0.001);
        assertEquals(0.10, config.getTrackWeight(), 0.001);
        assertEquals(0.10, config.getLocationWeight(), 0.001);
        assertEquals(0.05, config.getEducationWeight(), 0.001);

        double total = config.getSkillWeight() + config.getRoleWeight() + config.getExperienceWeight()
                + config.getTrackWeight() + config.getLocationWeight() + config.getEducationWeight();
        assertEquals(1.0, total, 0.001);

        config.setSkillWeight(0.5);
        config.setEducationWeight(0.05);
        assertEquals(0.5, config.getSkillWeight(), 0.001);
    }

    // ─── 23. Request validation ───────────────────────────────────────────────

    @Test
    @DisplayName("Illegal limit and minScore values are rejected")
    void requestValidation_rejectsBadRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> new JobMatchRequest(1L, List.of("java"), null, null, null, 0, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new JobMatchRequest(1L, List.of("java"), null, null, null, 150, null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new JobMatchRequest(1L, List.of("java"), null, null, null, 10, 101, null, null, List.of()));
    }

    // ─── 24. Stored-profile path (persistence + search mocks) ────────────────

    @Test
    @DisplayName("matchJobs resolves a stored profile by ID and searches the catalog before ranking")
    void matchJobs_storedProfilePath() {
        CandidateProfilePersistenceService persistence = mock(CandidateProfilePersistenceService.class);
        JobSearchService search = mock(JobSearchService.class);

        JobMatchingService storedService = new JobMatchingService(
                search, persistence,
                new SkillMatchingEngine(), new RoleMatchingEngine(), new LocationMatchingEngine(),
                new ExperienceMatchingEngine(), new EducationMatchingEngine(),
                new CareerTrackEngine(), new ExplanationGenerator(), new JobMatchingConfig());

        CandidateProfileEntity entity = CandidateProfileEntity.fromDomain(softwareCandidate());
        entity.setId(7L);
        when(persistence.getByIdOrThrow(7L)).thenReturn(entity);
        when(search.search(any(JobSearchRequest.class)))
                .thenReturn(new JobSearchResult(List.of(javaBackendFresherJob()), 1, "mock", false,
                        "Live job source not configured. Returning development mock data."));

        JobMatchResult result = storedService.matchJobs(JobMatchRequest.of(7L, List.of("java"), "Hyderabad", 20));

        assertEquals(7L, result.candidateProfileId());
        assertEquals("Alice", result.candidateName());
        assertEquals(1, result.matches().size());
        assertFalse(result.live());
        // Provenance is asserted via live()/source(); the message is now source-agnostic
        // so it never claims a mock catalog for a merely non-live result.
        assertTrue(result.message().contains("Matched job listings"), "message=" + result.message());
    }

    @Test
    @DisplayName("matchJobs propagates the not-found exception when the stored profile is missing")
    void matchJobs_missingProfile_throws() {
        CandidateProfilePersistenceService persistence = mock(CandidateProfilePersistenceService.class);
        when(persistence.getByIdOrThrow(999L))
                .thenThrow(new CandidateProfileNotFoundException(999L));

        JobMatchingService storedService = new JobMatchingService(
                mock(JobSearchService.class), persistence,
                new SkillMatchingEngine(), new RoleMatchingEngine(), new LocationMatchingEngine(),
                new ExperienceMatchingEngine(), new EducationMatchingEngine(),
                new CareerTrackEngine(), new ExplanationGenerator(), new JobMatchingConfig());

        assertThrows(CandidateProfileNotFoundException.class,
                () -> storedService.matchJobs(JobMatchRequest.of(999L, List.of("java"), null, 10)));
    }

    // ─── 24b. Stored-profile + supplied single job (Check Match flow) ───────

    @Test
    @DisplayName("matchJobs with stored profile + supplied single job uses the supplied job, not the catalog")
    void matchJobs_storedProfileSuppliedSingleJob() {
        CandidateProfilePersistenceService persistence = mock(CandidateProfilePersistenceService.class);
        JobSearchService search = mock(JobSearchService.class);

        JobMatchingService storedService = new JobMatchingService(
                search, persistence,
                new SkillMatchingEngine(), new RoleMatchingEngine(), new LocationMatchingEngine(),
                new ExperienceMatchingEngine(), new EducationMatchingEngine(),
                new CareerTrackEngine(), new ExplanationGenerator(), new JobMatchingConfig());

        CandidateProfileEntity entity = CandidateProfileEntity.fromDomain(softwareCandidate());
        entity.setId(7L);
        when(persistence.getByIdOrThrow(7L)).thenReturn(entity);
        // Catalog holds a different job; the supplied single job must win.
        when(search.search(any(JobSearchRequest.class)))
                .thenReturn(new JobSearchResult(List.of(vlsiFresherJob()), 1, "mock", false,
                        "Matched development mock job catalog against candidate profile."));

        JobMatchRequest request = new JobMatchRequest(
                7L, List.of(), null, null, null, 1, null, null, null,
                List.of(javaBackendFresherJob()));

        JobMatchResult result = storedService.matchJobs(request);

        assertEquals(7L, result.candidateProfileId());
        assertEquals("Alice", result.candidateName());
        assertEquals(1, result.matches().size());
        assertEquals("j1", result.matches().get(0).job().id());
        assertEquals("user-provided", result.source());
        assertFalse(result.live());
    }

    // ─── 25. Inline profile + jobs in matchJobs ───────────────────────────────

    @Test
    @DisplayName("matchJobs uses an inline profile and job list without touching persistence or search")
    void matchJobs_inlineProfileAndJobs() {
        JobMatchRequest request = new JobMatchRequest(
                7L, List.of(), null, null, null, 10, null, null,
                softwareCandidate(), List.of(javaBackendFresherJob()));

        JobMatchResult result = service.matchJobs(request);

        assertEquals(7L, result.candidateProfileId());
        assertEquals("Alice", result.candidateName());
        assertEquals(1, result.matches().size());
        assertEquals("user-provided", result.source());
        assertFalse(result.live());
    }
}