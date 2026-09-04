package com.agentplatform.orchestrator.tailoring;

/**
 * The set of resume sections a tailoring analysis can recommend ordering for.
 *
 * <p>{@code SUMMARY} is always first; the remaining sections are ordered
 * deterministically by candidate evidence and job relevance.</p>
 */
public enum ResumeSection {
    SUMMARY,
    SKILLS,
    EXPERIENCE,
    INTERNSHIPS,
    PROJECTS,
    CERTIFICATIONS,
    EDUCATION
}