package com.agentplatform.ui.dto;

import java.util.List;

/**
 * Read-only, allowlisted projection of a candidate profile for the Apply Kit
 * (Phase 12.8, Slice 2).
 *
 * <p>Only the fields the whitelist for assisted application preparation are exposed:
 * contact identity, preferred-role headline, and skills. The full resume (education,
 * experience, projects, evidence traces, certifications, ...) and anything unrelated
 * to the kit never reach the wire. {@code headline} is derived server-side from the
 * preferred roles; {@code preferredLocation} is the first stored preferred-locations
 * entry and is only used by the UI when {@code location} is blank.
 */
public record CandidateKitProfileDto(
        Long candidateId,
        String name,
        String email,
        String phone,
        String location,
        String preferredLocation,
        String headline,
        List<String> skills
) {
}