# Architecture — agent-platform

> **Status**: CURRENT — reflects actual repository state as of commit f47562b (Phase 12.2 prep)
> **Phase 11.1 frozen**: 0d141d6 | **Phase 12.1 verified**: 553eb76 | **Phase 12.2 prep**: f47562b
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
| `service` | AgentChatService, OrchestrationService, EvaluationService |
| `service.agent` | AgentChatService (chat with history, tool calling) |

### ui (`com.agentplatform.ui`)
- **Controllers**: AgentChatController, AiStatusController, ResumeUploadController, ResumeTailoringController, JobSearchController, JobMatchController, JobDetailsController, JobMatchController, ApplicationAdvisorController, ApplicationEmailController, CustomAgentController, OrchestrationController, ApplicationAdvisorController
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
```

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

---

## 7. Data Models (Key Entities)

- `CandidateProfile` — structured resume data (skills, experience, education, projects, certifications, tracks, evidence)
- `Job` — title, company, location, description, skills, source, sourceUrl, applicationUrl
- `JobMatchResult` — matchScore, recommendation, matched/missing skills, strengths, concerns
- `CandidateProfile` (persistence) — JPA entity with JSON converters for lists
- `JobApplication` — status (DRAFT/GENERATED/APPROVED/SENT), tailored content
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
- **Plain JUnit** for deterministic logic (matching engines, resume parsing)
- **No DB/LLM required** for unit tests — hermetic, fast
- 74+ test files across modules
- Browser automation (Shiplight/Playwright) for E2E verification