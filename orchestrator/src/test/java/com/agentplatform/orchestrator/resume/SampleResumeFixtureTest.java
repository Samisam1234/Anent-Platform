package com.agentplatform.orchestrator.resume;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The synthetic resume fixture is a real DOCX on the test classpath, so a broken or drifted
 * artifact would silently break the browser E2E scripts in {@code scripts/e2e/}. This test
 * pins the artifact to {@link SampleResumeFixture} and checks that it parses into the expected
 * deterministic profile — no LLM, no network, no database (Cleanup Batch 5).
 */
@DisplayName("SampleResumeFixture — synthetic DOCX fixture stays valid and parses deterministically")
class SampleResumeFixtureTest {

    private static final String FIXTURE_PATH = "src/test/resources/fixtures/sample-resume.docx";

    private final ResumeParserService parser = new ResumeParserService();

    private final DeterministicCandidateProfileBuilder builder = new DeterministicCandidateProfileBuilder();

    private byte[] trackedFixture() throws IOException {
        Path path = Path.of(FIXTURE_PATH);
        assertTrue(Files.exists(path), "tracked fixture missing: " + path.toAbsolutePath()
                + " — regenerate it with SampleResumeFixture (see scripts/e2e/README.md)");
        try (InputStream in = Files.newInputStream(path)) {
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("tracked file is a valid DOCX and yields the same text as the fixture definition")
    void trackedFixtureMatchesDefinition() throws IOException {
        byte[] tracked = trackedFixture();

        assertArrayEquals(new byte[]{80, 75, 3, 4}, java.util.Arrays.copyOf(tracked, 4),
                "fixture must start with the DOCX (PK\\003\\004) magic bytes");
        assertEquals(parser.extractText(SampleResumeFixture.docx()).trim(),
                parser.extractText(tracked).trim(),
                "tracked fixture has drifted from SampleResumeFixture.LINES — regenerate it");
    }

    @Test
    @DisplayName("parses into the expected deterministic profile")
    void parsesIntoDeterministicProfile() throws IOException {
        CandidateProfile profile = builder.build(parser.extractText(trackedFixture()));

        assertEquals(SampleResumeFixture.NAME, profile.name());
        assertEquals(SampleResumeFixture.EMAIL, profile.email());
        assertEquals(SampleResumeFixture.PHONE, profile.phone());
        assertEquals("Remote", profile.location());
        assertTrue(profile.skills().containsAll(
                        java.util.List.of("Java", "Spring Boot", "Python", "SQL", "Docker", "PostgreSQL", "Git")),
                "expected software skills, got: " + profile.skills());
        assertTrue(profile.education().stream().anyMatch(line -> line.contains("Example State University")),
                "education missing, got: " + profile.education());
        assertFalse(profile.experience().isEmpty(), "experience section must not be empty");
        assertFalse(profile.projects().isEmpty(), "projects section must not be empty");
        assertTrue(profile.certifications().stream().anyMatch(line -> line.contains("AWS Certified Developer Associate")),
                "certifications missing, got: " + profile.certifications());
        assertTrue(profile.resumeEvidence().size() >= profile.skills().size(),
                "every skill must carry resume evidence");
    }

    @Test
    @DisplayName("carries no personal data from the retired CV")
    void containsNoPersonalData() throws IOException {
        String text = parser.extractText(trackedFixture());

        for (String forbidden : new String[]{"Samiuddin", "svgsami7", "MOHAMMAD ABDUL", "example@gmail.com"}) {
            assertFalse(text.toLowerCase().contains(forbidden.toLowerCase()),
                    "fixture must not contain personal data: " + forbidden);
        }
    }
}
