package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.matching.CareerTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the negative exclusion rules.
 *
 * <p>Marketing used to appear only in the per-discipline lists for EMBEDDED and HARDWARE.
 * A candidate classified as VLSI/FPGA, AI/ML or software therefore had no negative-filter
 * protection against a marketing role at all, and an anonymous search had none either,
 * because the per-discipline rules are not consulted when the candidate's track is unknown.
 * Marketing is now global, like service desk and sales.</p>
 */
@DisplayName("NegativeJobFilter — exclusion rules")
class NegativeJobFilterTest {

    private final NegativeJobFilter filter = new NegativeJobFilter();

    private static Job titled(String title) {
        return new Job("job-" + Math.abs(title.hashCode()), title, "Acme", "Remote",
                "A listing used to exercise the exclusion rules.",
                List.of(), List.of(), null, "FULL_TIME", "2026-01-15",
                "TEST_SOURCE", null, "TEST", Instant.now());
    }

    @Nested
    @DisplayName("marketing is excluded for every candidate")
    class MarketingIsGlobal {

        @Test
        @DisplayName("rejected for a VLSI_FPGA candidate")
        void rejectedForVlsi() {
            assertTrue(filter.isExcluded(titled("Marketing Manager"), Set.of(CareerTrack.VLSI_FPGA)),
                    "a VLSI candidate previously had no marketing rule at all");
            assertEquals("marketing",
                    filter.exclusionReason(titled("Marketing Manager"), Set.of(CareerTrack.VLSI_FPGA)));
        }

        @Test
        @DisplayName("rejected for a SOFTWARE candidate")
        void rejectedForSoftware() {
            assertTrue(filter.isExcluded(titled("Digital Marketing Specialist"),
                    Set.of(CareerTrack.SOFTWARE)));
        }

        @Test
        @DisplayName("rejected for an AI_ML candidate")
        void rejectedForAiMl() {
            assertTrue(filter.isExcluded(titled("Marketing Executive"), Set.of(CareerTrack.AI_ML)));
        }

        @Test
        @DisplayName("still rejected for the hardware tracks that already had the rule")
        void stillRejectedForHardwareTracks() {
            assertTrue(filter.isExcluded(titled("Marketing Manager"), Set.of(CareerTrack.EMBEDDED)));
            assertTrue(filter.isExcluded(titled("Marketing Manager"), Set.of(CareerTrack.HARDWARE)));
        }

        @Test
        @DisplayName("rejected when the candidate's track is unknown")
        void rejectedWhenTrackUnknown() {
            assertTrue(filter.isExcluded(titled("Marketing Manager"), Set.of()),
                    "global rules must apply without a candidate track");
            assertTrue(filter.isExcluded(titled("Marketing Manager"), Set.of(CareerTrack.UNKNOWN)));
        }

        @Test
        @DisplayName("applies with no per-track rules at all, which proves it is global")
        void marketingIsInGlobalExclusions() {
            // applicableExclusions returns nothing for an unknown track, so the only way a
            // marketing title can be rejected here is through GLOBAL_EXCLUSIONS.
            assertTrue(filter.applicableExclusions(Set.of()).isEmpty(),
                    "sanity check: an unknown track contributes no per-discipline rules");
            assertEquals("marketing",
                    filter.exclusionReason(titled("Marketing Manager"), Set.of()),
                    "so this rejection must come from the global list");
        }

        @Test
        @DisplayName("does not fire on a word that merely contains \"marketing\"")
        void doesNotMatchInsideAnotherWord() {
            assertFalse(filter.isExcluded(titled("Remarketing Operations Engineer"), Set.of()),
                    "word boundaries must be respected");
        }
    }

    @Nested
    @DisplayName("the pre-existing global rules are unchanged")
    class ExistingGlobalsPreserved {

        @Test
        @DisplayName("service desk, helpdesk, sales and the unpaid rules still apply")
        void previousGlobalRulesStillWork() {
            for (String title : List.of("IT Service Desk Analyst", "Helpdesk Technician",
                    "Help Desk Support", "Sales Executive", "Unpaid Intern",
                    "Volunteer Coordinator")) {
                assertTrue(filter.isExcluded(titled(title), Set.of()),
                        title + " must still be excluded");
            }
        }

        @Test
        @DisplayName("product support stays a per-discipline rule, not a global one")
        void productSupportRemainsPerTrack() {
            assertTrue(filter.isExcluded(titled("Product Support Engineer"),
                            Set.of(CareerTrack.VLSI_FPGA)),
                    "product support is excluded for VLSI/FPGA");
            assertFalse(filter.isExcluded(titled("Product Support Engineer"),
                            Set.of(CareerTrack.EMBEDDED)),
                    "product-facing firmware work is legitimate for embedded");
        }

        @Test
        @DisplayName("ordinary engineering titles are never excluded")
        void engineeringTitlesAreNotExcluded() {
            for (String title : List.of("Senior Verification Engineer", "Java Backend Engineer",
                    "Embedded Firmware Engineer", "Machine Learning Engineer",
                    "Technical Support Engineer")) {
                assertFalse(filter.isExcluded(titled(title), Set.of(CareerTrack.VLSI_FPGA)),
                        title + " must not be excluded");
                assertFalse(filter.isExcluded(titled(title), Set.of()),
                        title + " must not be excluded for an unknown track");
            }
        }

        @Test
        @DisplayName("description prose is never used to exclude")
        void descriptionIsNeverMatched() {
            Job job = new Job("j-desc", "Backend Engineer", "Acme", "Remote",
                    "This role partners closely with sales and marketing teams and runs a "
                            + "service desk rotation.",
                    List.of("Java"), List.of(), null, "FULL_TIME", "2026-01-15",
                    "TEST_SOURCE", null, "TEST", Instant.now());

            assertFalse(filter.isExcluded(job, Set.of(CareerTrack.SOFTWARE)),
                    "exclusion phrases in free description text must not reject the listing");
        }
    }
}
