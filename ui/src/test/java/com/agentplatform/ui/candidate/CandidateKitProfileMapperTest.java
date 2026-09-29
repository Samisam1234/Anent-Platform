package com.agentplatform.ui.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.ui.dto.CandidateKitProfileDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for the Apply Kit allowlist projection. No Spring context: the mapper is
 * pure static logic, so these derivation rules stay cheap and hermetic.
 */
class CandidateKitProfileMapperTest {

    private static CandidateProfile profile() {
        return new CandidateProfile(
                "  Jane Doe  ", "Jane@Example.com ", " +1-555-0100 ", "  Berlin  ", // name, email, phone, location
                List.of("B.Sc. Computer Science"),
                List.of(" Java ", "Spring", ""),
                List.of("Java Developer at Acme (2020-2023)"),
                List.of(),
                List.of(),
                List.of(),
                List.of("Java", "Spring"),
                List.of(),
                List.of(" Software Engineer ", "Backend Developer"), // preferredRoles
                List.of("  Remote  ", "Berlin"), // preferredLocations
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("Only the allowlisted fields are projected and trimmed")
    void projectsAllowlist() {
        CandidateKitProfileDto dto = CandidateKitProfileMapper.toKitDto(profile(), 7L);
        assertEquals(7L, dto.candidateId());
        assertEquals("Jane Doe", dto.name());
        assertEquals("Jane@Example.com", dto.email());
        assertEquals("+1-555-0100", dto.phone());
        assertEquals("Berlin", dto.location());
        assertEquals("Software Engineer, Backend Developer", dto.headline());
        assertEquals(List.of(" Java ", "Spring", ""), dto.skills()); // list passes through verbatim
    }

    @Test
    @DisplayName("Headline is derived by joining preferred roles with ', '")
    void headlineJoinsPreferredRoles() {
        assertEquals("Software Engineer, Backend Developer",
                CandidateKitProfileMapper.deriveHeadline(List.of(" Software Engineer ", "Backend Developer")));
        assertNull(CandidateKitProfileMapper.deriveHeadline(null));
        assertNull(CandidateKitProfileMapper.deriveHeadline(List.of()));
        assertNull(CandidateKitProfileMapper.deriveHeadline(List.of("  ", "")));
        assertNull(CandidateKitProfileMapper.deriveHeadline(java.util.Arrays.asList("  ", null, "")));
    }

    @Test
    @DisplayName("Headline longer than 200 chars is dropped, never truncated")
    void headlineOverflowDropped() {
        List<String> roles = List.of("A".repeat(150), "B".repeat(60));
        assertEquals(212, String.join(", ", roles).length());
        assertNull(CandidateKitProfileMapper.deriveHeadline(roles));
        // just under the limit passes through intact
        assertEquals("A".repeat(90) + ", B", CandidateKitProfileMapper.deriveHeadline(List.of("A".repeat(90), "B")));
    }

    @Test
    @DisplayName("Blank location falls back to first preferred location, labelled separately")
    void locationFallbackKeepsPreferredLocation() {
        CandidateProfile noLocation = new CandidateProfile(
                "Jane Doe", "jane@example.com", "555-1234", "  ",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of("Remote", "Berlin"),
                List.of(), List.of());
        CandidateKitProfileDto dto = CandidateKitProfileMapper.toKitDto(noLocation, 1L);
        assertNull(dto.location());
        assertEquals("Remote", dto.preferredLocation());

        CandidateProfile withLocation = new CandidateProfile(
                "Jane Doe", "jane@example.com", "555-1234", "Berlin",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        CandidateKitProfileDto withDto = CandidateKitProfileMapper.toKitDto(withLocation, 1L);
        assertEquals("Berlin", withDto.location());
        assertNull(withDto.preferredLocation());
    }

    @ParameterizedTest
    @CsvSource({
            ",",
            "'  ', ",
            "'   Jane   ', Jane"
    })
    @DisplayName("Blank strings become null, whitespace-only values stay null")
    void blanksBecomeNull(String raw, String expected) {
        assertEquals(expected, CandidateKitProfileMapper.trimToNull(raw));
    }

    @Test
    @DisplayName("Null profile maps to an all-null allowlist (defensive, never a fake profile)")
    void nullProfileIsAllNull() {
        CandidateKitProfileDto dto = CandidateKitProfileMapper.toKitDto(null, 2L);
        assertEquals(2L, dto.candidateId());
        assertNull(dto.name());
        assertNull(dto.email());
        assertNull(dto.phone());
        assertNull(dto.location());
        assertNull(dto.preferredLocation());
        assertNull(dto.headline());
        assertEquals(List.of(), dto.skills());
    }
}