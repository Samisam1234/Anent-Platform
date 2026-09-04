package com.agentplatform.orchestrator.resume;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeterministicCandidateProfileBuilder — deterministic resume parsing")
class DeterministicCandidateProfileBuilderTest {

    private final DeterministicCandidateProfileBuilder builder =
            new DeterministicCandidateProfileBuilder();

    private CandidateProfile build(String resume) {
        return builder.build(resume);
    }

    // ─── 1. Software-only skills ──────────────────────────────────────────────

    @Nested
    @DisplayName("Software-only resume")
    class SoftwareOnlyTests {

        @Test
        @DisplayName("extracts canonical software skills and track")
        void softwareOnly() {
            String resume = """
                    Alice Johnson
                    alice@example.com
                    Skills
                    Java, Spring Boot, PostgreSQL, Git, Docker, REST API, Maven
                    Experience
                    Software Engineer at Acme (2 years): built microservices
                    """;
            CandidateProfile profile = build(resume);
            assertTrue(profile.skills().contains("Java"));
            assertTrue(profile.skills().contains("Spring Boot"));
            assertTrue(profile.skills().contains("PostgreSQL"));
            assertTrue(profile.skills().contains("Git"));
            assertTrue(profile.skills().contains("Docker"));
            assertTrue(profile.skills().contains("REST API"));
            assertTrue(profile.skills().contains("Microservices"));
            assertFalse(profile.skills().contains("Verilog"));
            assertFalse(profile.skills().contains("Arduino"));
            assertNotNull(profile.experience());
        }
    }

    // ─── 2. VLSI-only skills ──────────────────────────────────────────────────

    @Nested
    @DisplayName("VLSI-only resume")
    class VlsiOnlyTests {

        @Test
        @DisplayName("extracts canonical VLSI skills")
        void vlsiOnly() {
            String resume = """
                    Bob Chen
                    bob@example.com
                    Technical Skills
                    Verilog, SystemVerilog, UVM, FPGA, Static Timing Analysis
                    Projects
                    RTL design of an AXI interconnect
                    """;
            CandidateProfile profile = build(resume);
            assertTrue(profile.skills().contains("Verilog"));
            assertTrue(profile.skills().contains("SystemVerilog"));
            assertTrue(profile.skills().contains("UVM"));
            assertTrue(profile.skills().contains("FPGA"));
            assertFalse(profile.skills().contains("Java"));
            assertFalse(profile.skills().contains("Arduino"));
            assertNotNull(profile.projects());
        }
    }

    // ─── 3. Embedded-only skills ──────────────────────────────────────────────

    @Nested
    @DisplayName("Embedded-only resume")
    class EmbeddedOnlyTests {

        @Test
        @DisplayName("extracts canonical embedded skills")
        void embeddedOnly() {
            String resume = """
                    Carol Diaz
                    carol@example.com
                    Skills
                    Embedded Systems, Arduino, Microcontrollers, Firmware, I2C, SPI, UART
                    Experience
                    Firmware engineer: bare-metal on ARM Cortex-M
                    """;
            CandidateProfile profile = build(resume);
            assertTrue(profile.skills().contains("Embedded Systems"));
            assertTrue(profile.skills().contains("Arduino"));
            assertTrue(profile.skills().contains("Microcontrollers"));
            assertTrue(profile.skills().contains("Firmware"));
            assertTrue(profile.skills().contains("I2C"));
            assertTrue(profile.skills().contains("SPI"));
            assertTrue(profile.skills().contains("UART"));
            assertFalse(profile.skills().contains("Verilog"));
        }
    }

    // ─── 4. Mixed Software + VLSI ─────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed Software + VLSI resume")
    class MixedSoftwareVlsiTests {

        @Test
        @DisplayName("extracts both software and VLSI skills")
        void mixedSoftwareVlsi() {
            String resume = """
                    David Kim
                    david@example.com
                    Skills
                    Java, Spring Boot, Verilog, SystemVerilog, Git
                    Projects
                    Microservices backend and FPGA-based accelerator
                    """;
            CandidateProfile profile = build(resume);
            assertTrue(profile.skills().contains("Java"));
            assertTrue(profile.skills().contains("Spring Boot"));
            assertTrue(profile.skills().contains("Verilog"));
            assertTrue(profile.skills().contains("SystemVerilog"));
            assertTrue(profile.skills().contains("Git"));
        }
    }

    // ─── 5. Mixed Software + Embedded ─────────────────────────────────────────

    @Nested
    @DisplayName("Mixed Software + Embedded resume")
    class MixedSoftwareEmbeddedTests {

        @Test
        @DisplayName("extracts both software and embedded skills")
        void mixedSoftwareEmbedded() {
            String resume = """
                    Elena Garcia
                    elena@example.com
                    Skills: Python, C, Embedded Systems, Arduino
                    Projects: built an IoT sensor node
                    """;
            CandidateProfile profile = build(resume);
            assertTrue(profile.skills().contains("Python"));
            assertTrue(profile.skills().contains("C"));
            assertTrue(profile.skills().contains("Embedded Systems"));
            assertTrue(profile.skills().contains("Arduino"));
        }
    }

    // ─── 6. Mixed ECE + VLSI ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed ECE + VLSI resume")
    class MixedEceVlsiTests {

        @Test
        @DisplayName("extracts ECE and VLSI skills")
        void mixedEceVlsi() {
            String resume = """
                    Frank Lopez
                    frank@example.com
                    Skills: Digital Electronics, Analog Electronics, MATLAB, Verilog, RTL Design
                    Education: B.E. Electronics and Communication Engineering
                    """;
            CandidateProfile profile = build(resume);
            assertTrue(profile.skills().contains("Digital Electronics"));
            assertTrue(profile.skills().contains("Analog Electronics"));
            assertTrue(profile.skills().contains("MATLAB"));
            assertTrue(profile.skills().contains("Verilog"));
            assertTrue(profile.skills().contains("RTL Design"));
        }
    }

    // ─── 7. Project-derived skills ────────────────────────────────────────────

    @Nested
    @DisplayName("Project-derived skills")
    class ProjectDerivedTests {

        @Test
        @DisplayName("skills mentioned only in a project line still get extracted")
        void projectDerived() {
            String resume = """
                    Grace Ho
                    grace@example.com
                    Experience
                    Software Engineer at TechCorp (3 years)
                    Projects
                    Built a recommendation system using Python and Machine Learning
                    Deployed containerized services with Docker and Kubernetes
                    """;
            CandidateProfile profile = build(resume);
            assertTrue(profile.skills().contains("Python"));
            assertTrue(profile.skills().contains("Machine Learning"));
            assertTrue(profile.skills().contains("Docker"));
        }
    }

    // ─── 8. Skills appearing multiple times ───────────────────────────────────

    @Nested
    @DisplayName("Duplicate skills")
    class DuplicateTests {

        @Test
        @DisplayName("skills appearing multiple times are deduplicated")
        void duplicatesRemoved() {
            String resume = """
                    Henry Wu
                    henry@example.com
                    Skills: Java, Spring Boot, Java, Docker, SPRING BOOT
                    Experience: wrote Java everywhere
                    """;
            CandidateProfile profile = build(resume);
            long javaCount = profile.skills().stream().filter(s -> s.equals("Java")).count();
            long springCount = profile.skills().stream().filter(s -> s.equals("Spring Boot")).count();
            assertEquals(1, javaCount);
            assertEquals(1, springCount);
        }
    }

    // ─── 9. Unknown / non-taxonomy words ──────────────────────────────────────

    @Nested
    @DisplayName("Unknown / non-taxonomy content")
    class UnknownWordsTests {

        @Test
        @DisplayName("non-taxonomy words are not extracted as skills")
        void unknownWordsOmitted() {
            String resume = """
                    Ivy Smith
                    ivy@example.com
                    Skills: Cooking, Gardening, Painting
                    Experience: managed a team
                    """;
            CandidateProfile profile = build(resume);
            assertFalse(profile.skills().contains("Cooking"));
            assertFalse(profile.skills().contains("Gardening"));
            assertTrue(profile.skills().isEmpty() || !profile.skills().contains("Painting"));
        }
    }

    // ─── Career track detection ───────────────────────────────────────────────

    @Nested
    @DisplayName("Career track detection")
    class TrackDetectionTests {

        @Test
        @DisplayName("software-only skills produce software role")
        void softwareTrack() {
            CandidateProfile profile = build("""
                    John Doe
                    Skills: Java, Spring Boot, SQL, Git, Docker
                    """);
            assertTrue(profile.preferredRoles().contains("Software Engineer"));
        }

        @Test
        @DisplayName("VLSI skills produce VLSI/FPGA role")
        void vlsiTrack() {
            CandidateProfile profile = build("""
                    Jane Roe
                    Skills: Verilog, SystemVerilog, UVM, ASIC
                    """);
            assertTrue(profile.preferredRoles().contains("VLSI / FPGA Engineer"));
        }

        @Test
        @DisplayName("embedded skills produce embedded role")
        void embeddedTrack() {
            CandidateProfile profile = build("""
                    Sam Lee
                    Skills: Arduino, Microcontrollers, Firmware, UART
                    """);
            assertTrue(profile.preferredRoles().contains("Embedded Systems Engineer"));
        }

        @Test
        @DisplayName("AI/ML skills produce AI/ML role")
        void aiMlTrack() {
            CandidateProfile profile = build("""
                    Zoe Adams
                    Skills: Python, Machine Learning, Neural Networks, LangChain4j
                    """);
            assertTrue(profile.preferredRoles().contains("AI / ML Engineer"));
        }

        @Test
        @DisplayName("training is not inferred purely from degree name")
        void notJustFromDegree() {
            CandidateProfile profile = build("""
                    Test Candidate
                    Education: B.E. Electronics and Communication Engineering
                    """);
            // No skill evidence → no track-based roles should be inferred
            assertTrue(profile.preferredRoles().isEmpty());
        }

        @Test
        @DisplayName("no skills → no track roles")
        void noSkillsNoTracks() {
            CandidateProfile profile = build("""
                    Nobody
                    Relevant experience managing customer relationships.
                    """);
            assertTrue(profile.preferredRoles().isEmpty());
        }

        @Test
        @DisplayName("mixed software + VLSI yields both roles")
        void mixedTracks() {
            CandidateProfile profile = build("""
                    Ada Lovelace
                    Skills: Java, Spring Boot, Verilog, SystemVerilog, Git
                    """);
            assertTrue(profile.preferredRoles().contains("Software Engineer"));
            assertTrue(profile.preferredRoles().contains("VLSI / FPGA Engineer"));
        }
    }

    // ─── Resume evidence (Phase 2 Step 2.2) ─────────────────────────────────

    @Nested
    @DisplayName("Resume evidence extraction")
    class ResumeEvidenceTests {

        private ResumeEvidence evidenceFor(CandidateProfile profile, String canonicalSkill) {
            return profile.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals(canonicalSkill))
                    .findFirst().orElse(null);
        }

        @Test
        @DisplayName("skills listed in a Skills section are strong evidence")
        void skillsSectionEvidence() {
            CandidateProfile profile = build("""
                    Alice Johnson
                    Skills
                    Java, Spring Boot, PostgreSQL
                    """);
            ResumeEvidence java = evidenceFor(profile, "Java");
            assertEquals(ResumeEvidence.SourceSection.SKILLS, java.sourceSection());
            assertEquals(ResumeEvidence.EvidenceStrength.STRONG, java.evidenceStrength());
            assertFalse(java.matchedText().isBlank());
        }

        @Test
        @DisplayName("skills observed only in a project are medium evidence, project-sourced")
        void projectDerivedEvidence() {
            CandidateProfile profile = build("""
                    Grace Ho
                    Projects
                    Built a recommendation system using Python and Machine Learning
                    """);
            ResumeEvidence python = evidenceFor(profile, "Python");
            assertNotNull(python);
            assertEquals(ResumeEvidence.SourceSection.PROJECT, python.sourceSection());
            assertEquals(ResumeEvidence.EvidenceStrength.MEDIUM, python.evidenceStrength());
        }

        @Test
        @DisplayName("skills observed in education are weak evidence")
        void educationDerivedEvidence() {
            CandidateProfile profile = build("""
                    Frank Lopez
                    Education
                    Studied Digital Electronics and MATLAB fundamentals
                    """);
            ResumeEvidence digital = evidenceFor(profile, "Digital Electronics");
            assertEquals(ResumeEvidence.SourceSection.EDUCATION, digital.sourceSection());
            assertEquals(ResumeEvidence.EvidenceStrength.WEAK, digital.evidenceStrength());
        }

        @Test
        @DisplayName("a skill in multiple sections is collapsed into one entry with the strongest section")
        void multiSectionDedupUsesStrongest() {
            CandidateProfile profile = build("""
                    Henry Wu
                    Skills: Java, Docker
                    Projects: containerized services with Docker
                    """);
            List<ResumeEvidence> dockerEvidences = profile.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals("Docker")).toList();
            assertEquals(1, dockerEvidences.size());
            assertEquals(ResumeEvidence.SourceSection.SKILLS, dockerEvidences.get(0).sourceSection());
        }

        @Test
        @DisplayName("evidence is deduplicated per canonical skill")
        void evidenceIsUniquePerSkill() {
            CandidateProfile profile = build("""
                    Henry Wu
                    Skills: Java, Spring Boot
                    Experience: wrote Java everywhere
                    """);
            long javaEvidenceCount = profile.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals("Java")).count();
            assertEquals(1, javaEvidenceCount);
        }

        @Test
        @DisplayName("no taxonomy skills means no evidence and no fabrication")
        void noSkillsNoEvidence() {
            CandidateProfile profile = build("""
                    Nobody
                    Relevant experience managing customer relationships.
                    """);
            assertTrue(profile.resumeEvidence().isEmpty());
            assertTrue(profile.careerTrackEvidence().isEmpty());
        }

        @Test
        @DisplayName("career track evidence carries track, score, and contributing skills")
        void trackEvidence() {
            CandidateProfile profile = build("""
                    Ada Lovelace
                    Skills: Java, Spring Boot, Verilog, SystemVerilog
                    """);
            CareerTrackEvidence software = profile.careerTrackEvidence().stream()
                    .filter(e -> e.track().equals("Software Engineering")).findFirst().orElse(null);
            assertNotNull(software);
            assertTrue(software.contributingSkills().contains("Java"));
            assertTrue(software.score() >= 2.0);
        }
    }

    // ─── Resume evidence: extended coverage (Phase 2 Step 2.2) ───────────────

    @Nested
    @DisplayName("Resume evidence — extended section & track coverage")
    class AdditionalEvidenceTests {

        private ResumeEvidence evidenceFor(CandidateProfile profile, String canonicalSkill) {
            return profile.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals(canonicalSkill))
                    .findFirst().orElse(null);
        }

        @Test
        @DisplayName("skills under a 'Technical Skills' heading are strong, skills-sourced evidence")
        void technicalSkillsSectionEvidence() {
            CandidateProfile profile = build("""
                    Bob Chen
                    Technical Skills
                    Verilog, SystemVerilog, FPGA, Static Timing Analysis
                    """);
            ResumeEvidence verilog = evidenceFor(profile, "Verilog");
            assertNotNull(verilog);
            assertEquals(ResumeEvidence.SourceSection.SKILLS, verilog.sourceSection());
            assertEquals(ResumeEvidence.EvidenceStrength.STRONG, verilog.evidenceStrength());
        }

        @Test
        @DisplayName("skills under a 'Technologies' heading are treated as a skills section")
        void technologiesHeadingIsSkills() {
            CandidateProfile profile = build("""
                    Grace Ho
                    Technologies: Java, Spring Boot, PostgreSQL
                    """);
            ResumeEvidence java = evidenceFor(profile, "Java");
            assertNotNull(java);
            assertEquals(ResumeEvidence.SourceSection.SKILLS, java.sourceSection());
        }

        @Test
        @DisplayName("skills observed in a Work Experience section are medium, experience-sourced evidence")
        void experienceSectionEvidence() {
            CandidateProfile profile = build("""
                    Carol Diaz
                    Work Experience
                    Built payment microservices with Spring Boot and PostgreSQL
                    """);
            ResumeEvidence spring = evidenceFor(profile, "Spring Boot");
            assertNotNull(spring);
            assertEquals(ResumeEvidence.SourceSection.EXPERIENCE, spring.sourceSection());
            assertEquals(ResumeEvidence.EvidenceStrength.MEDIUM, spring.evidenceStrength());
        }

        @Test
        @DisplayName("skills observed in a Certifications section are strong, certification-sourced evidence")
        void certificationSectionEvidence() {
            CandidateProfile profile = build("""
                    Henry Wu
                    Certifications
                    Certified Java Developer, AWS Certified Developer
                    """);
            ResumeEvidence java = evidenceFor(profile, "Java");
            assertNotNull(java);
            assertEquals(ResumeEvidence.SourceSection.CERTIFICATION, java.sourceSection());
            assertEquals(ResumeEvidence.EvidenceStrength.STRONG, java.evidenceStrength());
        }

        @Test
        @DisplayName("a skill seen in multiple sections collapses to a single entry with the strongest section")
        void multiSectionCollapsesToStrongest() {
            CandidateProfile profile = build("""
                    Elena Garcia
                    Skills: Docker
                    Experience: deployed Docker in production
                    Projects: containerized microservices with Docker
                    """);
            List<ResumeEvidence> docker = profile.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals("Docker")).toList();
            assertEquals(1, docker.size());
            assertEquals(ResumeEvidence.SourceSection.SKILLS, docker.get(0).sourceSection());
        }

        @Test
        @DisplayName("each canonical skill yields exactly one evidence record (no uncontrolled duplicates)")
        void evidenceIsUniquePerSkillAcrossSections() {
            CandidateProfile profile = build("""
                    David Kim
                    Skills: Java, Java
                    Experience: Java development
                    Projects: wrote Java services
                    """);
            long javaCount = profile.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals("Java")).count();
            assertEquals(1, javaCount);
        }

        @Test
        @DisplayName("VLSI/FPGA track evidence lists Verilog, FPGA, Quartus Prime and RTL Design as contributors")
        void vlsiTrackEvidenceExample() {
            CandidateProfile profile = build("""
                    Sam Lee
                    Skills: Verilog, FPGA, Quartus Prime, RTL Design
                    """);
            CareerTrackEvidence vlsi = profile.careerTrackEvidence().stream()
                    .filter(e -> e.track().equals("VLSI / FPGA")).findFirst().orElse(null);
            assertNotNull(vlsi);
            assertTrue(vlsi.contributingSkills().contains("Verilog"));
            assertTrue(vlsi.contributingSkills().contains("FPGA"));
            assertTrue(vlsi.contributingSkills().contains("Quartus Prime"));
            assertTrue(vlsi.contributingSkills().contains("RTL Design"));
            assertEquals(8.0, vlsi.score(), 0.001);
        }

        @Test
        @DisplayName("lines before any recognized heading are attributed to the UNKNOWN section")
        void unheadedContentIsUnknown() {
            CandidateProfile profile = build("""
                    Zoe Adams
                    Passionate about Java and Spring Boot development
                    """);
            ResumeEvidence java = evidenceFor(profile, "Java");
            assertNotNull(java);
            assertEquals(ResumeEvidence.SourceSection.UNKNOWN, java.sourceSection());
            assertEquals(ResumeEvidence.EvidenceStrength.WEAK, java.evidenceStrength());
        }

        @Test
        @DisplayName("non-taxonomy words produce no evidence and nothing is fabricated")
        void noFabricatedEvidence() {
            CandidateProfile profile = build("""
                    Ivy Smith
                    Skills: Cooking, Gardening, Painting
                    """);
            assertTrue(profile.resumeEvidence().isEmpty());
        }

        @Test
        @DisplayName("real-world formatted resume produces correct evidence across varied headings")
        void realWorldFormattedResume() {
            CandidateProfile profile = build("""
                    Priya Nair
                    priya.nair@example.com
                    Professional Summary
                    Software engineer specialized in Java microservices.
                    Work Experience
                    Backend Engineer, FinTech Ltd. — built Spring Boot and PostgreSQL services
                    Academic Projects
                    Hospital Management System using Spring Boot and PostgreSQL
                    Certifications
                    Oracle Certified Professional, Java
                    Education
                    B.Tech in Computer Science
                    """);
// Spring Boot appears in experience and a project; it must
                    // collapse to a single record (project is the stronger section).
            List<ResumeEvidence> spring = profile.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals("Spring Boot")).toList();
            assertEquals(1, spring.size());
            assertEquals(ResumeEvidence.SourceSection.PROJECT, spring.get(0).sourceSection());

            // PostgreSQL is seen in experience and a project; one record, project-sourced.
            ResumeEvidence postgres = evidenceFor(profile, "PostgreSQL");
            assertNotNull(postgres);
            assertEquals(1, profile.resumeEvidence().stream()
                    .filter(e -> e.canonicalSkill().equals("PostgreSQL")).count());
            assertEquals(ResumeEvidence.SourceSection.PROJECT, postgres.sourceSection());
            assertTrue(profile.skills().contains("Spring Boot"));
            assertTrue(profile.skills().contains("PostgreSQL"));
            assertTrue(profile.skills().contains("Java"));
        }
    }
}
