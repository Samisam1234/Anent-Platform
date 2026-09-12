package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.CareerTrackEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the relevance pipeline: career-track filtering, negative
 * exclusions and weighted keyword relevance.
 *
 * <p>These are the scenarios that motivated the pipeline repair — a VLSI candidate must not
 * be shown Rails or service-desk roles, and a single incidental word in a job description
 * must not make an unrelated listing relevant. Everything runs against an in-memory stub
 * source: no network, no database, no LLM.</p>
 */
@DisplayName("Job relevance pipeline — track, exclusion and keyword relevance")
class JobRelevancePipelineTest {

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    /** A source that always returns the same catalog, so tests control the input exactly. */
    private static final class StubSource implements JobSource {
        private final List<Job> jobs;

        StubSource(List<Job> jobs) {
            this.jobs = jobs;
        }

        @Override
        public String getSourceName() {
            return "STUB";
        }

        @Override
        public boolean isLive() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<Job> search(JobSearchRequest request) {
            return jobs;
        }
    }

    private static CandidateProfile candidate(String name, List<String> softwareSkills,
                                              List<String> hardwareSkills, List<String> preferredRoles) {
        return new CandidateProfile(name, name.toLowerCase().replace(' ', '.') + "@example.com",
                null, "Hyderabad", List.of("B.Tech Engineering"), List.of(), List.of(), List.of(),
                List.of(), List.of(), softwareSkills, hardwareSkills, preferredRoles, List.of());
    }

    private static CandidateProfile vlsiCandidate() {
        return candidate("Vedika Rao", List.of(), List.of("Verilog", "VLSI", "UVM", "RTL Design"),
                List.of("VLSI Design Engineer"));
    }

    private static CandidateProfile embeddedCandidate() {
        return candidate("Arjun Nair", List.of(), List.of("Embedded C", "RTOS", "Firmware", "Microcontrollers"),
                List.of("Embedded Engineer"));
    }

    private static CandidateProfile softwareCandidate() {
        return candidate("Meera Iyer", List.of("Java", "Spring Boot", "PostgreSQL", "Git"), List.of(),
                List.of("Backend Engineer"));
    }

    private static CandidateProfile aiMlCandidate() {
        return candidate("Rohan Das", List.of("Python", "TensorFlow", "Machine Learning"), List.of(),
                List.of("Machine Learning Engineer"));
    }

    private static CandidateProfile emptyCandidate() {
        return candidate("Blank Profile", List.of(), List.of(), List.of());
    }

    private static Job job(String id, String title, String description,
                           List<String> requiredSkills, String employmentType) {
        return new Job(id, title, "Acme Corp", "Hyderabad, India", description, requiredSkills,
                List.of(), "1-3 years", employmentType, "2026-09-01", "STUB",
                "https://acme-corp.example.com/jobs/" + id, "PUBLIC_API", Instant.now(), null);
    }

    private static Job railsJob() {
        return job("rails-1", "Senior Ruby on Rails Engineer",
                "Build and maintain Ruby on Rails web applications backed by PostgreSQL and a React front end.",
                List.of("Ruby on Rails", "Ruby", "PostgreSQL"), "FULL_TIME");
    }

    private static Job serviceDeskJob() {
        return job("desk-1", "IT Service Desk Analyst",
                "Provide first-line technical support and ticket triage for internal end users.",
                List.of("IT Support", "Windows"), "FULL_TIME");
    }

    private static Job fpgaJob() {
        return job("fpga-1", "FPGA Design Engineer",
                "RTL design, logic synthesis and timing closure on Xilinx FPGA platforms.",
                List.of("Verilog", "FPGA", "Timing Closure"), "FULL_TIME");
    }

    private static Job firmwareJob() {
        return job("fw-1", "Embedded Firmware Engineer",
                "Bare-metal firmware for ARM Cortex-M microcontrollers using FreeRTOS and embedded C.",
                List.of("Embedded C", "RTOS", "Microcontrollers"), "FULL_TIME");
    }

    private static Job javaJob() {
        return job("java-1", "Java Spring Boot Developer",
                "Design and build REST microservices with Java and Spring Boot on PostgreSQL.",
                List.of("Java", "Spring Boot", "PostgreSQL"), "FULL_TIME");
    }

    private static Job mlJob() {
        return job("ml-1", "Machine Learning Engineer",
                "Design and train deep learning models for computer vision workloads.",
                List.of("Python", "TensorFlow", "PyTorch"), "FULL_TIME");
    }

    private static JobSearchResult searchFor(CandidateProfile profile, List<Job> catalog) {
        CandidateProfilePersistenceService profiles = mock(CandidateProfilePersistenceService.class);
        when(profiles.findById(anyLong())).thenReturn(Optional.of(CandidateProfileEntity.fromDomain(profile)));
        JobSearchService service = new JobSearchService(List.of(new StubSource(catalog)),
                new JobDeduplicationService(), profiles);
        return service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100, null, 1L));
    }

    private static List<String> ids(JobSearchResult result) {
        return result.jobs().stream().map(Job::id).toList();
    }

    // ─── A–G: track relevance end to end ──────────────────────────────────────

    @Nested
    @DisplayName("Career-track relevance")
    class TrackRelevanceTests {

        @Test
        @DisplayName("A: a VLSI candidate is not shown a Rails role")
        void vlsiCandidate_rejectsRails() {
            JobSearchResult result = searchFor(vlsiCandidate(), List.of(railsJob(), fpgaJob()));

            assertFalse(ids(result).contains("rails-1"),
                    "a Rails role is a different discipline and must not surface, got " + ids(result));
            assertTrue(ids(result).contains("fpga-1"), "the FPGA role must still surface");
        }

        @Test
        @DisplayName("B: a VLSI candidate is not shown a service desk role")
        void vlsiCandidate_rejectsServiceDesk() {
            JobSearchResult result = searchFor(vlsiCandidate(), List.of(serviceDeskJob(), fpgaJob()));

            assertFalse(ids(result).contains("desk-1"),
                    "service desk is excluded globally, got " + ids(result));
        }

        @Test
        @DisplayName("C: a VLSI candidate is shown an FPGA role")
        void vlsiCandidate_acceptsFpga() {
            JobSearchResult result = searchFor(vlsiCandidate(), List.of(fpgaJob()));

            assertEquals(List.of("fpga-1"), ids(result));
        }

        @Test
        @DisplayName("D: an embedded candidate is not shown a service desk role")
        void embeddedCandidate_rejectsServiceDesk() {
            JobSearchResult result = searchFor(embeddedCandidate(), List.of(serviceDeskJob(), firmwareJob()));

            assertFalse(ids(result).contains("desk-1"), "got " + ids(result));
        }

        @Test
        @DisplayName("E: an embedded candidate is shown a firmware role")
        void embeddedCandidate_acceptsFirmware() {
            JobSearchResult result = searchFor(embeddedCandidate(), List.of(firmwareJob()));

            assertEquals(List.of("fw-1"), ids(result));
        }

        @Test
        @DisplayName("F: a software candidate is shown a Java/Spring Boot role")
        void softwareCandidate_acceptsJava() {
            JobSearchResult result = searchFor(softwareCandidate(), List.of(javaJob()));

            assertEquals(List.of("java-1"), ids(result));
        }

        @Test
        @DisplayName("G: an AI/ML candidate is shown a machine learning role")
        void aiMlCandidate_acceptsMachineLearning() {
            JobSearchResult result = searchFor(aiMlCandidate(), List.of(mlJob(), serviceDeskJob()));

            assertTrue(ids(result).contains("ml-1"), "the ML role must surface, got " + ids(result));
            assertFalse(ids(result).contains("desk-1"), "service desk must not, got " + ids(result));
        }
    }

    // ─── H–K: keyword relevance, UNKNOWN handling, false positives ────────────

    @Nested
    @DisplayName("Keyword relevance and exclusion precision")
    class RelevanceTests {

        @Test
        @DisplayName("H: an empty profile does not let every listing through")
        void emptyKeywords_doNotPassEverything() {
            JobSearchResult result = searchFor(emptyCandidate(),
                    List.of(serviceDeskJob(), railsJob(), fpgaJob()));

            assertFalse(ids(result).contains("desk-1"),
                    "the global exclusion rules apply even with no profile signal, got " + ids(result));
            assertFalse(result.jobs().size() == 3,
                    "an empty keyword list must not blanket-accept the catalog, got " + ids(result));
        }

        @Test
        @DisplayName("I: an unclassifiable job receives a neutral, not favourable, track score")
        void unknownJob_isNotScoredFavourably() {
            Job unclassifiable = job("generic-1", "Programme Coordinator",
                    "Coordinate schedules and documentation for the team.", List.of(), "FULL_TIME");

            CareerTrackEngine engine = new CareerTrackEngine();
            CareerTrackEngine.CareerTrackEvaluation eval = engine.evaluate(softwareCandidate(), unclassifiable);

            assertEquals(CareerTrack.UNKNOWN, eval.jobTrack());
            assertNotEquals(0.8, eval.trackScore(), 0.001,
                    "an UNKNOWN job must no longer receive the old favourable 0.8");
            assertEquals(0.5, eval.trackScore(), 0.001,
                    "UNKNOWN is neutral: no bonus, but not zeroed either, got " + eval.trackScore());
        }

        @Test
        @DisplayName("J: one incidental word in a description does not make a listing relevant")
        void incidentalDescriptionWord_isNotEnough() {
            Job incidental = job("admin-1", "Administrative Coordinator",
                    "Coordinate office schedules. Familiarity with Java processes is a plus.",
                    List.of(), "FULL_TIME");

            JobRelevanceScorer scorer = new JobRelevanceScorer();

            assertFalse(scorer.isRelevant(incidental, List.of("java")),
                    "a single description-only mention must not qualify a listing");
            assertTrue(scorer.isRelevant(javaJob(), List.of("java")),
                    "a title match must still qualify");
        }

        @Test
        @DisplayName("J: several independent description mentions can still qualify a listing")
        void severalDescriptionMentions_canQualify() {
            Job substantial = job("platform-1", "Platform Engineer",
                    "Work across Java services, Spring Boot configuration and PostgreSQL tuning.",
                    List.of(), "FULL_TIME");

            JobRelevanceScorer scorer = new JobRelevanceScorer();

            assertTrue(scorer.isRelevant(substantial, List.of("java", "spring boot", "postgresql")),
                    "three independent discipline mentions are a real signal");
        }

        @Test
        @DisplayName("K: an exclusion phrase in an unrelated sentence does not reject a relevant role")
        void exclusionPhraseInProse_doesNotReject() {
            Job relevant = job("fw-2", "Embedded Firmware Engineer",
                    "This is a hands-on engineering role; it is not a sales or service desk position.",
                    List.of("Embedded C", "RTOS"), "FULL_TIME");

            NegativeJobFilter filter = new NegativeJobFilter();

            assertEquals(null, filter.exclusionReason(relevant, Set.of(CareerTrack.EMBEDDED)),
                    "only the title and structured fields may trigger an exclusion");
        }

        @Test
        @DisplayName("K: word-boundary matching does not reject a company named Salesforce")
        void wordBoundary_doesNotOverMatch() {
            Job atSalesforce = job("sw-2", "Backend Engineer",
                    "Backend services work at a large product company.",
                    List.of("Java", "Spring Boot"), "FULL_TIME");
            Job withCompany = new Job("sw-3", "Backend Engineer", "Salesforce", "Hyderabad, India",
                    "Backend services work.", List.of("Java", "Spring Boot"), List.of(), "1-3 years",
                    "FULL_TIME", "2026-09-01", "STUB", "https://acme-corp.example.com/jobs/sw-3",
                    "PUBLIC_API", Instant.now(), null);

            NegativeJobFilter filter = new NegativeJobFilter();

            assertEquals(null, filter.exclusionReason(atSalesforce, Set.of(CareerTrack.SOFTWARE)));
            assertEquals(null, filter.exclusionReason(withCompany, Set.of(CareerTrack.SOFTWARE)),
                    "'sales' must not match inside the company name Salesforce");
        }

        @Test
        @DisplayName("an unpaid placement is excluded via the structured employment type")
        void unpaidPlacement_isExcluded() {
            Job unpaid = job("int-1", "Software Engineering Intern",
                    "A learning-oriented internship placement.", List.of("Java"), "Unpaid Internship");

            NegativeJobFilter filter = new NegativeJobFilter();

            assertEquals("unpaid", filter.exclusionReason(unpaid, Set.of(CareerTrack.SOFTWARE)));
        }

        @Test
        @DisplayName("product support is excluded for software but not for embedded")
        void productSupport_isDisciplineSpecific() {
            Job support = job("sup-1", "Product Support Engineer",
                    "Support customers using the product.", List.of("Java"), "FULL_TIME");

            NegativeJobFilter filter = new NegativeJobFilter();

            assertEquals("product support", filter.exclusionReason(support, Set.of(CareerTrack.SOFTWARE)));
            assertEquals(null, filter.exclusionReason(support, Set.of(CareerTrack.EMBEDDED)),
                    "product-facing work is legitimate for embedded, per the rule set");
        }
    }

    // ─── Track classification itself ──────────────────────────────────────────

    @Nested
    @DisplayName("Career-track classification")
    class ClassificationTests {

        private final CareerTrackEngine engine = new CareerTrackEngine();

        @Test
        @DisplayName("VLSI/FPGA is its own track, not generic hardware")
        void vlsiIsItsOwnTrack() {
            assertEquals(CareerTrack.VLSI_FPGA, engine.classifyJob(fpgaJob()));
        }

        @Test
        @DisplayName("embedded is its own track, not generic hardware")
        void embeddedIsItsOwnTrack() {
            assertEquals(CareerTrack.EMBEDDED, engine.classifyJob(firmwareJob()));
        }

        @Test
        @DisplayName("AI/ML is its own track, not generic software")
        void aiMlIsItsOwnTrack() {
            assertEquals(CareerTrack.AI_ML, engine.classifyJob(mlJob()));
        }

        @Test
        @DisplayName("a plain backend role is still SOFTWARE")
        void backendIsSoftware() {
            assertEquals(CareerTrack.SOFTWARE, engine.classifyJob(javaJob()));
        }

        @Test
        @DisplayName("a coarse HARDWARE filter still accepts the finer hardware tracks")
        void coarseFilterAcceptsFineTracks() {
            assertTrue(CareerTrack.VLSI_FPGA.satisfies(CareerTrack.HARDWARE));
            assertTrue(CareerTrack.EMBEDDED.satisfies(CareerTrack.HARDWARE));
            assertTrue(CareerTrack.AI_ML.satisfies(CareerTrack.SOFTWARE));
            assertTrue(CareerTrack.MIXED.satisfies(CareerTrack.VLSI_FPGA),
                    "MIXED spans families and must not be dropped by a specific filter");
            assertFalse(CareerTrack.UNKNOWN.satisfies(CareerTrack.VLSI_FPGA),
                    "an unclassifiable listing must not enter a discipline-specific result set");
            assertFalse(CareerTrack.SOFTWARE.satisfies(CareerTrack.VLSI_FPGA));
        }
    }
}
