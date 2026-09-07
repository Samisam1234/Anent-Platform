package com.agentplatform.orchestrator.resume.persistence;

import com.agentplatform.logging.PiiSanitizer;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfileRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfilePersistenceService {
    private static final Logger log = LoggerFactory.getLogger(CandidateProfilePersistenceService.class);
    private final CandidateProfileRepository repository;

    public CandidateProfilePersistenceService(CandidateProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CandidateProfileEntity save(CandidateProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("CandidateProfile must not be null");
        }
        CandidateProfileEntity entity = CandidateProfileEntity.fromDomain(profile);
        CandidateProfileEntity saved = (CandidateProfileEntity)this.repository.save(entity);
        // Never log the candidate's email or raw resume text — only safe metadata.
        log.info("Persisted candidate profile: id={}, nameChars={}",
                saved.getId(), PiiSanitizer.safeLength(saved.getName()));
        return saved;
    }

    @Transactional(readOnly=true)
    public Optional<CandidateProfileEntity> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return this.repository.findById(id);
    }

    @Transactional(readOnly=true)
    public CandidateProfileEntity getByIdOrThrow(Long id) {
        return this.findById(id).orElseThrow(() -> new CandidateProfileNotFoundException(id));
    }

    @Transactional(readOnly=true)
    public Optional<CandidateProfileEntity> findLatest() {
        return this.repository.findTopByOrderByIdDesc();
    }

    @Transactional(readOnly=true)
    public CandidateProfileEntity getLatestOrThrow() {
        return this.findLatest().orElseThrow(() -> new CandidateProfileNotFoundException("No candidate profile found. Please upload a resume first."));
    }

    @Transactional(readOnly=true)
    public List<CandidateProfileEntity> findAll() {
        return this.repository.findAll();
    }
}

