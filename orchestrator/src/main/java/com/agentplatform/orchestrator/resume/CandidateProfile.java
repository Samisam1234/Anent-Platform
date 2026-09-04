package com.agentplatform.orchestrator.resume;

import java.util.List;

/**
 * Immutable domain model for a parsed candidate resume profile.
 *
 * <p>Additive evidence fields ({@link #resumeEvidence()} and
 * {@link #careerTrackEvidence()}) trace where each skill was observed and why a
 * career track was inferred, without affecting any existing consumers.</p>
 */
public record CandidateProfile(
        /** Candidate's full name as found in the resume. */
        String name,
        /** Candidate's email address, if present. */
        String email,
        /** Candidate's phone number, if present. */
        String phone,
        /** Candidate's current / preferred location, if present. */
        String location,
        /** Education entries parsed from the resume. */
        List<String> education,
        /** Canonicalized skill names extracted from the whole resume. */
        List<String> skills,
        /** Work experience lines parsed from the resume. */
        List<String> experience,
        /** Internship lines parsed from the resume. */
        List<String> internships,
        /** Project lines parsed from the resume. */
        List<String> projects,
        /** Certification lines parsed from the resume. */
        List<String> certifications,
        /** Software-oriented canonical skills (SOFTWARE / PROGRAMMING_AI). */
        List<String> softwareSkills,
        /** Hardware-oriented canonical skills (EMBEDDED / VLSI_FPGA / COMMUNICATION). */
        List<String> hardwareSkills,
        /** Inferred preferred roles derived from detected career tracks. */
        List<String> preferredRoles,
        /** Geographic preferences for work location. */
        List<String> preferredLocations,
        /** Traceable evidence for each extracted canonical skill (additive, may be empty). */
        List<ResumeEvidence> resumeEvidence,
        /** Traceable evidence explaining detected career tracks (additive, may be empty). */
        List<CareerTrackEvidence> careerTrackEvidence
) {

    /**
     * Backward-compatible 14-arg constructor that leaves evidence empty.
     * Keeps all existing callers (matching, persistence, tests) working.
     */
    public CandidateProfile(
            String name, String email, String phone, String location,
            List<String> education, List<String> skills, List<String> experience,
            List<String> internships, List<String> projects, List<String> certifications,
            List<String> softwareSkills, List<String> hardwareSkills,
            List<String> preferredRoles, List<String> preferredLocations) {
        this(name, email, phone, location, education, skills, experience, internships,
                projects, certifications, softwareSkills, hardwareSkills,
                preferredRoles, preferredLocations, List.of(), List.of());
    }

    /**
     * Normalizes empty evidence lists to an immutable empty list.
     */
    public CandidateProfile {
        resumeEvidence = resumeEvidence != null ? List.copyOf(resumeEvidence) : List.of();
        careerTrackEvidence = careerTrackEvidence != null ? List.copyOf(careerTrackEvidence) : List.of();
    }
}