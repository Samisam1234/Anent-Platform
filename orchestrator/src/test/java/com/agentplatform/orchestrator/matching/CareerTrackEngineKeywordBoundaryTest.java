package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Regression coverage for career-track keyword matching.
 *
 * <p>Keyword matching used to be plain {@code text.contains(kw)}. Short acronyms in the
 * taxonomy then matched inside ordinary English: {@code "sta"} is a VLSI keyword and also a
 * substring of <em>standard</em>, <em>status</em>, <em>start</em>, <em>state</em> and
 * <em>establish</em>. Since nearly every job description uses one of those words, nearly
 * every listing produced a VLSI hit — which classified service-desk, product-support,
 * marketing and sales roles as VLSI/FPGA (so they survived the career-track filter for a
 * hardware candidate) and could classify a genuine software role as VLSI/FPGA (so it was
 * wrongly rejected for a software candidate).</p>
 *
 * <p>Every case below is a classification assertion rather than a direct call into the
 * matcher, because the matcher is an implementation detail; what matters is the track a
 * listing ends up in.</p>
 */
@DisplayName("CareerTrackEngine — keyword word-boundary matching")
class CareerTrackEngineKeywordBoundaryTest {

    private final CareerTrackEngine engine = new CareerTrackEngine();

    private static Job job(String title, String description) {
        return new Job("job-" + Math.abs(title.hashCode()), title, "Acme", "Remote",
                description, List.of(), List.of(), null, "FULL_TIME", "2026-01-15",
                "TEST_SOURCE", null, "TEST", Instant.now());
    }

    @Nested
    @DisplayName("short acronyms must not match inside ordinary words")
    class SubstringFalsePositives {

        @Test
        @DisplayName("\"standard\" produces no VLSI hit from the keyword \"sta\"")
        void standardIsNotSta() {
            CareerTrack track = engine.classifyJob(job("Operations Coordinator",
                    "Follow standard operating procedures across the team."));

            assertNotEquals(CareerTrack.VLSI_FPGA, track,
                    "\"standard\" must not be read as the VLSI acronym STA");
            assertEquals(CareerTrack.UNKNOWN, track, "no genuine discipline signal here");
        }

        @Test
        @DisplayName("\"status\", \"start\", \"state\" and \"establish\" produce no VLSI hit")
        void statusStartStateEstablishAreNotSta() {
            CareerTrack track = engine.classifyJob(job("Programme Coordinator",
                    "Establish documentation, publish status updates, start reviews, "
                            + "and keep the estate inventory in a consistent state."));

            assertNotEquals(CareerTrack.VLSI_FPGA, track);
            assertNotEquals(CareerTrack.HARDWARE, track);
            assertEquals(CareerTrack.UNKNOWN, track);
        }

        @Test
        @DisplayName("a service desk listing is no longer classified VLSI/FPGA")
        void serviceDeskIsNotVlsi() {
            CareerTrack track = engine.classifyJob(job("IT Service Desk Analyst",
                    "Provide first-line support. Standard shift patterns apply."));

            assertNotEquals(CareerTrack.VLSI_FPGA, track,
                    "the incidental \"sta\" in \"standard\" used to make this a VLSI role");
        }

        @Test
        @DisplayName("\"api\" does not match inside \"rapid\" or \"capital\"")
        void apiIsNotInsideRapidOrCapital() {
            CareerTrack track = engine.classifyJob(job("Rapid Capital Analyst",
                    "Deliver rapid analysis for capital planning cycles."));

            assertNotEquals(CareerTrack.SOFTWARE, track,
                    "neither \"rapid\" nor \"capital\" contains the standalone word API");
            assertEquals(CareerTrack.UNKNOWN, track);
        }

        @Test
        @DisplayName("\"arm\" does not match inside \"farmer\"")
        void armIsNotInsideFarmer() {
            CareerTrack track = engine.classifyJob(job("Agricultural Systems Coordinator",
                    "Support farmer outreach programmes."));

            assertNotEquals(CareerTrack.HARDWARE, track);
            assertEquals(CareerTrack.UNKNOWN, track);
        }
    }

    @Nested
    @DisplayName("genuine technical keywords still match")
    class TruePositivesPreserved {

        @Test
        @DisplayName("a standalone \"sta\" in a real VLSI sentence still classifies VLSI/FPGA")
        void standaloneStaStillMatches() {
            CareerTrack track = engine.classifyJob(job("STA Engineer",
                    "Perform static timing analysis (STA) and own timing closure."));

            assertEquals(CareerTrack.VLSI_FPGA, track,
                    "STA is intentionally part of the VLSI taxonomy and must keep matching");
        }

        @Test
        @DisplayName("a standalone \"API\" still classifies SOFTWARE")
        void standaloneApiStillMatches() {
            CareerTrack track = engine.classifyJob(job("API Engineer",
                    "Design and expose REST APIs for partner integrations."));

            assertEquals(CareerTrack.SOFTWARE, track);
        }

        @Test
        @DisplayName("\"ARM\" still classifies embedded")
        void standaloneArmStillMatches() {
            CareerTrack track = engine.classifyJob(job("Firmware Engineer",
                    "Bare-metal firmware for ARM Cortex-M parts running an RTOS."));

            assertEquals(CareerTrack.EMBEDDED, track);
        }

        @Test
        @DisplayName("keywords containing punctuation still match (\"bare-metal\", \"scikit-learn\")")
        void punctuatedKeywordsStillMatch() {
            assertEquals(CareerTrack.EMBEDDED, engine.classifyJob(job("Embedded Engineer",
                            "Bare-metal development on constrained targets.")),
                    "a hyphenated keyword must keep matching its hyphenated form");

            assertEquals(CareerTrack.AI_ML, engine.classifyJob(job("ML Engineer",
                            "Builds classifiers with scikit-learn and owns model training.")),
                    "a hyphenated ML keyword must keep matching");
        }
    }

    @Nested
    @DisplayName("no false rejection of real software roles")
    class NoFalseRejection {

        @Test
        @DisplayName("a genuine software job that says \"standard\" is not classified VLSI")
        void softwareJobWithStandardStaysSoftware() {
            CareerTrack track = engine.classifyJob(job("Java Backend Engineer",
                    "Build standard Spring Boot microservices using standard REST APIs "
                            + "and PostgreSQL."));

            assertEquals(CareerTrack.SOFTWARE, track,
                    "\"standard\" must not pull a software role into the hardware family");
            assertNotEquals(CareerTrack.VLSI_FPGA, track);
            assertNotEquals(CareerTrack.MIXED, track);
        }

        @Test
        @DisplayName("a genuine software job mentioning status and state stays SOFTWARE")
        void softwareJobWithStatusStaysSoftware() {
            CareerTrack track = engine.classifyJob(job("Backend Developer",
                    "Own service state machines, expose status endpoints, and start "
                            + "deployments from a Java and Spring Boot codebase."));

            assertEquals(CareerTrack.SOFTWARE, track);
        }
    }
}
