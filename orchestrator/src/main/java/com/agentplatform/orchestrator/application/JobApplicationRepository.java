package com.agentplatform.orchestrator.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    // Deterministic list order for the candidate applications page: newest update
    // first, stable id DESC tie-break (updatedAt is never null on stored rows).
    List<JobApplication> findByCandidateIdOrderByUpdatedAtDescIdDesc(Long candidateId);

    List<JobApplication> findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(
            Long candidateId, ApplicationStatus applicationStatus);
}