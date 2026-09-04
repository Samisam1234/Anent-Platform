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
}

