package com.agentplatform.ui.candidate;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.ui.dto.CandidateKitProfileDto;
import java.util.List;

/**
 * Builds the Apply Kit's allowlisted profile projection from the stored domain
 * profile. Pure static logic — no Spring, so the derivation rules are unit-testable
 * without loading a context.
 *
 * <p>The mapper never invents a value. Blanks become {@code null} (the kit renders
 * "not available" and invites a manual entry); a headline is derived only from real
 * preferred roles and is dropped entirely if the derived line would exceed 200 chars.
 */
public final class CandidateKitProfileMapper {

    private CandidateKitProfileMapper() {
    }

    public static CandidateKitProfileDto toKitDto(CandidateProfile profile, Long candidateId) {
        if (profile == null) {
            return new CandidateKitProfileDto(candidateId, null, null, null, null, null, null, List.of());
        }
        return new CandidateKitProfileDto(
                candidateId,
                trimToNull(profile.name()),
                trimToNull(profile.email()),
                trimToNull(profile.phone()),
                trimToNull(profile.location()),
                trimToNull(firstOrNull(profile.preferredLocations())),
                deriveHeadline(profile.preferredRoles()),
                profile.skills() != null ? List.copyOf(profile.skills()) : List.of()
        );
    }

    /**
     * Headline derivation (12.8 §4): the preferred roles, joined verbatim with
     * ", ". Stays null when there are no roles or the joined line exceeds 200
     * characters — the kit shows it unavailable rather than truncating a title.
     */
    static String deriveHeadline(List<String> preferredRoles) {
        if (preferredRoles == null || preferredRoles.isEmpty()) {
            return null;
        }
        String joined = String.join(", ", preferredRoles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .toList());
        if (joined.isBlank()) {
            return null;
        }
        return joined.length() <= 200 ? joined : null;
    }

    static String firstOrNull(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}