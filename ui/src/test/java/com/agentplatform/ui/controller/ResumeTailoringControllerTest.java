package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.advisor.ApplicationAdvisorRequest;
import com.agentplatform.orchestrator.document.DocxResumeDocumentGenerator;
import com.agentplatform.orchestrator.document.GeneratedResumeDocument;
import com.agentplatform.orchestrator.document.PdfResumeDocumentGenerator;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.job.exception.JobNotFoundException;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeEvidence;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import com.agentplatform.orchestrator.tailoring.AtsReadinessAnalysis;
import com.agentplatform.orchestrator.tailoring.DraftOrigin;
import com.agentplatform.orchestrator.tailoring.HighlightedSkill;
import com.agentplatform.orchestrator.tailoring.RecommendationType;
import com.agentplatform.orchestrator.tailoring.RelevantEntry;
import com.agentplatform.orchestrator.tailoring.ResumeSection;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import com.agentplatform.orchestrator.tailoring.TailoringRecommendation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link ResumeTailoringController}. Only the web layer and the three
 * endpoints are loaded; every service and document generator is mocked. Both document
 * generators are mocked as their concrete classes so the two implementations never
 * appear as ambiguous beans.
 */
@WebMvcTest(controllers = {ResumeTailoringController.class, GlobalExceptionHandler.class})
class ResumeTailoringControllerTest {

    private static final String BASE = "/api/v1/resume/tailor";
    private static final String PDF_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CandidateProfilePersistenceService profilePersistenceService;

    @MockBean
    private JobSearchService jobSearchService;

    @MockBean
    private com.agentplatform.orchestrator.gap.CareerGapAnalysisService careerGapAnalysisService;

    @MockBean
    private com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService tailoringAnalysisService;

    @MockBean
    private com.agentplatform.orchestrator.tailoring.TailoredResumeDraftService draftService;

    @MockBean
    private PdfResumeDocumentGenerator pdfGenerator;

    @MockBean
    private DocxResumeDocumentGenerator docxGenerator;

    // ─── Fixtures ────────────────────────────────────────────────────────

    private static CandidateProfile profile() {
        return new CandidateProfile(
                "Jane Doe", "jane@example.com", "555-1234", "Berlin",
                List.of("B.Sc. Computer Science"),
                List.of("Java", "Spring"),
                List.of("Java Developer at Acme (2020-2023)"),
                List.of("Software Intern at Beta (2019)"),
                List.of("Built an order management system"),
                List.of("Oracle Certified Java SE 17"),
                List.of("Java", "Spring"),
                List.of("Embedded C"),
                List.of("Software Engineer"),
                List.of("Berlin", "Remote"));
    }

    private static Job job() {
        return new Job(
                "job-1", "Senior Java Developer", "Acme GmbH", "Berlin, Germany",
                "Build backend services in Java and Spring.",
                List.of("Java", "Spring"), List.of("Docker"),
                "5 years", "FULL_TIME", "2026-09-01", "mock",
                "https://example.test/jobs/job-1", "MOCK", Instant.parse("2026-09-01T08:00:00Z"),
                "https://example.test/apply");
    }

    private static ResumeTailoringAnalysis analysis() {
        return new ResumeTailoringAnalysis(
                1L, "job-1",
                List.of("Java", "Spring"), List.of("Docker"),
                List.of("Kafka"), List.of("AWS"),
                List.of(new HighlightedSkill("Java", List.of(ResumeEvidence.SourceSection.SKILLS))),
                List.of(new RelevantEntry("Order management system", ResumeSection.PROJECTS,
                        List.of("Java"), List.of("Spring"))),
                List.of(new RelevantEntry("Java Developer at Acme", ResumeSection.EXPERIENCE,
                        List.of("Java"), List.of("Spring"))),
                List.of(new RelevantEntry("Software Intern at Beta", ResumeSection.INTERNSHIPS,
                        List.of("Java"), List.of())),
                List.of(ResumeSection.SKILLS, ResumeSection.EXPERIENCE),
                List.of(new TailoringRecommendation(RecommendationType.HIGHLIGHT_SKILL,
                        "Highlight Java", "Matches a required skill",
                        List.of(ResumeEvidence.SourceSection.SKILLS))),
                List.of(new TailoringRecommendation(RecommendationType.MISSING_REQUIREMENT,
                        "Kafka", "Required skill not present", List.of())),
                new AtsReadinessAnalysis(72, "qualified", 2, 1, 1, 1, true, true,
                        "Close knowledge match; Kafka is missing."));
    }

    private static TailoredResumeDraft draft() {
        return new TailoredResumeDraft(
                "job-1", 1L,
                "Experienced Java developer specialized in Spring-based backends.",
                List.of("Java", "Spring", "Docker"),
                List.of("Order management system"),
                List.of("Java Developer at Acme (2020-2023)"),
                List.of("Software Intern at Beta (2019)"),
                List.of(ResumeSection.SKILLS, ResumeSection.EXPERIENCE,
                        ResumeSection.PROJECTS, ResumeSection.INTERNSHIPS),
                DraftOrigin.DETERMINISTIC,
                List.of("Kafka is in the job description but was not found in the resume."));
    }

    private void stubResolved() {
        CandidateProfileEntity entity = org.mockito.Mockito.mock(CandidateProfileEntity.class);
        when(entity.toDomain()).thenReturn(profile());
        when(profilePersistenceService.getByIdOrThrow(1L)).thenReturn(entity);
        when(jobSearchService.findById("job-1")).thenReturn(Optional.of(job()));
        // gap itself is never dereferenced server-side, only passed through to the
        // tailoring/draft mocks, so null is a hermetic fixture without extra record plumbing.
        when(careerGapAnalysisService.analyze(any(), any())).thenReturn(null);
        when(tailoringAnalysisService.analyze(any(), any(), any())).thenReturn(analysis());
        when(draftService.generate(any(), any(), any())).thenReturn(draft());
    }

    private static String body(Long candidateId, String jobId) throws Exception {
        return new ObjectMapper().writeValueAsString(new ApplicationAdvisorRequest(candidateId, jobId));
    }

    // ─── 1. JSON analysis + draft ────────────────────────────────────────

    @Test
    @DisplayName("POST " + BASE + " — valid request returns 200 with analysis and DETERMINISTIC draft")
    void tailor_validRequest_returns200Json() throws Exception {
        stubResolved();

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.draft.jobId").value("job-1"))
                .andExpect(jsonPath("$.draft.candidateId").value(1))
                .andExpect(jsonPath("$.draft.origin").value("DETERMINISTIC"))
                .andExpect(jsonPath("$.draft.orderedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.draft.sectionOrder[0]").value("SKILLS"))
                .andExpect(jsonPath("$.draft.warnings[0]").value(org.hamcrest.Matchers.containsString("Kafka")))
                .andExpect(jsonPath("$.analysis.matchedRequiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.analysis.atsReadiness.score").value(72))
                .andExpect(jsonPath("$.analysis.highlightedSkills[0].canonicalSkill").value("Java"));
    }

    // ─── 2. Resolution failures → 404 ────────────────────────────────────

    @Test
    @DisplayName("unknown candidate → 404 ProblemDetail")
    void tailor_unknownCandidate_returns404() throws Exception {
        when(profilePersistenceService.getByIdOrThrow(999L))
                .thenThrow(new CandidateProfileNotFoundException(999L));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999L, "job-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Candidate Profile Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("unknown job → 404 ProblemDetail")
    void tailor_unknownJob_returns404() throws Exception {
        CandidateProfileEntity entity = org.mockito.Mockito.mock(CandidateProfileEntity.class);
        when(entity.toDomain()).thenReturn(profile());
        when(profilePersistenceService.getByIdOrThrow(1L)).thenReturn(entity);
        when(jobSearchService.findById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "nope")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Job Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ─── 3. Validation → 400 ─────────────────────────────────────────────

    @Test
    @DisplayName("missing candidateId → 400")
    void tailor_missingCandidate_returns400() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "job-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("negative candidateId → 400")
    void tailor_negativeCandidate_returns400() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(-1L, "job-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("blank jobId → 400")
    void tailor_blankJob_returns400() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("oversized jobId → 400")
    void tailor_oversizedJob_returns400() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "x".repeat(201))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("malformed JSON → 400")
    void tailor_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    // ─── 4. PDF document ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST " + BASE + "/pdf — returns PDF attachment with safe filename")
    void tailorPdf_returns200PdfAttachment() throws Exception {
        stubResolved();
        when(pdfGenerator.generate(any(), any(), any()))
                .thenReturn(new GeneratedResumeDocument(
                        new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'},
                        "application/pdf",
                        "senior-java-developer-tailored-resume.pdf"));

        mockMvc.perform(post(BASE + "/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"senior-java-developer-tailored-resume.pdf\""))
                .andExpect(content().bytes(new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'}));
    }

    @Test
    @DisplayName("POST " + BASE + "/pdf — rendering failure yields safe 500 (no internals)")
    void tailorPdf_renderingFailure_returns500Safe() throws Exception {
        stubResolved();
        when(pdfGenerator.generate(any(), any(), any()))
                .thenThrow(new IllegalStateException("PDF write failed: secret rendering token"));

        String raw = mockMvc.perform(post(BASE + "/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(raw.contains("secret"),
                "must not leak exception message");
        org.junit.jupiter.api.Assertions.assertFalse(raw.contains("IllegalStateException"),
                "must not leak exception class name");
        org.junit.jupiter.api.Assertions.assertFalse(raw.contains(" at "),
                "must not leak a stack trace");
    }

    // ─── 5. DOCX document ────────────────────────────────────────────────

    @Test
    @DisplayName("POST " + BASE + "/docx — returns DOCX attachment with safe filename")
    void tailorDocx_returns200DocxAttachment() throws Exception {
        stubResolved();
        when(docxGenerator.generate(any(), any(), any()))
                .thenReturn(new GeneratedResumeDocument(
                        new byte[]{'P', 'K', 3, 4},
                        PDF_TYPE,
                        "senior-java-developer-tailored-resume.docx"));

        mockMvc.perform(post(BASE + "/docx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(PDF_TYPE))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"senior-java-developer-tailored-resume.docx\""))
                .andExpect(content().bytes(new byte[]{'P', 'K', 3, 4}));
    }

    @Test
    @DisplayName("POST " + BASE + "/docx — rendering failure yields safe 500 (no internals)")
    void tailorDocx_renderingFailure_returns500Safe() throws Exception {
        stubResolved();
        when(docxGenerator.generate(any(), any(), any()))
                .thenThrow(new IllegalStateException("DOCX write failed: secret rendering token"));

        String raw = mockMvc.perform(post(BASE + "/docx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(raw.contains("secret"),
                "must not leak exception message");
        org.junit.jupiter.api.Assertions.assertFalse(raw.contains(" at "),
                "must not leak a stack trace");
    }
}