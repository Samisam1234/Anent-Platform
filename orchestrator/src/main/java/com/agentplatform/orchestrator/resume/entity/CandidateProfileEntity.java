package com.agentplatform.orchestrator.resume.entity;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.CareerTrackEvidence;
import com.agentplatform.orchestrator.resume.ResumeEvidence;
import com.agentplatform.orchestrator.resume.persistence.CareerTrackEvidenceListJsonConverter;
import com.agentplatform.orchestrator.resume.persistence.ResumeEvidenceListJsonConverter;
import com.agentplatform.orchestrator.resume.persistence.StringListJsonConverter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent JPA entity for candidate profiles saved in PostgreSQL table {@code candidate_profiles}.
 */
@Entity
@Table(name = "candidate_profiles", indexes = {
        @Index(name = "idx_candidate_name", columnList = "name"),
        @Index(name = "idx_candidate_email", columnList = "email")
})
public class CandidateProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String email;

    @Column
    private String phone;

    @Column
    private String location;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> education = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> skills = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> experience = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> internships = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> projects = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> certifications = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "software_skills", columnDefinition = "TEXT")
    private List<String> softwareSkills = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "hardware_skills", columnDefinition = "TEXT")
    private List<String> hardwareSkills = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "preferred_roles", columnDefinition = "TEXT")
    private List<String> preferredRoles = new ArrayList<>();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "preferred_locations", columnDefinition = "TEXT")
    private List<String> preferredLocations = new ArrayList<>();

    @Convert(converter = ResumeEvidenceListJsonConverter.class)
    @Column(name = "resume_evidence", columnDefinition = "TEXT")
    private List<ResumeEvidence> resumeEvidence = new ArrayList<>();

    @Convert(converter = CareerTrackEvidenceListJsonConverter.class)
    @Column(name = "career_track_evidence", columnDefinition = "TEXT")
    private List<CareerTrackEvidence> careerTrackEvidence = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CandidateProfileEntity() {
    }

    public CandidateProfileEntity(String name, String email, String phone, String location,
                                  List<String> education, List<String> skills, List<String> experience,
                                  List<String> internships, List<String> projects, List<String> certifications,
                                  List<String> softwareSkills, List<String> hardwareSkills,
                                  List<String> preferredRoles, List<String> preferredLocations) {
        this(name, email, phone, location, education, skills, experience, internships, projects, certifications,
                softwareSkills, hardwareSkills, preferredRoles, preferredLocations, List.of(), List.of());
    }

    public CandidateProfileEntity(String name, String email, String phone, String location,
                                  List<String> education, List<String> skills, List<String> experience,
                                  List<String> internships, List<String> projects, List<String> certifications,
                                  List<String> softwareSkills, List<String> hardwareSkills,
                                  List<String> preferredRoles, List<String> preferredLocations,
                                  List<ResumeEvidence> resumeEvidence, List<CareerTrackEvidence> careerTrackEvidence) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.location = location;
        this.education = education != null ? new ArrayList<>(education) : new ArrayList<>();
        this.skills = skills != null ? new ArrayList<>(skills) : new ArrayList<>();
        this.experience = experience != null ? new ArrayList<>(experience) : new ArrayList<>();
        this.internships = internships != null ? new ArrayList<>(internships) : new ArrayList<>();
        this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
        this.certifications = certifications != null ? new ArrayList<>(certifications) : new ArrayList<>();
        this.softwareSkills = softwareSkills != null ? new ArrayList<>(softwareSkills) : new ArrayList<>();
        this.hardwareSkills = hardwareSkills != null ? new ArrayList<>(hardwareSkills) : new ArrayList<>();
        this.preferredRoles = preferredRoles != null ? new ArrayList<>(preferredRoles) : new ArrayList<>();
        this.preferredLocations = preferredLocations != null ? new ArrayList<>(preferredLocations) : new ArrayList<>();
        this.resumeEvidence = resumeEvidence != null ? new ArrayList<>(resumeEvidence) : new ArrayList<>();
        this.careerTrackEvidence = careerTrackEvidence != null ? new ArrayList<>(careerTrackEvidence) : new ArrayList<>();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Converts a domain {@link CandidateProfile} record to a persistent entity.
     */
    public static CandidateProfileEntity fromDomain(CandidateProfile domain) {
        if (domain == null) {
            return null;
        }
        return new CandidateProfileEntity(
                domain.name(),
                domain.email(),
                domain.phone(),
                domain.location(),
                domain.education(),
                domain.skills(),
                domain.experience(),
                domain.internships(),
                domain.projects(),
                domain.certifications(),
                domain.softwareSkills(),
                domain.hardwareSkills(),
                domain.preferredRoles(),
                domain.preferredLocations(),
                domain.resumeEvidence(),
                domain.careerTrackEvidence()
        );
    }

    /**
     * Converts this entity into an immutable domain {@link CandidateProfile} record.
     */
    public CandidateProfile toDomain() {
        return new CandidateProfile(
                this.name,
                this.email,
                this.phone,
                this.location,
                this.education != null ? List.copyOf(this.education) : List.of(),
                this.skills != null ? List.copyOf(this.skills) : List.of(),
                this.experience != null ? List.copyOf(this.experience) : List.of(),
                this.internships != null ? List.copyOf(this.internships) : List.of(),
                this.projects != null ? List.copyOf(this.projects) : List.of(),
                this.certifications != null ? List.copyOf(this.certifications) : List.of(),
                this.softwareSkills != null ? List.copyOf(this.softwareSkills) : List.of(),
                this.hardwareSkills != null ? List.copyOf(this.hardwareSkills) : List.of(),
                this.preferredRoles != null ? List.copyOf(this.preferredRoles) : List.of(),
                this.preferredLocations != null ? List.copyOf(this.preferredLocations) : List.of(),
                this.resumeEvidence != null ? List.copyOf(this.resumeEvidence) : List.of(),
                this.careerTrackEvidence != null ? List.copyOf(this.careerTrackEvidence) : List.of()
        );
    }

    // ── Getters and Setters ──

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<String> getEducation() {
        return education;
    }

    public void setEducation(List<String> education) {
        this.education = education != null ? new ArrayList<>(education) : new ArrayList<>();
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? new ArrayList<>(skills) : new ArrayList<>();
    }

    public List<String> getExperience() {
        return experience;
    }

    public void setExperience(List<String> experience) {
        this.experience = experience != null ? new ArrayList<>(experience) : new ArrayList<>();
    }

    public List<String> getInternships() {
        return internships;
    }

    public void setInternships(List<String> internships) {
        this.internships = internships != null ? new ArrayList<>(internships) : new ArrayList<>();
    }

    public List<String> getProjects() {
        return projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public List<String> getCertifications() {
        return certifications;
    }

    public void setCertifications(List<String> certifications) {
        this.certifications = certifications != null ? new ArrayList<>(certifications) : new ArrayList<>();
    }

    public List<String> getSoftwareSkills() {
        return softwareSkills;
    }

    public void setSoftwareSkills(List<String> softwareSkills) {
        this.softwareSkills = softwareSkills != null ? new ArrayList<>(softwareSkills) : new ArrayList<>();
    }

    public List<String> getHardwareSkills() {
        return hardwareSkills;
    }

    public void setHardwareSkills(List<String> hardwareSkills) {
        this.hardwareSkills = hardwareSkills != null ? new ArrayList<>(hardwareSkills) : new ArrayList<>();
    }

    public List<String> getPreferredRoles() {
        return preferredRoles;
    }

    public void setPreferredRoles(List<String> preferredRoles) {
        this.preferredRoles = preferredRoles != null ? new ArrayList<>(preferredRoles) : new ArrayList<>();
    }

    public List<String> getPreferredLocations() {
        return preferredLocations;
    }

    public void setPreferredLocations(List<String> preferredLocations) {
        this.preferredLocations = preferredLocations != null ? new ArrayList<>(preferredLocations) : new ArrayList<>();
    }

    public List<ResumeEvidence> getResumeEvidence() {
        return resumeEvidence;
    }

    public void setResumeEvidence(List<ResumeEvidence> resumeEvidence) {
        this.resumeEvidence = resumeEvidence != null ? new ArrayList<>(resumeEvidence) : new ArrayList<>();
    }

    public List<CareerTrackEvidence> getCareerTrackEvidence() {
        return careerTrackEvidence;
    }

    public void setCareerTrackEvidence(List<CareerTrackEvidence> careerTrackEvidence) {
        this.careerTrackEvidence = careerTrackEvidence != null ? new ArrayList<>(careerTrackEvidence) : new ArrayList<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
