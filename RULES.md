# Rules — agent-platform

> **Status**: CURRENT — non-negotiable engineering constraints as of commit 00079a9 (Phase 12.10 COMPLETE AND VERIFIED; Cleanup Batches 1–3 complete)

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
| **Phase 12.6** | 92c0942, 055db64, bc457e2, 091f7c3, 0a02e22 | **VERIFIED** | ATS Resume Tailoring — spec, backend + tests, frontend preview + PDF/DOCX download UI; core workflow + PDF/DOCX read-back verified; D2 page-level overflow deferred to 12.10 |
| **Phase 12.7** | 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | **VERIFIED** | Application Package — prepared-review sections, editing flow, email recipient verification + simulated-send labelling; full suite 1,216 tests green + browser acceptance A–P 51/51 (2026-09-29); H2 long-text `TEXT` persistence fix + regression test + two `applications.js` regression fixes in 6a9f647. D2 page-level overflow + live job-source reachability deferred to 12.10; SMTP not configured (simulated sends labelled) |
| **Phase 12.8** | d60f556, c758dca, 8718d5c, 8b2e966 | **VERIFIED** | Employer Application — Apply Kit assisted apply (review surface + eligibility in the shared modal shell; whitelisted profile/package fields + read-only `GET /api/v1/candidate/{candidateId}`; final review + manual handoff + client-side marker); full suite 1,227 tests green + browser acceptance A–Q 67/67 (2026-09-30). Slice 3 non-goals enforced by construction: no employer-page DOM automation, no auto-submit, no CAPTCHA/MFA, no credentials. D2 page-level overflow + measured-live reachability flakiness deferred to 12.10 |
| **Phase 12.9** | f21b09c, 45afa38, 28d49aa, 95da0e9 | **VERIFIED** | Application Tracking — status transitions + employer handoff (approve/reject idempotent; email-send only after approval with explicit `approved: true`; handoff only after approval and only absolute `http(s)` URLs; illegal transitions 400; optimistic lock 409); status-filtered list (unknown status 400, page resets to All); event timeline + per-status action states. Honesty rules enforced by construction: no auto-submission, no unattended email (explicit recipient confirm), no timeline row without a persisted field, transport acceptance is never called delivery — a simulated send is labelled and persisted as `SENT_SIMULATED` with the status unchanged; opening an employer site is never presented as a submission. Full suite 1,278 tests green + browser acceptance 10/10 API + 10/10 UI (2026-09-30). D2 page-level overflow ≤768px, live reachability/advisor intermittency, SMTP, ownership-free candidate endpoints, and Phase 12.5 verification remain open (12.10) |

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
| **No Guessed Email Recipient** | Email recipient is user-entered and user-confirmed before any send; a placeholder address is never used as a real send address. Simulated sends (no SMTP) are labelled as simulated in the UI — never disguised as real mail. |
| **No Fabricated Qualifications** | ATS tailoring reorders/rephrases existing content only. Never invent skills, employers, projects, certifications, education, or years of experience. |

---

## 3. Frozen Phase Boundaries (Immutable)

| Phase | Commit | Status | Allowed Changes |
|-------|--------|--------|-----------------|
| Phase 8 (Logging) | — | **FROZEN** | None |
| Phase 9 | — | **FROZEN** | None |
| Phase 10 | — | **FROZEN** | None |
| Phase 11.1 (MCP) | 0d141d6 | **FROZEN** | Config-only changes (enable/disable, URLs, timeouts) |
| Phase 12.1 | 553eb76 | **FROZEN** | Evidence-based resume parsing, MCP job search |
| Phase 12.6 (ATS Tailoring) | 92c0942, 055db64, bc457e2 | **VERIFIED** | No changes; backend (PDF/DOCX generation, 3 endpoints, tests), frontend (preview modal, download buttons) complete; core workflow + PDF/DOCX read-back browser-verified 2026-09-29; D2 page-level overflow at ≤768px remains deferred to 12.10 (not resolved); live job-source reachability remains UNVERIFIED (PRD Known Gaps) |
| Phase 12.7 (Application Package) | 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | **VERIFIED** | No changes; prepared-review sections, editing flow, recipient-verified email complete; full suite 1,216 tests green + browser acceptance A–P 51/51 verified 2026-09-29; H2 long-text `TEXT` fix + regression test + two `applications.js` regression fixes in 6a9f647; D2 page-level overflow ≤768px + live job-source reachability remain open (Phase 12.10); SMTP not configured (simulated sends labelled) |
| Phase 12.8 (Employer Application — Apply Kit) | d60f556, c758dca, 8718d5c, 8b2e966 | **VERIFIED** | No changes; Apply Kit assisted apply complete — review surface + eligibility in the shared modal shell; whitelisted profile/package values + read-only `GET /api/v1/candidate/{candidateId}` (loopback-bound); final review + manual handoff + client-side marker + stale-package re-validation. Implemented/reviewed/transferred only — never auto-fills, never submits the employer page, never touches CAPTCHA/MFA/credentials. Full suite 1,227 tests green + browser acceptance A–Q 67/67 verified 2026-09-30. D2 page-level overflow ≤768px + live single-job reachability flakiness (advisor `JobNotFoundException` race, repro 14×200/16×404) remain open (Phase 12.10) |
| Phase 12.9 (Application Tracking) | f21b09c, 45afa38, 28d49aa, 95da0e9 | **VERIFIED** | No changes; status-filtered list, guarded/idempotent status transitions, employer handoff recording, event timeline and per-status action states are complete. Transitions stay server-enforced: approve/reject idempotent (a repeat never rewrites `approvedAt`), email-send requires approval plus an explicit `approved: true`, handoff requires approval and an absolute `http(s)` URL, every illegal transition 400s naming the current status, concurrent edits 409. The UI may only reflect persisted state: timeline rows require a non-null persisted field, a simulated send is labelled as such and never called delivery, an employer-site opening is never described as a submission, and terminal states expose no edit/approve/send action. Full suite 1,278 tests green + browser acceptance 10/10 API + 10/10 UI verified 2026-09-30. D2 page-level overflow ≤768px (51px @768, 429px @390), live job-source reachability/advisor intermittency, unconfigured SMTP, and unauthenticated ownership-free candidate endpoints remain open (Phase 12.10) |
| Phase 12.10 (Final E2E + MCP + handoff) | 1ba94cc | **VERIFIED** | D2 page-level overflow resolved; live job-source reachability confirmed against ARBEITNOW/REMOTIVE/OPENINGS-MCP (`live: true`); employer-site handoff recorded server-side as an OPENING only, never a submission |
| Cleanup Batch 1 (dead code) | 7fb6e18 | **COMPLETE** | Confirmed dead code removed; advisor score rendering fixed with regression tests |
| Cleanup Batch 2 (repository hygiene) | 620749c | **COMPLETE** | Single root `.gitignore`, tracked modernize scripts, runtime logs ignored |
| Cleanup Batch 3 (Maven dependencies/config) | 00079a9 | **COMPLETE** | Dependency tree version/scope-identical after manifest cleanup; 1,301 Java + 24 JS tests green |
| Cleanup Batch 4 (documentation consistency) | b45bb4c | **COMPLETE** | Nine project documents reconciled against source and verified evidence |
| Cleanup Batch 5 (fixture + E2E scripts) | *(uncommitted)* | **IN PROGRESS** | Synthetic DOCX fixture replaces the personal CV; E2E scripts tracked in scripts/e2e/; `playwright` declared directly, unused `patchright` removed |

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

---

## 5. AI/Model Rules

| Rule | Description |
|------|-------------|
| **Current Local Model** | `llama3.2:3b` (Ollama), set by `ollama.chat-model`. **Do not change** without evidence. |
| **No Gemma 3 4B** | Gemma 3 4B is NOT the active local model. Historical test references may exist but not active. |
| **No Edge0** | Edge0 is NOT part of current runtime. |
| **UI Model Options** | The `custom` page selector offers exactly the locally-pulled tags `llama3.2:3b` and `qwen2.5:1.5b`; free-text model names are not supported. |
| **Model Switching** | Dynamic per-request via `OllamaChatModelFactory`; null/blank → `ollama.chat-model` (`llama3.2:3b`). |
| **Gemini Usage** | Disabled by code default and unconfigured in YAML — no current path uses it. If enabled, it applies to the LLM parsing path + `GET /api/v1/ai/status?probe=true`, with a 60s read timeout (remote API). |
| **Ollama Timeout** | `ollama.reasoning-timeout: 300s` in `application.yml`; `OllamaChatModelFactory` falls back to 2 minutes if the property is absent. Cold-start model loading is the slow path. |
| **Timeouts** | Server: 300s (`ollama.reasoning-timeout`). Client: 300s (`CLIENT_TIMEOUT_MS` in `resume.js`). Do not increase without evidence. |

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
| **Current Totals** | 1,304 Java tests (15 skipped Testcontainers PostgreSQL/pgvector tests) and 24 JS tests in `ui/src/test/js` (`node --test`). |
| **No Test Weakening** | Do not weaken, delete, or skip tests. |
| **Git Diff Check** | `git diff --check` must pass (only CRLF warnings allowed). |
| **No Commit Until Verified** | Only commit after browser verification + tests pass. |
| **No Push** | Never push without explicit approval. |

---

## 7. Frontend Architecture Rules

| Rule | Description |
|------|-------------|
| **No React/SPA** | Plain HTML + CSS + Vanilla JS (IIFE modules). Zero React/Vue/Svelte. |
| **Vanilla JS Only** | ES6 modules via IIFE. No build step, no bundler, no npm. |
| **Static Assets** | `ui/src/main/resources/static/` — 6 HTML pages + 15 JS modules + 1 CSS. |
| **Model Selector** | `<select id="modelSelector">` with exact locally-pulled tags only. |
| **Modal System** | Single global modal shell (`modalShell.js`) — no stacking. |
| **Toast Notifications** | `toastContainer` — auto-dismiss, error/info types. |
| **Error Handling** | `apiError.js` — safe, user-readable messages; never leak stack traces. |
| **No Code Highlighting** | There is no chat page in the static UI, so no Highlight.js and no syntax-highlighting surface exists. |

---

## 8. Configuration Rules

| Rule | Description |
|------|-------------|
| **Environment Variables** | `ADZUNA_APP_ID`/`ADZUNA_APP_KEY`/`ADZUNA_COUNTRY`, `OLLAMA_BASE_URL`, `OLLAMA_CHAT_MODEL`, `OLLAMA_EMBEDDING_MODEL`, `SPRING_PROFILES_ACTIVE`, `LLM_DEFAULT_PROVIDER`/`LLM_FAILURE_THRESHOLD`/`LLM_COOLDOWN_DURATION`, and the disabled providers' own keys (`GROQ_API_KEY`, `OPENROUTER_API_KEY`, `CEREBRAS_API_KEY`, `CLOUDFLARE_ENABLED`/`CLOUDFLARE_API_TOKEN`/`CLOUDFLARE_ACCOUNT_ID`/`CLOUDFLARE_AI_GATEWAY_ID`). |
| **Provider Enablement** | Every provider has an `enabled:` key in `application.yml`; only Ollama is on. Gemini has **no** YAML block (`gemini.enabled=false` by code default). |
| **Profile-Specific Config** | `SPRING_PROFILES_ACTIVE=postgres` for pgvector. Default = H2 in-memory. |
| **Job-Source Enablement** | Hardcoded per provider in `application.yml` — there is **no** `JOB_SOURCES_*_ENABLED` property, so setting such a variable has no effect. Only Adzuna credentials are environment-driven. |
| **Never Hardcode Credentials** | Env vars only; never in code or config files. |
| **Gemini Key** | There is no `${GEMINI_API_KEY:}` placeholder in YAML. If Gemini is ever enabled, `GeminiProperties` reads `GEMINI_API_KEY`; never log or expose it. |
| **Ignore Rules** | One tracked root `.gitignore` (`.env`, build output, IDE files, runtime logs). No module-level `.gitignore` remains; `application.yml` is tracked. |

---

## 9. Versioning & Dependencies

| Rule | Description |
|------|-------------|
| **LangChain4j Versions** | Core `1.0.0`, Gemini `1.0.0-beta5`, Ollama `1.0.0-beta5` (pinned in root `dependencyManagement`). Don't "fix" to single version. |
| **No Maven Wrapper** | System `mvn` required (Java 21). |
| **Module Dependencies** | `orchestrator` → `agent-core`, `logging`, `memory-service`, `tool-service`; `ui` → `orchestrator`, `logging`; `rag-service` → `agent-core`, `memory-service`. |
| **Reactor Order** | `agent-core` → `logging` → `memory-service` → `tool-service` → `orchestrator` → `ui` → `rag-service` |
| **Dependency Pinning** | Root `dependencyManagement` pins all versions. |

---

## 10. Git & Commit Rules

| Rule | Description |
|------|-------------|
| **No Auto-Commit** | Only commit after: implementation → test → browser verify. |
| **Pre-Commit Checks** | `mvn clean test` → Failures: 0, Errors: 0. `git diff --check` clean (CRLF warnings OK). |
| **Browser Verification** | Shiplight/Playwright required for frontend user-flow completion. |
| **No Push** | Never push without explicit approval. |
| **Commit Message** | Format: `Phase X.Y - description` (or `Cleanup Batch N - description`). |

---

## 11. Documentation Maintenance

| Rule | Description |
|------|-------------|
| **Source of Truth** | Documents describe ACTUAL repository, not aspirations. |
| **Status Labels** | `CURRENT` / `FROZEN` / `PLANNED` / `BLOCKED` at top of each file. |
| **No Invention** | Do not document future features as implemented. |
| **Contradiction Resolution** | Priority: RULES.md → PRD.md → ARCHITECTURE.md → DESIGN.md → TASKS.md → MEMORY.md |
| **Stale Info** | Mark as `STALE` or remove; don't leave misleading info. |

---

## 12. Phase Status Reference

| Phase | Commit | Status | Notes |
|-------|--------|--------|-------|
| 8 (Logging) | — | **FROZEN** | LoggingContext, PiiSanitizer |
| 9 | — | **FROZEN** | — |
| 10 | — | **FROZEN** | — |
| 11.1 (MCP) | 0d141d6 | **FROZEN** | Config-only changes allowed |
| 12.1 | 553eb76 | **FROZEN** | Evidence-based resume, MCP job search |
| 12.2 prep | f47562b | **CHECKPOINT** | Local AI runtime, timeouts |
| 12.5 (Career Analysis + Readiness UI) | 6f4b483 | **IMPLEMENTED — verification not recorded** | CSS + markup polish only (career report + advisor breakdown); no spec, no tests, no committed browser-verification evidence |
| 12.6 (ATS Tailoring) | 92c0942, 055db64, bc457e2 | **VERIFIED** | Spec, backend + tests, frontend preview + PDF/DOCX download UI; core workflow + PDF/DOCX read-back verified; D2 page-level overflow deferred to 12.10 |
| 12.7 (Application Package) | 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | **VERIFIED** | Prepared-review sections, editing flow, email recipient verification + simulated-send labelling; docs sync (cceccc3); H2 long-text `TEXT` persistence fix + `JobApplicationLongTextFieldsPersistenceTest` regression + two `applications.js` regression fixes (6a9f647). Full suite 1,216 tests, 0 failures / 0 errors / 15 skipped; browser acceptance A–P 51/51 passed (2026-09-29). D2 page-level overflow + live job-source reachability remain open (Phase 12.10 gate); SMTP not configured (simulated sends labelled) |
| 12.8 (Employer Application — Apply Kit) | d60f556, c758dca, 8718d5c, 8b2e966 | **VERIFIED** | Apply Kit assisted apply — review surface + eligibility (shared modal shell), whitelisted profile/package values + read-only `GET /api/v1/candidate/{candidateId}`, final review + manual handoff + client-side marker. Full suite 1,227 tests, 0 failures / 0 errors / 15 skipped; browser acceptance A–Q 67/67 passed (2026-09-30, two green runs). D2 page-level overflow + live single-job reachability flakiness (advisor `JobNotFoundException` race) open (12.10); no automated filling/submission, no CAPTCHA/MFA, no credentials |
| 12.9 (Application Tracking) | f21b09c, 45afa38, 28d49aa, 95da0e9 | **VERIFIED** | Status-filtered list (`?status=<ENUM>`, unknown status 400 + defensive reset to All), server-enforced idempotent status transitions, employer-handoff recording, event timeline from persisted fields only, per-status action visibility. Full suite 1,278 tests, 0 failures / 0 errors / 15 skipped; browser acceptance 10/10 API + 10/10 UI passed (2026-09-30). No auto-submission, no unattended email, no fabricated timeline event, no "delivered" claim; D2 page-level overflow ≤768px, live reachability/advisor intermittency, unconfigured SMTP, and unauthenticated ownership-free candidate endpoints open (12.10) |
| 12.10 (Final E2E + MCP + handoff) | 1ba94cc | **VERIFIED** | D2 page-level overflow resolved; live job-source reachability confirmed (ARBEITNOW/REMOTIVE/OPENINGS-MCP, `live: true`); employer-site handoff recorded as an OPENING only. Docker was unavailable in the latest verification run, so PostgreSQL/pgvector + the 15 Testcontainers tests remain unexecuted — the app runs on in-memory H2 by default |
| Cleanup Batch 1 (dead code) | 7fb6e18 | **COMPLETE** | Confirmed dead code removed; advisor score rendering fixed with regression tests |
| Cleanup Batch 2 (repository hygiene) | 620749c | **COMPLETE** | Single root `.gitignore`, tracked modernize scripts, runtime logs ignored |
| Cleanup Batch 3 (Maven dependencies/config) | 00079a9 | **COMPLETE** | Dependency tree version/scope-identical after manifest cleanup; 1,301 Java + 24 JS tests green |
| Cleanup Batch 4 (documentation consistency) | b45bb4c | **COMPLETE** | Nine project documents reconciled against source and verified evidence |
| Cleanup Batch 5 (fixture + E2E scripts) | *(uncommitted)* | **IN PROGRESS** | Synthetic DOCX fixture replaces the personal CV; E2E scripts tracked in scripts/e2e/; `playwright` declared directly, unused `patchright` removed |

---

**END OF RULES.md**