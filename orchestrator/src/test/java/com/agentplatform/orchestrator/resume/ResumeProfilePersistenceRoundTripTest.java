package com.agentplatform.orchestrator.resume;

import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CareerTrackEvidenceListJsonConverter;
import com.agentplatform.orchestrator.resume.persistence.ResumeEvidenceListJsonConverter;
import com.agentplatform.orchestrator.resume.persistence.StringListJsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence round-trip (Phase 2 Step 2.3 §8).
 *
 * <p>Verifies that a parsed {@link CandidateProfile} carrying canonical skills,
 * software/hardware split, {@link ResumeEvidence} and {@link CareerTrackEvidence}
 * survives the exact DB column path used by {@code candidate_profiles} — the JSON
 * {@code @Converter}s plus {@link CandidateProfileEntity#fromDomain}/{@link CandidateProfileEntity#toDomain}
 * — without losing information, using the existing H2-capable configuration.
 * No new persistence framework is introduced.</p>
 */
@DisplayName("Persistence round-trip — CandidateProfile with evidence survives the DB column path")
class ResumeProfilePersistenceRoundTripTest {

    private final ResumeEvidenceListJsonConverter evidenceConverter = new ResumeEvidenceListJsonConverter();
    private final CareerTrackEvidenceListJsonConverter trackConverter = new CareerTrackEvidenceListJsonConverter();
    private final StringListJsonConverter stringListConverter = new StringListJsonConverter();

    private CandidateProfile fullProfile() {
        List<ResumeEvidence> evidence = List.of(
                new ResumeEvidence("Java", ResumeEvidence.SourceSection.SKILLS, "Java", ResumeEvidence.EvidenceStrength.STRONG),
                new ResumeEvidence("Spring Boot", ResumeEvidence.SourceSection.EXPERIENCE, "built with Spring Boot", ResumeEvidence.EvidenceStrength.MEDIUM),
                new ResumeEvidence("Verilog", ResumeEvidence.SourceSection.PROJECT, "Verilog RTL", ResumeEvidence.EvidenceStrength.MEDIUM)
        );
        List<CareerTrackEvidence> tracks = List.of(
                new CareerTrackEvidence("Software Engineering", 4.5, List.of("Java", "Spring Boot")),
                new CareerTrackEvidence("VLSI / FPGA", 2.0, List.of("Verilog"))
        );
        return new CandidateProfile(
                "Alice Johnson", "alice@example.com", "+1 555 000 0000", "Bangalore, India",
                List.of("B.Tech in Computer Science"), List.of("Java", "Spring Boot", "Verilog"),
                List.of("Software Engineer at Acme (2 years): built services"),
                List.of(), List.of("FPGA accelerator"), List.of("Oracle Certified Professional, Java"),
                List.of("Java", "Spring Boot"), List.of("Verilog"),
                List.of("Software Engineer", "VLSI / FPGA Engineer"), List.of("Bangalore, India"),
                evidence, tracks);
    }

    @Nested
    @DisplayName("JSON converters")
    class ConverterTests {

        @Test
        @DisplayName("ResumeEvidence list round-trips through JSON with all fields intact")
        void resumeEvidenceRoundTrip() {
            List<ResumeEvidence> original = fullProfile().resumeEvidence();

            String json = evidenceConverter.convertToDatabaseColumn(original);
            assertTrue(json.startsWith("["));
            List<ResumeEvidence> loaded = evidenceConverter.convertToEntityAttribute(json);

            assertEquals(original.size(), loaded.size());
            for (int i = 0; i < original.size(); i++) {
                assertEquals(original.get(i).canonicalSkill(), loaded.get(i).canonicalSkill());
                assertEquals(original.get(i).sourceSection(), loaded.get(i).sourceSection());
                assertEquals(original.get(i).matchedText(), loaded.get(i).matchedText());
                assertEquals(original.get(i).evidenceStrength(), loaded.get(i).evidenceStrength());
            }
        }

        @Test
        @DisplayName("CareerTrackEvidence list round-trips through JSON with all fields intact")
        void careerTrackRoundTrip() {
            List<CareerTrackEvidence> original = fullProfile().careerTrackEvidence();

            String json = trackConverter.convertToDatabaseColumn(original);
            assertTrue(json.startsWith("["));
            List<CareerTrackEvidence> loaded = trackConverter.convertToEntityAttribute(json);

            assertEquals(original.size(), loaded.size());
            for (int i = 0; i < original.size(); i++) {
                assertEquals(original.get(i).track(), loaded.get(i).track());
                assertEquals(original.get(i).score(), loaded.get(i).score(), 0.0001);
                assertEquals(original.get(i).contributingSkills(), loaded.get(i).contributingSkills());
            }
        }

        @Test
        @DisplayName("empty or null evidence serializes to [] and deserializes to empty list")
        void emptyEvidence() {
            assertEquals("[]", evidenceConverter.convertToDatabaseColumn(null));
            assertEquals("[]", evidenceConverter.convertToDatabaseColumn(List.of()));
            assertTrue(evidenceConverter.convertToEntityAttribute("[]").isEmpty());
            assertTrue(evidenceConverter.convertToEntityAttribute(null).isEmpty());
        }

        @Test
        @DisplayName("string lists round-trip through the string JSON converter")
        void stringListRoundTrip() {
            List<String> original = fullProfile().skills();
            String json = stringListConverter.convertToDatabaseColumn(original);
            List<String> loaded = stringListConverter.convertToEntityAttribute(json);
            assertEquals(original, loaded);
        }
    }

    @Nested
    @DisplayName("Domain ↔ entity")
    class DomainEntityTests {

        @Test
        @DisplayName("fromDomain → converters → toDomain preserves a full profile")
        void entityRoundTrip() {
            CandidateProfile original = fullProfile();

            CandidateProfileEntity entity = CandidateProfileEntity.fromDomain(original);

            // Simulate Hibernate writing and reading the JSON converter columns.
            String resumeJson = evidenceConverter.convertToDatabaseColumn(entity.getResumeEvidence());
            String trackJson = trackConverter.convertToDatabaseColumn(entity.getCareerTrackEvidence());
            entity.setResumeEvidence(evidenceConverter.convertToEntityAttribute(resumeJson));
            entity.setCareerTrackEvidence(trackConverter.convertToEntityAttribute(trackJson));

            CandidateProfile loaded = entity.toDomain();

            assertEquals(original.name(), loaded.name());
            assertEquals(original.email(), loaded.email());
            assertEquals(original.education(), loaded.education());
            assertEquals(original.skills(), loaded.skills());
            assertEquals(original.softwareSkills(), loaded.softwareSkills());
            assertEquals(original.hardwareSkills(), loaded.hardwareSkills());
            assertEquals(original.resumeEvidence(), loaded.resumeEvidence());
            assertEquals(original.careerTrackEvidence(), loaded.careerTrackEvidence());
        }

        @Test
        @DisplayName("toDomain of an empty entity yields empty lists, not null")
        void emptyEntityToDomain() {
            CandidateProfileEntity entity = new CandidateProfileEntity(
                    "A", null, null, null, null, null, null, null, null, null,
                    null, null, null, null);
            CandidateProfile domain = entity.toDomain();
            assertEquals("A", domain.name());
            assertTrue(domain.skills().isEmpty());
            assertTrue(domain.resumeEvidence().isEmpty());
            assertNull(domain.email());
        }
    }
}