# Rules — agent-platform

> **Status**: CURRENT — non-negotiable engineering constraints as of commit da933ac (Phase 12.4 verified)

---

## 1. Frozen Phases (Immutable)

| Phase | Commit | Status | Notes |
|-------|--------|--------|-------|
| Phase 8 (Logging) | — | **FROZEN** | LoggingContext, PiiSanitizer, MDC runId |
| Phase 9 | — | **FROZEN** | — |
| Phase 10 | — | **FROZEN** | — |
| Phase 11.1 (MCP) | 0d141d6 | **FROZEN** | OPENINGS-MCP integration; config-only changes allowed |
| Phase 12.1 | 553eb76 | **FROZEN** | Evidence-based resume parsing, MCP job search |
| Phase 12.2 | f47562b | **FROZEN** | Resume/Profile frontend reliability |
| **Phase 12.3** | a6eaf5c | **VERIFIED** | Job search flow and live job sources |
| **Phase 12.4** | da933ac | **VERIFIED** | Match Details — Job Details modal visual polish, source URLs |
| **Phase 12.6** | 92c0942, 055db64, bc457e2 | **VERIFIED** | ATS Resume Tailoring — spec, backend + tests, frontend preview + PDF/DOCX download UI; core workflow + PDF/DOCX read-back verified; D2 page-level overflow deferred to 12.10 |

**Rule**: Frozen phases are **immutable**. No code changes, no refactoring, no "improvements" unless explicitly required by a new phase with approval.

---

## 2. Core Product Rules (Non-Negotiable)

| Rule | Description |
|------|-------------|
| **Resume = Source of Truth** | All profile data derives from the uploaded resume. Nothing is invented, hallucinated, or inferred without evidence. |
| **Evidence-Based Career Tracks** | Tracks inferred from resume evidence (skills, projects, experience). No inference from single keywords. |
| **Project ≠ Professional Experience** | Academic/personal projects are distinct from professional employment. Never conflate. |
| **Certification ≠ Professional Experience** | Certifications listed separately; never counted as work experience. |
| **Explicit Keyword Behavior** | Free-text keyword field **removed** from Job Search / Matches. Keywords derive from profile skills/roles. |
| **Official/Source URLs Only** | Job modal shows "View Job Listing" (source URL) and "Apply on Employer Site" (employer URL if provided). Never fabricate URLs. |
| **Manual/User-Authorized Application Only** | No automatic form submission. Explicit "Approve for Application" step required. "Apply on Employer Site" opens employer URL in new tab. |
| **No Fabricated Qualifications** | ATS tailoring reorders/rephrases existing content only. Never invent skills, employers, projects, certifications, education, or years of experience. |
| **Explicit Keyword Behavior** | Free-text keyword input **removed** from Job Search and Matches. Keywords derived from profile skills/roles/tracks. |
| **Official/Source URLs Only** | Job modal shows "View Job Listing" (source URL) and "Apply on Employer Site" (if employer URL provided). Never fabricate. |
| **Manual/User-Authorized Application Only** | No auto-submit. Explicit "Approve for Application" step required. |

---

## 3. Frozen Phase Boundaries (Immutable)

| Phase | Commit | Status | Allowed Changes |
|-------|--------|--------|-----------------|
| Phase 8 (Logging) | — | **FROZEN** | None |
| Phase 9 | — | **FROZEN** | None |
| Phase 10 | — | **FROZEN** | None |
| Phase 11.1 (MCP) | 0d141d6 | **FROZEN** | Config-only changes (enable/disable, URLs, timeouts) |
| Phase 12.1 | 553eb76 | **FROZEN** | Evidence-based resume parsing, MCP job search |
| Phase 12.6 (ATS Tailoring) | 92c0942, 055db64, bc457e2 | **IMPLEMENTED** | No changes; backend (PDF/DOCX generation, 3 endpoints, tests), frontend (preview modal, download buttons) complete; core workflow + PDF/DOCX read-back browser-verified 2026-09-29; D2 page-level overflow at ≤768px deferred |

**Rule**: Frozen phases are **immutable**. No code changes, refactoring, or "improvements" unless explicitly required by a new phase with approval.

---

## 4. Implementation Rules (Non-Negotiable)

| Rule | Description |
|------|-------------|
| **Minimal/YAGNI Changes** | No speculative code, no speculative abstractions, no "for later" scaffolding. |
| **Resume = Source of Truth** | All profile data derives from uploaded resume. Nothing invented, hallucinated, or inferred without evidence. |
| **Never Fabricate Candidate Information** | Never invent skills, employers, projects, certifications, education, years of experience, or application data. |
| **Project ≠ Professional Experience** | Academic/personal projects listed separately; never conflated with employment. |
| **Certification ≠ Professional Experience** | Certifications listed separately; never counted as work experience. |
| **Evidence-Based Career Tracks** | Tracks require multiple evidence signals (skills + projects + experience). Single keyword ≠ career track. |
| **Explicit Keyword Behavior** | Free-text keyword input **removed** from Job Search and Matches. Keywords derive from profile skills/roles/tracks. |
| **Official/Source URLs Only** | Job modal shows "View Job Listing" (source URL) and "Apply on Employer Site" (employer URL if provided). Never fabricate URLs. |
| **Manual/User-Authorized Application Only** | No auto-submit. Explicit "Approve for Application" step required. "Apply on Employer Site" opens external URL. |
| **No Fabricated Qualifications** | ATS tailoring reorders/rephrases existing content only. Never invent skills, employers, projects, certifications, education, years, or application data. |
| **Explicit Keyword Behavior** | Free-text keyword input **removed** from Job Search and Matches. Keywords derive from profile skills/roles/tracks. |
| **Official/Source URLs Only** | Job modal shows "View Job Listing" (source URL) and "Apply on Employer Site" (employer URL if provided). Never fabricate URLs. |
| **Manual/User-Authorized Application Only** | No auto-submit. Explicit "Approve for Application" step required. |

---

## 5. AI/Model Rules

| Rule | Description |
|------|-------------|
| **Current Local Model** | `llama3.2:3b` (Ollama). **Do not change**. |
| **No Gemma 3 4B** | Gemma 3 4B is NOT the active local model. Historical test references may exist but not active. |
| **No Edge0** | Edge0 is NOT part of current runtime. |
| **Model Switching** | Dynamic per-request via `OllamaChatModelFactory`; null/blank → `ollama.default-model` (`llama3.2:3b`). |
| **Gemini Usage** | ONLY for resume parsing (LLM path) + `GET /api/v1/ai/status?probe=true`. 60s read timeout (remote API). |
| **Ollama Timeout** | `ollama.reasoning-timeout: 600s` (10 min) for cold-start model loading. |
| **Timeouts** | Server: 600s (config). Client: 630s (JS). Do not increase without evidence. |

---

## 6. Testing & Verification Rules

| Rule | Description |
|------|-------------|
| **No Full `@SpringBootTest`** | Use `@WebMvcTest` slice tests with mocked services, or plain JUnit. |
| **No DB/LLM Required for Tests** | Tests hermetic and fast. No DB, no external LLM. |
| **JPA/Entity Wiring** | Via `ui/PersistenceConfig` (`@EnableJpaRepositories` + `@EntityScan` over `com.agentplatform`), NOT on `AgentPlatformApplication`. |
| **Gemini Timeout** | 60s read timeout (remote cloud API). Do not remove. |
| **Maven Verification** | `mvn clean test` → **Failures: 0, Errors: 0** required before commit. |
| **Browser Verification** | Shiplight/Playwright verification required for frontend user-flow completion. |
| **No Test Weakening** | Do not weaken, delete, or skip tests. |
| **Git Diff Check** | `git diff --check` must pass (only CRLF warnings allowed). |
| **No Commit Until Verified** | Only commit after browser verification + tests pass. |
| **No Push** | Never push without explicit approval. |

---

## 6. Frontend Architecture Rules

| Rule | Description |
|------|-------------|
| **No React/SPA** | Plain HTML + CSS + Vanilla JS (IIFE modules). Zero React/Vue/Svelte. |
| **Vanilla JS Only** | ES6 modules via IIFE. No build step, no bundler, no npm. |
| **Static Assets** | `ui/src/main/resources/static/` — 5 HTML pages + 13 JS modules + 1 CSS. |
| **Model Selector** | `<select id="modelSelector">` with exact locally-pulled tags only. |
| **Modal System** | Single global modal shell (`modalShell.js`) — no stacking. |
| **Toast Notifications** | `toastContainer` — auto-dismiss, error/info types. |
| **Error Handling** | `apiError.js` — safe, user-readable messages; never leak stack traces. |
| **Code Highlighting** | Highlight.js 11.9.0 via cdnjs; `hljs.configure({ignoreUnescapedHTML:true})`. |

---

## 6. Configuration Rules

| Rule | Description |
|------|-------------|
| **Environment Variables** | `GEMINI_API_KEY` (gitignored), `ADZUNA_APP_ID/KEY`, `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL`. |
| **Profile-Specific Config** | `SPRING_PROFILES_ACTIVE=postgres` for pgvector. Default = H2 in-memory. |
| **Environment Override** | `JOB_SOURCES_PUBLIC_API_ENABLED=false`, `JOB_SOURCES_ARBEITNOW_ENABLED=false`, `ADZUNA_APP_ID/KEY`. |
| **Never Hardcode Credentials** | Env vars only; never in code or config files. |
| **No Root `.gitignore` Until Recently** | Module-level .gitignore + root `.gitignore` for `.env` + `node_modules/`. |

---

## 7. Versioning & Dependencies

| Rule | Description |
|------|-------------|
| **LangChain4j Versions** | Core `1.0.0`, Gemini `1.0.0-beta5`, Ollama `1.0.0-beta5` (pinned in root `dependencyManagement`). Don't "fix" to single version. |
| **No Maven Wrapper** | System `mvn` required (Java 21). |
| **Module Build Order** | `tool-service` → `agent-core` → `memory-service` → `orchestrator` → `ui` → `logging` → `rag-service` |
| **No Maven Wrapper** | System `mvn` required. |
| **Dependency Pinning** | Root `dependencyManagement` pins all versions. |

---

## 8. Git & Commit Rules

| Rule | Description |
|------|-------------|
| **No Auto-Commit** | Only commit after: implementation → test → browser verify. |
| **Pre-Commit Checks** | `mvn clean test` → Failures: 0, Errors: 0. `git diff --check` clean (CRLF warnings OK). |
| **Browser Verification** | Shiplight/Playwright required for frontend user-flow completion. |
| **No Push** | Never push without explicit approval. |
| **Commit Message** | Format: `Phase X.Y - description`. |

---

## 9. Documentation Maintenance

| Rule | Description |
|------|-------------|
| **Source of Truth** | Documents describe ACTUAL repository, not aspirations. |
| **Status Labels** | `CURRENT` / `FROZEN` / `PLANNED` / `BLOCKED` at top of each file. |
| **No Invention** | Do not document future features as implemented. |
| **Contradiction Resolution** | Priority: RULES.md → PRD.md → ARCHITECTURE.md → DESIGN.md → TASKS.md → MEMORY.md |
| **Stale Info** | Mark as `STALE` or remove; don't leave misleading info. |

---

## 9. Testing & Verification (Summary)

| Requirement | Standard |
|-------------|----------|
| `mvn clean test` | Failures: 0, Errors: 0 |
| `git diff --check` | Clean (CRLF warnings OK) |
| Browser verification | Shiplight/Playwright for user-flow completion |
| No test weakening | Never weaken/delete/skip tests |

---

## 10. Phase Status Reference

| Phase | Commit | Status | Notes |
|-------|--------|--------|-------|
| 8 (Logging) | — | **FROZEN** | LoggingContext, PiiSanitizer |
| 9 | — | **FROZEN** | — |
| 10 | — | **FROZEN** | — |
| 11.1 (MCP) | 0d141d6 | **FROZEN** | Config-only changes allowed |
| 12.1 | 553eb76 | **FROZEN** | Evidence-based resume, MCP job search |
| 12.2 prep | f47562b | **CHECKPOINT** | Local AI runtime, timeouts |
| 12.6 (ATS Tailoring) | 92c0942, 055db64, bc457e2 | **VERIFIED** | Spec, backend + tests, frontend preview + PDF/DOCX download UI; core workflow + PDF/DOCX read-back verified; D2 page-level overflow deferred to 12.10 |

---

**END OF RULES.md**