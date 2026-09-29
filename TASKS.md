# Tasks — agent-platform

> **Status**: CURRENT — roadmap as of commit 0a02e22 (Phase 12.6 verified; Phase 12.7 planned)
> **Phase 11.1 frozen**: 0d141d6 | **Phase 12.1 verified**: 553eb76 | **Phase 12.2 prep**: f47562b | **Phase 12.3 verified**: a6eaf5c | **Phase 12.4 verified**: da933ac | **Phase 12.5 implemented (verification not recorded)**: 6f4b483 | **Phase 12.6 verified**: 0a02e22 | **Phase 12.7 planned**

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
| **Phase 12.4** | da933ac | **VERIFIED** | Match Details — Job Details modal polish, source URLs |
| **Phase 12.5** | 6f4b483 | **IMPLEMENTED — verification not recorded** | Career Analysis + Readiness UI polish — CSS + markup only (career report + advisor breakdown); no spec, no tests, no committed browser-verification evidence |
| **Phase 12.6** | 92c0942, 055db64, bc457e2 | **VERIFIED** | ATS Resume Tailoring — spec, backend + tests, frontend preview + PDF/DOCX download UI; core workflow + PDF/DOCX read-back verified; D2 page-level overflow deferred to 12.10 |
| **Phase 12.7** | — | **PLANNED** | Application Package — scope defined in PHASE_12.7_SPEC.md |

---

## Phase 12.2 — Resume/Profile Frontend Reliability (FROZEN)

### Objective
Make the browser UI accurately display the profile that the backend produces from the uploaded resume.

### Current Blockers
| Blocker | Details |
|---------|---------|
| **Browser automation environment** | Shiplight/Playwright works but Spring Boot devtools causes process exit |
| **Ollama cold-start/runtime** | First inference ~290s (model loading); subsequent ~2-5s |
| **Frontend/API issues** | Application Advisor modal DOM missing; Prepared Application modal broken; Career Agent "WAITING" artifact (all historical — advisor/prepare review flows now render via the shared modal shell) |

### Work Items (In Order)

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | **Fix Application Advisor modal DOM** (`matches.html` + `matches.js`) | **SUPERSEDED** | Advisor modal renders via the shared modal shell; `ApplicationAdvisorResponse` already carries `jobTitle`/`company` |
| 2 | **Fix Prepared Application modal** (`matches.html` + `matches.js`) | **SUPERSEDED** | Review renders via the shared modal shell; no `prepReviewOverlay` wrapper exists |
| 3 | **Fix Career Agent "WAITING" artifact** | **TODO** | Relabel or drop static progress list |
| 4 | **Add `jobTitle`/`company` to `ApplicationAdvisorResponse`** | **RESOLVED** | Fields present on the record and rendered by the advisor modal |
| 5 | **Fix Prepared Application content** | **RESOLVED** | `JobApplicationPreparationService` builds the package from `CandidateProfile` + `Job` (profile-derived) |
| 6 | **Enable live job sources by default** | **PLANNED** | `job-sources.public-api.enabled: true`; banner logic update |
| 7 | **Remove free-text keyword fields** | **PLANNED** | Remove `#jobsKeywordsInput` / `#matchesKeywordsInput`; derive from profile |
| 6 | **Add "Continue to Job Search" CTA** | **PLANNED** | On resume upload success |
| 7 | **Fix Career Agent "WAITING" label** | **PLANNED** | Relabel or drop static progress list |

### Remaining Phase 12 Work (Ordered)

| Phase | Milestone | Description |
|-------|-----------|-------------|
| **12.2** | Resume/Profile frontend reliability | **FROZEN** |
| **12.3** | Job search reliability | **VERIFIED** — profile-driven search, live sources enabled |
| **12.4** | Match details | **VERIFIED** — Job Details modal polish, source URLs |
| **12.5** | Career Analysis + Readiness | **IMPLEMENTED — verification not recorded** (6f4b483) |
| **12.6** | ATS Resume Tailoring | **VERIFIED** — Tailored resume preview/download; D2 page-level overflow deferred |
| **12.7** | Application Package | Prepare Application modal, review/approve |
| **12.8** | Employer Application | "Apply on Employer Site" flow |
| **12.9** | Application Tracking | Application list, status, history |
| **12.10** | Final Shiplight E2E | Full browser E2E regression |

---

## Phase 12.3 — Job Search Reliability (VERIFIED)

- Enable live job sources by default (`job-sources.public-api.enabled: true`)
- Remove free-text keyword fields from Job Search and Matches
- Derive discovery keywords from `CandidateProfile` skills/roles
- Fix source banner logic (live = default, mock = fallback)
- Verify Job Details modal source URLs

---

## Phase 12.4 — Match Details (VERIFIED)

Implementation: `da933ac` ("Phase 12.4 - polish match details UI"). Live browser verification passed.

- [x] Match Details / Job Details modal visual polish (`style.css`)
- [x] Six match factor bars (label / bar / mono %)
- [x] Strengths / Concerns presentation
- [x] Skills (matched / missing / preferred) and metadata presentation
- [x] Source / application action semantics preserved — source "View Job Listing"; employer apply URL never fabricated
- [x] Responsive modal behavior verified (desktop + narrow viewports, no truncation)
- [x] Console / network checks passed (0 errors, 0 failed requests)
- [x] No automatic application submission — explicit user action only
- [x] No matching algorithm / backend / schema / provider changes

---

## Phase 12.5 — Career Analysis + Readiness (IMPLEMENTED — verification not recorded)

Implementation: `6f4b483` ("Phase 12.5 - polish career analysis and readiness UI", 2026-09-25). Scope was a visual pass only — `matches.js` (labelled score rows, tone-classed strengths/gaps lists) and `style.css` additions — no backend/logic change.

- Career Analysis modal polish — `.career-report*`, score hero, next-steps list
- Application Readiness breakdown polish — `.advisor-hero`, `.advisor-breakdown`, `fit-list` tones
- **Verification status**: No spec file, no test changes, and no committed browser-verification evidence for this commit exist. The changes are present in HEAD and covered indirectly by later phases' browser runs, but Phase 12.5 itself is **not marked VERIFIED**. It remains correctly labeled **IMPLEMENTED — verification not recorded**; do not promote it to VERIFIED without new browser-verification evidence.

---

## Phase 12.6 — ATS Resume Tailoring (VERIFIED)

- Tailored resume preview modal (analysis + draft) — `matches.js:buildTailoringPreviewHtml`
- Download tailored resume (PDF/DOCX) — `POST /api/v1/resume/tailor/pdf` and `/docx`
- Section reordering preview — `draft.sectionOrder` rendered in order
- Highlighted skills/projects/experience/internships — `draft.highlighted*`
- Professional summary — `draft.professionalSummary`
- Warnings/notes — `draft.warnings`
- Commits: 92c0942 (spec), 055db64 (backend + tests), bc457e2 (frontend)
- **Verification (2026-09-29)**: core workflow browser-verified — tailor 200, modal renders analysis + draft at 1440/768/390px, PDF/DOCX downloads 200 with correct filename/MIME, close/reopen clean, 0 console errors, 0 failed requests. Artifact read-back (H) passed: saved `test.pdf` (2,900 B) and `test.docx` (3,902 B) parsed with PDFBox 3 + POI 5.2.5 contain candidate name, tailored summary, ordered skills, and warnings/notes.
- **D2 (deferred)**: at 768px and 390px the tailoring modal itself fits and its controls remain usable, but the page still has horizontal overflow — `scrollWidth=943` versus client widths 768/390. This is the deferred D2 issue, not a claim of zero page-level overflow. D2 must be addressed before the Phase 12.10 full E2E acceptance.

---

## Phase 12.7 — Application Package (PLANNED)

> Detailed, reviewable specification: **PHASE_12.7_SPEC.md** (created during roadmap reconciliation).

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
| Application Advisor modal DOM missing | High | Historical — superseded by shared modal shell rendering |
| Prepared Application modal broken | High | Historical — superseded by shared modal shell rendering |
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

> **STALE / SUPERSEDED**: the items below belonged to the legacy 12.2 investigation
> (INVESTIGATION-REPORT §8/§9.1). Since then the UI migrated to the single global
> modal shell (`modalShell.js`) — Application Advisor, Career Analysis, Application
> Readiness, prepared-application review, and ATS tailoring all render through it, and
> the dedicated `advisorReviewJobTitle`/`advisorReviewCompany`/`prepReviewOverlay`
> wrappers no longer exist in `matches.html`. The ApplicationAdvisorResponse already
> carries `jobTitle`/`company` echo fields. Phase 12.7 (Application Package) supersedes
> the application-flow items. Keep only for historical reference.

**Legacy action (pre-modal-shell):**
1. `matches.html` — Add `advisorReviewJobTitle` / `advisorReviewCompany` header elements
2. De-duplicate 4 advisor DOM IDs (keep on `<ul>`, remove from wrapper `<div>`)
3. Restore `<div class="prep-review-overlay" id="prepReviewOverlay" hidden>` wrapper
4. `matches.js` — Null-guard new lookups; relax `data.applicationId != null` gate
5. `ApplicationAdvisorResponse` — Add `jobTitle`/`company` fields
5. `ApplicationAdvisorService.adviseFromDomain` — Populate from `Job`

---

**END OF TASKS.md**