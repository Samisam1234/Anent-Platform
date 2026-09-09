package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeException;
import com.agentplatform.orchestrator.resume.ResumeParserService;
import com.agentplatform.orchestrator.resume.ResumeProfileService;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for resume upload and profile extraction.
 *
 * <p>{@code POST /api/v1/resume/upload} accepts a multipart PDF or DOCX file,
 * extracts text using {@link ResumeParserService}, parses it into a
 * {@link CandidateProfile} via {@link ResumeProfileService}, persists it
 * using {@link CandidateProfilePersistenceService}, and returns the
 * candidate ID and profile data.</p>
 *
 * <p>Profile building is bounded by {@code ollama.reasoning-timeout} inside
 * {@link ResumeProfileService}, so this endpoint always terminates: when the AI
 * provider is unavailable or too slow the deterministic parser supplies the
 * profile and the response carries {@code aiModelUsed=false} plus a
 * user-facing {@code notice} explaining what happened.</p>
 */
@RestController
@RequestMapping("/api/v1/resume")
public class ResumeUploadController {

    private static final Logger log = LoggerFactory.getLogger(ResumeUploadController.class);
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final ResumeParserService resumeParserService;
    private final ResumeProfileService resumeProfileService;
    private final CandidateProfilePersistenceService persistenceService;

    public ResumeUploadController(ResumeParserService resumeParserService,
                                  ResumeProfileService resumeProfileService,
                                  CandidateProfilePersistenceService persistenceService) {
        this.resumeParserService = resumeParserService;
        this.resumeProfileService = resumeProfileService;
        this.persistenceService = persistenceService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadResume(
            @RequestPart("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File must not be empty"));
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                return ResponseEntity.badRequest().body(Map.of("error", "File exceeds 10 MB limit"));
            }
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.equals("application/pdf")
                    && !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type. Upload PDF or DOCX."));
            }

            byte[] bytes = file.getBytes();
            String text = resumeParserService.extractText(bytes);
            ResumeProfileService.ProfileOutcome outcome = resumeProfileService.buildProfileOutcome(text);
            CandidateProfile profile = outcome.profile();
            CandidateProfileEntity saved = persistenceService.save(profile);

            log.info("Resume uploaded and profile persisted: id={}, name={}, aiUsed={}",
                    saved.getId(), saved.getName(), outcome.aiUsed());

            // Map.of rejects nulls, so the optional notice is added conditionally.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("candidateId", saved.getId());
            body.put("profile", profile);
            body.put("aiModelUsed", outcome.aiUsed());
            if (outcome.notice() != null) {
                body.put("notice", outcome.notice());
            }
            return ResponseEntity.ok(body);
        } catch (IOException e) {
            log.error("Failed to read uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read uploaded file"));
        } catch (ResumeException e) {
            log.warn("Resume parsing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during resume upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed; please try again later."));
        }
    }
}