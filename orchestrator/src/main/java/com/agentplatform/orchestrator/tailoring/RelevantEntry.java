package com.agentplatform.orchestrator.tailoring;

import java.util.List;

/**
 * An existing, verified resume entry (project, experience, or internship line) that has
 * deterministic relevance to the target job.
 *
 * <p>{@code content} is the original candidate text, never modified or invented.
 * {@code canonicalSkills} are the taxonomy skills found in the entry's text,
 * {@code matchedJobSkills} are the subset that the job also asks for (canonical), and
 * {@code section} records where the entry lives in the resume.</p>
 */
public record RelevantEntry(
        String content,
        ResumeSection section,
        List<String> canonicalSkills,
        List<String> matchedJobSkills
) {
    public RelevantEntry {
        content = content == null ? "" : content.trim();
        canonicalSkills = canonicalSkills != null ? List.copyOf(canonicalSkills) : List.of();
        matchedJobSkills = matchedJobSkills != null ? List.copyOf(matchedJobSkills) : List.of();
    }
}