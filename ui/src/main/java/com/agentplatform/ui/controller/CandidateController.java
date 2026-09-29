package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import com.agentplatform.ui.candidate.CandidateKitProfileMapper;
import com.agentplatform.ui.dto.CandidateKitProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only candidate data for the Apply Kit (Phase 12.8, Slice 2).
 *
 * <p>Returns the allowlisted, kit-specific projection of the stored candidate profile —
 * never the full resume or the raw parser output. Missing candidate → {@link
 * CandidateProfileNotFoundException} → RFC 7807 404 via {@link GlobalExceptionHandler};
 * a non-numeric id → 400 via the same handler. This controller only reads; nothing here
 * mutates a profile or an application record.
 *
 * <p><b>Security disposition (Phase 12.8).</b> This endpoint is unauthenticated and has
 * no per-candidate ownership authorization, and candidate IDs are sequential and
 * enumerable. It is intended only for the trusted, single-user, local-first prototype:
 * {@code server.address: 127.0.0.1} (application.yml) restricts remote/LAN access but
 * does not isolate local processes or operating-system users. Networked or multi-user
 * deployment is blocked pending a proper identity, ownership, and transport-security
 * design. Do not add partial authentication, fake ownership checks, ID obscurity, or
 * rate-limit workarounds here.
 */
@RestController
@RequestMapping("/api/v1/candidate")
public class CandidateController {

    private static final Logger log = LoggerFactory.getLogger(CandidateController.class);

    private final CandidateProfilePersistenceService persistenceService;

    public CandidateController(CandidateProfilePersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping("/{candidateId}")
    public CandidateKitProfileDto getCandidate(@PathVariable Long candidateId) {
        CandidateProfileEntity entity = persistenceService.getByIdOrThrow(candidateId);
        CandidateProfile profile = entity.toDomain();
        log.info("Serving kit candidate profile: id={}", entity.getId()); // never log contact data
        return CandidateKitProfileMapper.toKitDto(profile, entity.getId());
    }
}