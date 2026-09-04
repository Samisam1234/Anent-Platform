package com.agentplatform.orchestrator.resume.persistence;

import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateProfileRepository
extends JpaRepository<CandidateProfileEntity, Long> {
    public Optional<CandidateProfileEntity> findTopByOrderByIdDesc();
}

