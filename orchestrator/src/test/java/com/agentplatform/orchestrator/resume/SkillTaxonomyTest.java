package com.agentplatform.orchestrator.resume;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillTaxonomy — canonical skill map")
class SkillTaxonomyTest {

    @Nested
    @DisplayName("normalize() — canonical names")
    class NormalizeTests {

        @Test
        @DisplayName("null and blank → empty string")
        void nullAndBlank() {
            assertEquals("", SkillTaxonomy.normalize(null));
            assertEquals("", SkillTaxonomy.normalize(""));
            assertEquals("", SkillTaxonomy.normalize("   "));
        }

        @Test
        @DisplayName("canonical names map to themselves")
        void canonicalNames() {
            assertEquals("Java", SkillTaxonomy.normalize("Java"));
            assertEquals("Spring Boot", SkillTaxonomy.normalize("Spring Boot"));
            assertEquals("PostgreSQL", SkillTaxonomy.normalize("PostgreSQL"));
        }

        @Test
        @DisplayName("aliases resolve to canonical names")
        void aliases() {
            assertEquals("JavaScript", SkillTaxonomy.normalize("JS"));
            assertEquals("PostgreSQL", SkillTaxonomy.normalize("postgres"));
            assertEquals("Spring Boot", SkillTaxonomy.normalize("springboot"));
            assertEquals("Spring Boot", SkillTaxonomy.normalize("Spring Boot framework"));
            assertEquals("SystemVerilog", SkillTaxonomy.normalize("system verilog"));
            assertEquals("Verilog", SkillTaxonomy.normalize("verilog hdl"));
            assertEquals("FPGA", SkillTaxonomy.normalize("fpga design"));
        }

        @Test
        @DisplayName("case insensitive")
        void caseInsensitive() {
            assertEquals(SkillTaxonomy.normalize("Java"), SkillTaxonomy.normalize("JAVA"));
            assertEquals(SkillTaxonomy.normalize("java"), SkillTaxonomy.normalize("Java"));
            assertEquals(SkillTaxonomy.normalize("spring boot"), SkillTaxonomy.normalize("SPRING BOOT"));
        }

        @Test
        @DisplayName("unknown skills → cleaned lowercase form")
        void unknownSkills() {
            assertFalse(SkillTaxonomy.normalize("SAP").isEmpty());
            assertEquals("sap", SkillTaxonomy.normalize("SAP"));
            assertEquals("unknown thing", SkillTaxonomy.normalize("Unknown Thing"));
        }
    }

    @Nested
    @DisplayName("normalizeAll()")
    class NormalizeAllTests {

        @Test
        @DisplayName("null collection → empty list")
        void nullCollection() {
            assertEquals(List.of(), SkillTaxonomy.normalizeAll(null));
        }

        @Test
        @DisplayName("removes duplicates and case variants")
        void removesDuplicates() {
            List<String> result = SkillTaxonomy.normalizeAll(List.of("Java", "JAVA", "Core Java", "java"));
            assertEquals(1, result.size());
            assertEquals("Java", result.get(0));
        }

        @Test
        @DisplayName("resolves aliases to single canonical")
        void resolvesAliases() {
            List<String> result = SkillTaxonomy.normalizeAll(List.of("Spring Boot", "springboot", "Spring Boot framework"));
            assertEquals(List.of("Spring Boot"), result);
        }

        @Test
        @DisplayName("sorts alphabetically and drops empties")
        void sortsAndDropsEmpties() {
            List<String> result = SkillTaxonomy.normalizeAll(List.of("Docker", "", "Git", "  ", "Java"));
            assertEquals(List.of("Docker", "Git", "Java"), result);
        }
    }

    @Nested
    @DisplayName("findMatches()")
    class FindMatchesTests {

        @Test
        @DisplayName("null and blank → empty list")
        void nullAndBlank() {
            assertEquals(List.of(), SkillTaxonomy.findMatches(null));
            assertEquals(List.of(), SkillTaxonomy.findMatches(""));
        }

        @Test
        @DisplayName("finds skills in a resume text")
        void findsSkillsInText() {
            String resume = "Skills: Java, Spring Boot, PostgreSQL\n"
                    + "Experience: built microservices with REST APIs and Docker";
            List<String> found = SkillTaxonomy.findMatches(resume);
            assertTrue(found.contains("Java"));
            assertTrue(found.contains("Spring Boot"));
            assertTrue(found.contains("PostgreSQL"));
            assertTrue(found.contains("Microservices"));
            assertTrue(found.contains("REST API"));
            assertTrue(found.contains("Docker"));
        }

        @Test
        @DisplayName("returns empty for non-taxonomy text")
        void noMatches() {
            assertEquals(List.of(), SkillTaxonomy.findMatches("This resume has no technical content at all."));
        }
    }

    @Nested
    @DisplayName("getCategory()")
    class GetCategoryTests {

        @Test
        @DisplayName("category lookup for known skills")
        void knownCategories() {
            assertEquals(SkillTaxonomy.Category.SOFTWARE, SkillTaxonomy.getCategory("Java"));
            assertEquals(SkillTaxonomy.Category.SOFTWARE, SkillTaxonomy.getCategory("Spring Boot"));
            assertEquals(SkillTaxonomy.Category.PROGRAMMING_AI, SkillTaxonomy.getCategory("Python"));
            assertEquals(SkillTaxonomy.Category.EMBEDDED, SkillTaxonomy.getCategory("Arduino"));
            assertEquals(SkillTaxonomy.Category.VLSI_FPGA, SkillTaxonomy.getCategory("Verilog"));
            assertEquals(SkillTaxonomy.Category.COMMUNICATION, SkillTaxonomy.getCategory("DSP"));
        }

        @Test
        @DisplayName("unknown or null → null")
        void unknownOrNull() {
            assertNull(SkillTaxonomy.getCategory(null));
            assertNull(SkillTaxonomy.getCategory("SAP"));
            assertNull(SkillTaxonomy.getCategory("not a skill"));
        }
    }

    @Nested
    @DisplayName("skillsInCategory()")
    class SkillsInCategoryTests {

        @Test
        @DisplayName("returns the canonical skills of a category")
        void categorySkills() {
            List<String> software = SkillTaxonomy.skillsInCategory(SkillTaxonomy.Category.SOFTWARE);
            assertTrue(software.contains("Java"));
            assertTrue(software.contains("Spring Boot"));
            assertTrue(software.contains("Docker"));
        }

        @Test
        @DisplayName("null category → empty list")
        void nullCategory() {
            assertEquals(List.of(), SkillTaxonomy.skillsInCategory(null));
        }
    }

    @Nested
    @DisplayName("allCanonicalSkills()")
    class AllCanonicalTests {

        @Test
        @DisplayName("contains the required categories")
        void containsAllCategories() {
            Set<String> all = SkillTaxonomy.allCanonicalSkills();
            assertTrue(all.contains("Java"));
            assertTrue(all.contains("Spring Boot"));
            assertTrue(all.contains("Verilog"));
            assertTrue(all.contains("Arduino"));
            assertTrue(all.contains("Python"));
            assertTrue(all.contains("DSP"));
        }
    }

    @Nested
    @DisplayName("False positive resistance")
    class FalsePositiveTests {

        @Test
        @DisplayName("short aliases do not over-match in text")
        void noOverMatch() {
            // "C" should not match "CSS" or "C++"; "JS" should not be spawned from arbitrary words
            assertFalse(SkillTaxonomy.findMatches("I know CSS and enjoy coffee").contains("C"));
            assertFalse(SkillTaxonomy.findMatches("CSS").contains("C"));
            assertNull(SkillTaxonomy.getCategory("coffee"));
        }
    }
}
