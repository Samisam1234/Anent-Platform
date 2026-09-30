# Architecture — agent-platform

> **Status**: CURRENT — reflects actual repository state as of commit 95da0e9 (Phase 12.7 COMPLETE AND VERIFIED; Phase 12.8 COMPLETE AND VERIFIED; Phase 12.9 COMPLETE AND VERIFIED)
> **Phase 11.1 frozen**: 0d141d6 | **Phase 12.1 verified**: 553eb76 | **Phase 12.2 prep**: f47562b | **Phase 12.3 verified**: a6eaf5c | **Phase 12.4 verified**: da933ac | **Phase 12.5 implemented (verification not recorded)**: 6f4b483 | **Phase 12.6 verified**: 0a02e22 | **Phase 12.7 verified**: 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | **Phase 12.8 verified**: d60f556, c758dca, 8718d5c, 8b2e966 | **Phase 12.9 verified**: f21b09c, 45afa38, 28d49aa, 95da0e9
> **Current local model**: llama3.2:3b (Ollama)

---

## 1. Module Overview

| Module | Package | State | Description |
|--------|---------|-------|-------------|
| `agent-core` | `com.agentplatform.core` | **IMPLEMENTED** | AI configuration, model abstractions, Ollama/Gemini integration, AiErrorClassifier, AiStatusService |
| `orchestrator` | `com.agentplatform.orchestrator` | **IMPLEMENTED** | Core business logic: advisor, agent, application, gap, job, matching, resume, tailoring, service |
| `ui` | `com.agentplatform.ui` | **IMPLEMENTED** | Only bootable module; Spring Boot app, controllers, DTOs, static HTML/CSS/JS |
| `memory-service` | `com.agentplatform.memory` | **IMPLEMENTED** | InMemoryConversationStore + pgvector/JPA persistence |
| `tool-service` | `com.agentplatform.tools` | **IMPLEMENTED** | @Tool beans (ImageTools, EmailTools, WhatsAppTools), MailConfig |
| `logging` | `com.agentplatform.logging` | **IMPLEMENTED** | LoggingContext, PiiSanitizer, LoggingConfig |
| `rag-service` | `com.agentplatform.rag` | **DORMANT** | Enabled via `rag.enabled=true` + Postgres profile; RAG infrastructure ready |

---

## 2. Module Dependency Graph (Build Order)

```
tool-service
    ↓
agent-core
    ↓
memory-service
    ↓
orchestrator
    ↓
ui (bootable)
    ↓
logging
    ↓
rag-service
```

- `ui` is the only bootable module (`spring-boot-maven-plugin` only in ui/pom.xml)
- `ui` scans `com.agentplatform` root → all `@Component`/`@Service`/`@Repository` auto-discovered
- `logging` and `rag-service` are NOT empty placeholders — both have real implementations

---

## 3. Module Responsibilities

### agent-core (`com.agentplatform.core`)
- `OllamaChatModelFactory` — builds per-request OllamaChatModel; dynamic model switching
- `GeminiConfig` / `GeminiProperties` — Google AI Gemini integration (resume parsing, ai/status probe)
- `AiErrorClassifier` — provider-aware error classification (quota, auth, model unavailable, network, timeout)
- `AiStatusService` / `AiStatusResponse` — AI provider health/status (cheap GET + optional probe)
- `OllamaChatModelFactory` — per-request model building; default model `llama3.2:3b`

### orchestrator (`com.agentplatform.orchestrator`)
| Package | Responsibility |
|---------|----------------|
| `advisor` | ApplicationAdvisorService, CareerGapAnalysis, ResumeTailoringAnalysis |
| `agent` | CareerAgentOrchestrator, AgentReasoningService, AgentToolOrchestrator, ToolPolicy |
| `application` | JobApplicationController, ApplicationPreparationService, ApplicationStorageService |
| `gap` | CareerGapAnalysisService, CareerImprovementPlanService |
| `job` | JobSearchService, JobAggregatorService, JobSourceProvider implementations, JobMatchingService |
| `matching` | JobMatchingService, Skill/Role/Experience/Education/Location/Track engines |
| `resume` | ResumeProfileService, ResumeParserService, DeterministicCandidateProfileBuilder |
| `tailoring` | ResumeTailoringAnalysisService, TailoredResumeDraftService |
| `document` | ResumeDocumentGenerator, PdfResumeDocumentGenerator, DocxResumeDocumentGenerator |
| `service` | AgentChatService, OrchestrationService, EvaluationService |
| `service.agent` | AgentChatService (chat with history, tool calling) |

### ui (`com.agentplatform.ui`)
- **Controllers**: AgentChatController, AiStatusController, ResumeUploadController, ResumeTailoringController (POST /tailor, /tailor/pdf, /tailor/docx), JobSearchController, JobMatchController, JobDetailsController, ApplicationAdvisorController, ApplicationEmailController, CustomAgentController, OrchestrationController
- **Static assets**: `src/main/resources/static/` — 5 HTML pages + 13 JS modules + CSS
- **PersistenceConfig** — `@EnableJpaRepositories` + `@EntityScan` over `com.agentplatform`

### memory-service (`com.agentplatform.memory`)
- `ConversationStore` interface + `InMemoryConversationStore` (thread-safe, caps 60 msgs/conv, 200 convs)
- `PersistentConversationStore` (JPA) + `PgVectorConversationStore` (pgvector)
- `ConversationStoreConfig` — conditional bean registration based on profile

### tool-service (`com.agentplatform.tools`)
- `ImageTools.generateImage(prompt)` → deterministic picsum URL → HTML fragment
- `EmailTools.sendEmail(recipient, subject, body)` — JavaMailSender (no host configured = simulated)
- `WhatsAppTools.sendWhatsAppMessage(phone, message)` → stub confirmation
- `MailConfig` — `@Value` setter `tools.email.from`

### logging (`com.agentplatform.logging`)
- `LoggingContext` — MDC runId correlation
- `PiiSanitizer` — email/phone/name redaction, length limiting
- `LoggingConfig` — Logback config with MDC runId

### rag-service (`com.agentplatform.rag`)
- **DORMANT**: `rag.enabled=false` by default; activates with `SPRING_PROFILES_ACTIVE=postgres`
- `TextChunker`, `DocumentEntity`, `DocumentChunkEntity`, repositories, `PgVectorRagService`
- Requires PostgreSQL/pgvector profile

---

## 4. Data Flow (Key Paths)

### Resume Upload → Profile
```
POST /api/v1/resume/upload (multipart)
  → ResumeUploadController
    → ResumeParserService.extractText(bytes)  [PDFBox/POI]
    → ResumeProfileService.buildProfileOutcome(text)
      → LLM parsing (Ollama llama3.2:3b) with 600s timeout
      → DeterministicCandidateProfileBuilder fallback
    → CandidateProfilePersistenceService.save(profile)
    → Returns CandidateProfile + X-Candidate-Id header
```

### Job Search
```
POST /api/v1/jobs/search
  → JobSearchController
    → JobSearchService.search(JobSearchRequest)
      → JobAggregatorService.search() → parallel JobSourceProvider calls
      → JobDeduplicationService → JobNormalizer → filters
      → JobSearchResult
```

### Job Matching
```
POST /api/v1/jobs/match {candidateProfileId, ...}
  → JobMatchController
    → JobMatchingService.matchJobs()
      → CandidateProfilePersistenceService.getByIdOrThrow()
      → JobAggregatorService.search() (uses profile-derived keywords)
      → evaluateJob() → 6 matching engines → JobMatchResult
```

### Career Analysis (Application Advisor)
```
POST /api/v1/jobs/advisor {candidateId, jobId}
  → ApplicationAdvisorController
    → ApplicationAdvisorService.advise()
      → CareerGapAnalysisService.analyze()
      → ResumeTailoringAnalysisService.analyze()
      → JobMatchingService.matchProfileAgainstJobs()
      → Composite score = 0.60 * ATS + 0.40 * jobMatchScore
```

### ATS Resume Tailoring
```
POST /api/v1/resume/tailor {candidateId, jobId}
  → ResumeTailoringController
    → ResumeTailoringAnalysisService.analyze()
      → CareerGapAnalysisService.analyze()
      → Deterministic reordering/emphasis of existing resume content

POST /api/v1/resume/tailor/pdf {candidateId, jobId}
  → ResumeTailoringController
    → PdfResumeDocumentGenerator.generate(draft, profile, job)
      → Returns application/pdf with Content-Disposition attachment

POST /api/v1/resume/tailor/docx {candidateId, jobId}
  → ResumeTailoringController
    → DocxResumeDocumentGenerator.generate(draft, profile, job)
      → Returns application/vnd.openxmlformats-officedocument.wordprocessingml.document with Content-Disposition attachment
```

### Application Package (Prepare → Review → Edit → Approve → Email)

```
POST /api/v1/applications/prepare {candidateId, jobId, jobTitle, company, location, customInstructions}
  → JobApplicationPreparationService.prepareApplication(...)
      → ApplicationPreparationResult (profile + job driven; deterministic fallback when no AI; never invents)
    → ApplicationStorageService.store(...) → JobApplication (status GENERATED)
      → prepared-content fields are mapped TEXT (not VARCHAR 255): long summaries/cover letters/answers
        persist without H2 truncation (fix + regression in 6a9f647)

GET /api/v1/applications/{id}           → detail; the prepared-review modal deep-links here (optionally &edit=1)
PUT /api/v1/applications/{id}           → ApplicationUpdates{coverLetter, professionalSummary, applicationAnswers}
POST /api/v1/applications/{id}/approve  → APPROVED_FOR_APPLICATION; /reject → REJECTED

POST /api/v1/applications/email/send {applicationId, approved, recipientEmail?}
  → ApplicationEmailController
    → stored status must be APPROVED_FOR_APPLICATION and approved == true, else REJECTED (400, service not invoked)
    → recipientEmail present but invalid → 400 before the service runs;
      absent → draft stays REVIEW_REQUIRED (null recipient) — the placeholder is never substituted
    → ApplicationEmailService.send(draft, approved) → ApplicationSendResult
      {status: SENT | REJECTED | FAILED, simulated}
      → SENT_SIMULATED when no EmailTools/SMTP transport is wired — labelled "simulated" in the UI,
        never disguised as real mail
```

- The email recipient is **user-entered and user-confirmed** (required, editable, inline-validated in the UI).
  This is manual confirmation of the address by the user — independent mailbox-ownership verification is
  NOT implemented and is out of scope.
- No automatic email sending and no employer-application submission at any point; approval only enables
  the (user-triggered) email send.

### Employer Apply Kit (Assisted Apply — Phase 12.8)

```
Applications detail (APPROVED_FOR_APPLICATION only) → "Assisted Apply" → shared modalShell Apply Kit
  → GET /api/v1/candidate/{candidateId}       (read-only, RFC 7807 404; loopback-bound —
      unauthenticated: trusted single-user local-first prototype only, server.address 127.0.0.1)
  → package job jobId → GET /api/v1/jobs/{id}  (URL provenance; then jobLink.safeUrl — validated
      https employer destination or the kit is disabled with the reason + manual fallback)
  → whitelist mapping (name/email/phone/location/headline/summary/skills) from the stored
      CandidateProfile + approved JobApplication; every value labeled with its source, editable,
      never a best-guess; links unsupported (no stored URL fields)
  → review interstitial (final review + copy-all) → user acknowledgement → open employer tab
      (same validated URL as the manual link) → per-field copy; user pastes and submits on the
      employer site — the app never writes to or submits that page
  → client-side-only marker agentplatform:applyKit:<id> (opened-at, job URL, edited-values
      snapshot) — NOT a status change, NOT a backend write; Phase 12.9 migration seam
```

- No automation of employer-page DOM, no auto-submit, no CAPTCHA/MFA, no credentials — assisted
  **preparation and transfer only**. The Apply Kit lives in `applications.js` (no new static file).
- Stale package on return → kit blocks with "package changed" + reload (status re-validated).

### Application Tracking (Phase 12.9)

```
GET  /api/v1/applications/candidate/{id}[?status=<ENUM>]   → ordered list (updatedAt DESC, id DESC tie-break)
      status filter is server-validated: unknown value → RFC 7807 400 naming every valid status;
      the page's select only offers known values, so a 400 is defensive → reset to All + info toast
POST /api/v1/applications/{id}/approve    GENERATED|UNDER_REVIEW → APPROVED_FOR_APPLICATION
POST /api/v1/applications/{id}/reject     GENERATED|UNDER_REVIEW|APPROVED_FOR_APPLICATION → REJECTED
POST /api/v1/applications/email/send      APPROVED_FOR_APPLICATION + approved:true (explicit user confirm)
      → persists emailSendAttemptedAt + emailSendResult (SENT | SENT_SIMULATED); status → EMAIL_SENT
POST /api/v1/applications/{id}/handoff    APPROVED_FOR_APPLICATION + absolute http(s) URL
      → persists employerOpenedAt + employerUrl (an OPENING only, never a submission)
```

- `ApplicationStorageService` owns every transition. Approve/reject are **idempotent** (a repeat
  call returns the entity unchanged and never rewrites `approvedAt`); any other source status —
  including the terminal `REJECTED` / `ARCHIVED` / `EMAIL_SENT` — throws `IllegalArgumentException`
  surfaced as RFC 7807 400. `@Version` optimistic locking maps a concurrent edit to **409** via
  `GlobalExceptionHandler`.
- `ApplicationEmailService` has a no-arg constructor, so the live wiring holds `emailTools = null`
  and every send returns `SENT_SIMULATED` deterministically; a real `SENT` requires the
  tools-wired constructor and is therefore only exercised by unit tests. Simulated is never
  presented as delivered.
- Frontend (`applications.js`, no new static file): status badge per card, filter + filtered
  counts, filtered-empty "Show all" state, retryable "Could not load applications." failure state,
  and a detail timeline built **only** from non-null persisted fields (prepared / last updated /
  approved / email-send attempt with its real-vs-simulated outcome / employer site opened). Action
  visibility is derived from status on both cards and detail (edit: GENERATED+APPROVED; approve:
  GENERATED; assisted apply + send email: APPROVED; terminal statuses expose none), and the approve
  action is confirmed in the shared `modalShell` — never a native dialog.
- The 12.8 client-side `agentplatform:applyKit:<id>` marker is not a status; the 12.9 handoff
  endpoint is the server-side record of the employer-site opening.

---

## 5. Key Integrations

### Ollama (Local LLM)
- `OllamaChatModelFactory` → per-request `OllamaChatModel` (model from request or default `llama3.2:3b`)
- Base URL: `http://localhost:11434` (configurable via `ollama.base-url`)
- Timeout: `ollama.reasoning-timeout` = 600s (was 120s; increased for cold-start)
- Chat + custom/process route through Ollama; Gemini only for resume parsing + ai/status probe

### Gemini (Google AI Studio)
- `GeminiConfig` → `GoogleAiGeminiChatModel` bean
- Used ONLY for: resume parsing (LLM path) + `GET /api/v1/ai/status?probe=true`
- 60s read timeout (remote cloud API)

### OPENINGS-MCP (Phase 11.1)
- **FROZEN**: Implemented at 0d141d6, enabled via config
- Config: `job-sources.openings-mcp.enabled=true`, `base-url: http://localhost:9000/`
- Tools: `google_search_jobs`, `amazon_search_jobs`, `apple_search_jobs`, `meta_search_jobs`
- SSE transport; parallel tool calls; URL validation via `JobUrlValidator`
- Currently **disabled** in `application.yml` (`enabled: false`)

### PostgreSQL/pgvector
- Profile: `SPRING_PROFILES_ACTIVE=postgres`
- Docker: `pgvector/pgvector:pg16`, db `agentdb`, user `agent`/`agentpassword`
- `init.sql` creates `vector` extension + `memory` table
- Used by `memory-service` (pgvector conversation store) and `rag-service`

---

## 6. Frozen Boundaries (Do Not Modify)

| Boundary | Commit | Status |
|----------|--------|--------|
| Phase 11.1 MCP | 0d141d6 | **FROZEN** — config only changes |
| Phase 8 (Logging) | — | **FROZEN** |
| Phase 9 | — | **FROZEN** |
| Phase 10 | — | **FROZEN** |
| Phase 11.1 | 0d141d6 | **FROZEN** |
| Phase 12.1 | 553eb76 | **FROZEN** |
| Phase 12.2 prep | f47562b | **CHECKPOINT** |
| Phase 12.6 (ATS Tailoring) | 92c0942, 055db64, bc457e2 | **VERIFIED** — backend (document generation, 3 endpoints, controller tests, generator tests) + frontend (preview modal, download buttons); core workflow + PDF/DOCX read-back verified; D2 page-level overflow deferred to 12.10 |
| Phase 12.7 (Application Package) | 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | **VERIFIED** — prepared-review sections, editing flow, recipient-verified email; full suite 1,216 tests green + browser acceptance A–P 51/51 (2026-09-29); H2 long-text `TEXT` persistence fix + regression test + two `applications.js` regression fixes in 6a9f647; D2 page-level overflow + live job-source reachability deferred to 12.10 |
| Phase 12.8 (Employer Application — Apply Kit) | d60f556, c758dca, 8718d5c, 8b2e966 | **VERIFIED** — user-triggered assisted apply (review + per-field copy; user pastes and submits on the employer site; never writes to a page it does not serve); one read-only `GET /api/v1/candidate/{candidateId}` (loopback-bound); full suite 1,227 tests green + browser acceptance A–Q 67/67 (2026-09-30); D2 page-level overflow + live single-job reachability flakiness deferred to 12.10 |
| Phase 12.9 (Application Tracking) | f21b09c, 45afa38, 28d49aa, 95da0e9 | **VERIFIED** — status-filtered list, server-enforced idempotent transitions + approval-gated email send + handoff recording, persisted-field-only event timeline, per-status action visibility; full suite 1,278 tests green + browser acceptance 10/10 API + 10/10 UI (2026-09-30); no auto-submit, no unattended email, no fabricated timeline row, no "delivered" claim; D2 page-level overflow + live reachability/advisor intermittency + unconfigured SMTP + ownership-free candidate endpoints deferred to 12.10 |

---

## 7. Data Models (Key Entities)

- `CandidateProfile` — structured resume data (skills, experience, education, projects, certifications, tracks, evidence)
- `Job` — title, company, location, description, skills, source, sourceUrl, applicationUrl
- `JobMatchResult` — matchScore, recommendation, matched/missing skills, strengths, concerns
- `CandidateProfile` (persistence) — JPA entity with JSON converters for lists
- `JobApplication` — status (DRAFT/GENERATED/UNDER_REVIEW/APPROVED_FOR_APPLICATION/REJECTED/ARCHIVED), tailored content; prepared-content long-text fields mapped `TEXT` (H2/PostgreSQL, no VARCHAR 255 truncation)
- `ConversationStore` — messages with vector embeddings (pgvector)

---

## 8. Configuration (application.yml)

```yaml
ollama:
  base-url: http://localhost:11434
  chat-model: llama3.2:3b
  reasoning-timeout: 600s

job-sources:
  public-api:
    enabled: false
    base-url: https://remotive.com/api/remote-jobs
  arbeitnow:
    enabled: true
  adzuna:
    enabled: true
    app-id: ${ADZUNA_APP_ID:}
    app-key: ${ADZUNA_APP_KEY:}
  openings-mcp:
    enabled: false
    base-url: http://localhost:9000/
    timeout-seconds: 20
    country-code: IND

agent.matching:
  skill-weight: 0.40
  role-weight: 0.20
  experience-weight: 0.15
  track-weight: 0.10
  location-weight: 0.10
  education-weight: 0.05
```

---

## 9. Testing Strategy

- **No full `@SpringBootTest`** — slice tests (`@WebMvcTest`) + mocked services
- **Plain JUnit** for deterministic logic (matching engines, resume parsing, document generation)
- **No DB/LLM required** for unit tests — hermetic, fast
- 74+ test files across modules
- Browser automation (Shiplight/Playwright) for E2E verification
- **New in 12.6**: `ResumeTailoringControllerTest` (12 cases: JSON 200, 404s, 400s, PDF 200, DOCX 200, safe 500 on generator failure), `PdfResumeDocumentGeneratorTest` (7 cases: PDF magic, text re-read, unicode safe, pagination, byte determinism, null inputs, slugify), `DocxResumeDocumentGeneratorTest` (6 cases: DOCX magic, text re-read, cert/education sections, null inputs, content stability, slugify)
- **PDF byte determinism (12.6)**: `PdfResumeDocumentGenerator` pins the PDF document identifier via `document.setDocumentId(FILE_ID)`. PDFBox otherwise writes a variable `/ID` into the xref trailer for each render, which would break byte-for-byte deterministic PDF output for equivalent inputs. The fixed ID is what makes the deterministic-byte assertion in `PdfResumeDocumentGeneratorTest` hold. DOCX output is intentionally **not** byte-deterministic — POI embeds `docProps/core.xml` creation timestamps — so only PDF determinism is asserted.