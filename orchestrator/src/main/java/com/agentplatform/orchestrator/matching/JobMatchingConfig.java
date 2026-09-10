package com.agentplatform.orchestrator.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="agent.matching")
public class JobMatchingConfig {
    private double skillWeight = 0.4;
    private double roleWeight = 0.2;
    private double experienceWeight = 0.15;
    private double trackWeight = 0.1;
    private double locationWeight = 0.1;
    private double educationWeight = 0.05;

    /**
     * Minimum required-skill coverage a job must have to be reported at all. A listing
     * below this is filtered out entirely rather than surfacing as a weak match, because
     * a role whose required skills the candidate barely has is not a match worth ranking.
     *
     * <p>Only applies to jobs that actually declare required skills. A source that
     * publishes no skill requirements (or a listing with none) has undefined overlap, and
     * filtering those would silently discard entire sources.</p>
     */
    private double minRequiredSkillOverlap = 0.20;

    /**
     * Multiplier applied to the weighted score when the candidate's career track is
     * incompatible with the job's (for example an embedded-systems candidate against a
     * Rails role). Multiplicative rather than additive so it cannot be offset by strong
     * education, location or experience sub-scores.
     */
    private double trackMismatchPenalty = 0.55;

    /**
     * Multiplier applied when the job states a location that genuinely differs from the
     * candidate's. Also multiplicative, so a location mismatch reduces the score
     * proportionally instead of costing a fixed few points.
     */
    private double locationMismatchPenalty = 0.80;

    public double getSkillWeight() {
        return this.skillWeight;
    }

    public void setSkillWeight(double skillWeight) {
        this.skillWeight = skillWeight;
    }

    public double getRoleWeight() {
        return this.roleWeight;
    }

    public void setRoleWeight(double roleWeight) {
        this.roleWeight = roleWeight;
    }

    public double getExperienceWeight() {
        return this.experienceWeight;
    }

    public void setExperienceWeight(double experienceWeight) {
        this.experienceWeight = experienceWeight;
    }

    public double getTrackWeight() {
        return this.trackWeight;
    }

    public void setTrackWeight(double trackWeight) {
        this.trackWeight = trackWeight;
    }

    public double getLocationWeight() {
        return this.locationWeight;
    }

    public void setLocationWeight(double locationWeight) {
        this.locationWeight = locationWeight;
    }

    public double getEducationWeight() {
        return this.educationWeight;
    }

    public void setEducationWeight(double educationWeight) {
        this.educationWeight = educationWeight;
    }

    public double getMinRequiredSkillOverlap() {
        return this.minRequiredSkillOverlap;
    }

    public void setMinRequiredSkillOverlap(double minRequiredSkillOverlap) {
        this.minRequiredSkillOverlap = minRequiredSkillOverlap;
    }

    public double getTrackMismatchPenalty() {
        return this.trackMismatchPenalty;
    }

    public void setTrackMismatchPenalty(double trackMismatchPenalty) {
        this.trackMismatchPenalty = trackMismatchPenalty;
    }

    public double getLocationMismatchPenalty() {
        return this.locationMismatchPenalty;
    }

    public void setLocationMismatchPenalty(double locationMismatchPenalty) {
        this.locationMismatchPenalty = locationMismatchPenalty;
    }
}

