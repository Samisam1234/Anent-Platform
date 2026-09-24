# Tasks — agent-platform

> **Status**: CURRENT — roadmap as of commit a6eaf5c (Phase 12.3 — job search flow and live job sources)
> **Phase 11.1 frozen**: 0d141d6 | **Phase 12.1 verified**: 553eb76 | **Phase 12.2 prep**: f47562b | **Phase 12.3 verified**: a6eaf5c

---

## Phase Status

| Phase | Commit | Status | Notes |
|-------|--------|--------|-------|
| Phase 8 (Logging) | — | **FROZEN** | LoggingContext, PiiSanitizer, MDC runId |
| Phase 9 | — | **FROZEN** | — |
| Phase 10 | — | **FROZEN** | — |
| Phase 11.1 (MCP) | 0d141d6 | **FROZEN** | OPENINGS-MCP integration; config-only changes |
| Phase 12.1 | 553eb76 | **FROZEN** | Evidence-based resume parsing, MCP job search |
| Phase 12.2 prep | f47562b | **CHECKPOINT** | Local AI runtime, timeouts, model config |
| Phase 12.2 | f47562b | **FROZEN** | Resume/Profile frontend reliability |
| **Phase 12.3** | a6eaf5c | **VERIFIED** | Job search reliability, live sources |
| **Phase 12.4** | — | **NEXT / PLANNED** | Match Details — Job Details modal polish, source URLs |

---

## Phase 12.2 — Resume/Profile Frontend Reliability (ACTIVE)

### Objective
Make the browser UI accurately display the profile that the backend produces from the uploaded resume.

### Current Blockers
| Blocker | Details |
|---------|---------|
| **Browser automation environment** | Shiplight/Playwright works but Spring Boot devtools causes process exit |
| **Ollama cold-start/runtime** | First inference ~290s (model loading); subsequent ~2-5s |
| **Frontend/API issues** | Application Advisor modal DOM missing; Prepared Application modal broken; Career Agent "WAITING" artifact |

### Work Items (In Order)

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | **Fix Application Advisor modal DOM** (`matches.html` + `matches.js`) | **TODO** | Add `advisorReviewJobTitle`/`advisorReviewCompany`; de-duplicate 4 DOM IDs; restore `prepReviewOverlay` |
| 2 | **Fix Prepared Application modal** (`matches.html` + `matches.js`) | **TODO** | Add `prepReviewOverlay` wrapper; fix `openPreparedReview` guard |
| 3 | **Fix Career Agent "WAITING" artifact** | **TODO** | Relabel or drop static progress list |
| 4 | **Add `jobTitle`/`company` to `ApplicationAdvisorResponse`** | **TODO** | Populate in `ApplicationAdvisorService.adviseFromDomain` |
| 5 | **Fix Prepared Application content** | **TODO** | Pass `CandidateProfile` to `JobApplicationPreparationService` |
| 6 | **Enable live job sources by default** | **PLANNED** | `job-sources.public-api.enabled: true`; banner logic update |
| 7 | **Remove free-text keyword fields** | **PLANNED** | Remove `#jobsKeywordsInput` / `#matchesKeywordsInput`; derive from profile |
| 6 | **Add "Continue to Job Search" CTA** | **PLANNED** | On resume upload success |
| 7 | **Fix Career Agent "WAITING" label** | **PLANNED** | Relabel or drop static progress list |

### Remaining Phase 12 Work (Ordered)

| Phase | Milestone | Description |
|-------|-----------|-------------|
| **12.2** | Resume/Profile frontend reliability | Current |
| **12.3** | Job search reliability | Profile-driven search, live sources enabled |
| **12.4** | Match details | Job Details modal polish, source URLs |
| **12.5** | Career Analysis + Readiness | Career Analysis modal, Readiness modal |
| **12.6** | ATS Resume Tailoring | Tailored resume preview/download |
| **12.7** | Application Package | Prepare Application modal, review/approve |
| **12.8** | Employer Application | "Apply on Employer Site" flow |
| **12.9** | Application Tracking | Application list, status, history |
| **12.10** | Final Shiplight E2E | Full browser E2E regression |

---

## Phase 12.3 — Job Search Reliability (PLANNED)

- Enable live job sources by default (`job-sources.public-api.enabled: true`)
- Remove free-text keyword fields from Job Search and Matches
- Derive discovery keywords from `CandidateProfile` skills/roles
- Fix source banner logic (live = default, mock = fallback)
- Verify Job Details modal source URLs

---

## Phase 12.4 — Match Details (PLANNED)

- Polish Job Details modal (source URL, application URL)
- Match factor visualization (factor bars)
- Strengths/Concerns rendering

---

## Phase 12.5 — Career Analysis + Readiness (PLANNED)

- Career Analysis modal (fix DOM, render all sections)
- Application Readiness modal (readiness score, gaps, actions)
- Factor score visualization (bars)

---

## Phase 12.6 — ATS Resume Tailoring (PLANNED)

- Tailored resume preview modal
- Download tailored resume (PDF/DOCX)
- Section reordering preview
- Highlighted skills/projects

---

## Phase 12.7 — Application Package (PLANNED)

- Prepare Application modal (review tailored resume, cover letter, Q&A)
- Edit mode for package content
- Approve for Application action
- Email send (with approval)

---

## Phase 12.8 — Employer Application (PLANNED)

- "Apply on Employer Site" flow
- Browser automation for form filling (supported fields only)
- User review before submit
- No CAPTCHA/MFA bypass

---

## Phase 12.9 — Application Tracking (PLANNED)

- Applications page: list, status, filter
- Application detail view
- Status transitions (DRAFT → GENERATED → APPROVED → SENT)

---

## Phase 12.10 — Final Shiplight E2E (PLANNED)

- Full browser E2E regression test
- Resume → Profile → Match → Analysis → Tailor → Prepare → Apply
- Regression against Phase 11.1/12.1 baselines

---

## Current Phase 12.2 Blockers (For Tracking)

| Blocker | Severity | Mitigation |
|---------|----------|------------|
| Browser automation environment | High | Shiplight works but Spring Boot devtools causes process exit |
| Ollama cold-start/runtime | High | First inference ~290s; subsequent ~2-5s |
| Application Advisor modal DOM missing | High | DOM elements missing in HTML |
| Prepared Application modal broken | High | Missing overlay wrapper |
| Career Agent "WAITING" artifact | Medium | Frontend artifact, not backend bug |

---

## Completed in Phase 12.2 Prep (f47562b)

- [x] Timeout increased: 120s → 600s server, 150s → 630s client
- [x] Model references updated: `gemma3:4b` → `llama3.2:3b`
- [x] Cold-start comment added to `resume.js`
- [x] `.gitignore` updated with `node_modules/`
- [x] Test artifacts cleaned up
- [x] Checkpoint commit: f47562b

---

## Current Phase 12.2 Status

| Area | Status |
|------|--------|
| Backend profile parsing | ✅ Works (deterministic fallback) |
| Resume upload API | ✅ Works |
| Resume page upload flow | ✅ Works |
| Profile display | ⚠️ Partial (SVG contamination, section bugs) |
| Matches page | ⚠️ Partial (keywords field, mock default) |
| Application Advisor modal | ❌ Broken (DOM missing) |
| Prepared Application modal | ❌ Broken (wrapper missing) |
| Career Agent "WAITING" | ⚠️ Artifact |
| Job search | ⚠️ Keywords field present |
| Live job sources | Disabled (mock default) |
| Browser automation | ⚠️ Shiplight works but Spring Boot devtools issue |

---

## Next Immediate Action

**Fix Application Advisor modal DOM** (INVESTIGATION-REPORT §8/§9.1):
1. `matches.html` — Add `advisorReviewJobTitle` / `advisorReviewCompany` header elements
2. De-duplicate 4 advisor DOM IDs (keep on `<ul>`, remove from wrapper `<div>`)
3. Restore `<div class="prep-review-overlay" id="prepReviewOverlay" hidden>` wrapper
4. `matches.js` — Null-guard new lookups; relax `data.applicationId != null` gate
5. `ApplicationAdvisorResponse` — Add `jobTitle`/`company` fields
5. `ApplicationAdvisorService.adviseFromDomain` — Populate from `Job`

---

**END OF TASKS.md**