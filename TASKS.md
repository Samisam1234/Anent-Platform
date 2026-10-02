# Tasks — agent-platform

> **Status**: CURRENT — roadmap as of commit 00079a9 (Phase 12.10 COMPLETE AND VERIFIED; Cleanup Batches 1–3 complete; Batch 4 documentation consistency in progress, uncommitted)
> **Phase 11.1 frozen**: 0d141d6 | **Phase 12.1 verified**: 553eb76 | **Phase 12.2 prep**: f47562b | **Phase 12.3 verified**: a6eaf5c | **Phase 12.4 verified**: da933ac | **Phase 12.5 implemented (verification not recorded)**: 6f4b483 | **Phase 12.6 verified**: 92c0942, 055db64, bc457e2, 091f7c3, 0a02e22 | **Phase 12.7 verified**: 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | **Phase 12.8 verified**: d60f556, c758dca, 8718d5c, 8b2e966 | **Phase 12.9 verified**: f21b09c, 45afa38, 28d49aa, 95da0e9 | **Phase 12.10 verified**: 1ba94cc
> **Cleanup Batch 1**: 7fb6e18 | **Batch 2**: 620749c | **Batch 3**: 00079a9

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
| **Phase 12.6** | 92c0942, 055db64, bc457e2, 091f7c3, 0a02e22 | **VERIFIED** | ATS Resume Tailoring — spec, backend + tests, frontend preview + PDF/DOCX download UI, docs sync (091f7c3), PDF byte determinism + D2 responsive follow-up (0a02e22); core workflow + PDF/DOCX read-back verified; D2 page-level overflow resolved in 12.10 |
| **Phase 12.7** | 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | **VERIFIED** | Application Package — prepared-review sections (9009aae), editing flow (351283e), email recipient verification + simulated-send labelling (9275582), docs sync (cceccc3), acceptance fixes (6a9f647: H2 long-text `TEXT` persistence fix + `JobApplicationLongTextFieldsPersistenceTest` regression + two `applications.js` regression fixes). Slices 1–3 verified: full suite 1,216 tests, 0 failures / 0 errors / 15 skipped; browser acceptance A–P 51/51 checks passed. D2 page-level overflow + live job-source reachability were resolved in Phase 12.10; SMTP-unconfigured simulated sends remain a documented limitation |
| **Phase 12.8** | d60f556, c758dca, 8718d5c, 8b2e966 | **VERIFIED** | Employer Application — Apply Kit assisted apply; full suite 1,227 tests green; browser acceptance A–Q 67/67 (2026-09-30) |
| **Phase 12.9** | f21b09c, 45afa38, 28d49aa, 95da0e9 | **VERIFIED** | Application Tracking — status-filtered list, server-enforced transitions, event timeline, action states; full suite 1,278 tests green; browser acceptance 10/10 API + 10/10 UI (2026-09-30) |
| **Phase 12.10** | 1ba94cc | **VERIFIED** | Final Shiplight E2E — D2 page-level overflow resolved; live job-source reachability confirmed (ARBEITNOW/REMOTIVE/OPENINGS-MCP, `live: true`); employer handoff recorded as an opening only |
| **Cleanup Batch 1** | 7fb6e18 | **COMPLETE** | Confirmed dead code removed; advisor score rendering fixed with regression tests |
| **Cleanup Batch 2** | 620749c | **COMPLETE** | Repository hygiene — single root `.gitignore`, tracked modernize scripts, runtime logs ignored |
| **Cleanup Batch 3** | 00079a9 | **COMPLETE** | Maven dependency/config manifest cleanup; dependency tree version/scope-identical; 1,301 Java + 24 JS tests green |
| **Cleanup Batch 4** | *(uncommitted)* | **IN PROGRESS** | Documentation consistency across the nine project documents — source-verified only, no source/config changes |
| **Cleanup Batch 5** | — | **PENDING** | Not started; scope not yet defined |

---

## Phase 12.2 — Resume/Profile Frontend Reliability (FROZEN)

### Objective
Make the browser UI accurately display the profile that the backend produces from the uploaded resume.

### Current Blockers
| Blocker | Details |
|---------|---------|
| **Browser automation environment** | Shiplight/Playwright works but Spring Boot devtools causes process exit |
| **Ollama cold-start/runtime** | First inference ~290s (model loading); subsequent ~2-5s |
| **Frontend/API issues** | All resolved — advisor/prepare review flows render via the shared modal shell, the keyword fields and mock-default banner are gone, and the Career Agent `WAITING` artifact was removed in Cleanup Batch 1 (`7fb6e18`) |

### Work Items (In Order)

> Historical Phase 12.2 investigation list, retained for traceability. Every item is now resolved or superseded — see the Phase Status table above and PRD §4.

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | **Fix Application Advisor modal DOM** (`matches.html` + `matches.js`) | **SUPERSEDED** | Advisor modal renders via the shared modal shell; `ApplicationAdvisorResponse` already carries `jobTitle`/`company` |
| 2 | **Fix Prepared Application modal** (`matches.html` + `matches.js`) | **SUPERSEDED** | Review renders via the shared modal shell; no `prepReviewOverlay` wrapper exists |
| 3 | **Fix Career Agent "WAITING" artifact** | **RESOLVED** | Static progress list and the invented `WAITING` state removed (`7fb6e18`); `careerAgent.js` renders the deterministic advisor report |
| 4 | **Add `jobTitle`/`company` to `ApplicationAdvisorResponse`** | **RESOLVED** | Fields present on the record and rendered by the advisor modal |
| 5 | **Fix Prepared Application content** | **RESOLVED** | `JobApplicationPreparationService` builds the package from `CandidateProfile` + `Job` (profile-derived) |
| 6 | **Enable live job sources by default** | **DONE (12.3)** | Remotive, Arbeitnow, Adzuna and OPENINGS-MCP enabled; mock disabled; live-first banner |
| 7 | **Remove free-text keyword fields** | **DONE (12.3)** | `#jobsKeywordsInput` / `#matchesKeywordsInput` removed; discovery derives from the profile |
| 8 | **Add a continue CTA after profile ready** | **DONE** | `#continueToMatchesBtn` ("View My Matches") on upload success; Matches is the primary discovery surface |

### Remaining Phase 12 Work (Ordered)

| Phase | Milestone | Description |
|-------|-----------|-------------|
| **12.2** | Resume/Profile frontend reliability | **FROZEN** |
| **12.3** | Job search reliability | **VERIFIED** — profile-driven search, live sources enabled |
| **12.4** | Match details | **VERIFIED** — Job Details modal polish, source URLs |
| **12.5** | Career Analysis + Readiness | **IMPLEMENTED — verification not recorded** (6f4b483) |
| **12.6** | ATS Resume Tailoring | **VERIFIED** — Tailored resume preview/download; D2 page-level overflow deferred |
| **12.7** | Application Package | **VERIFIED** — review sections, editing, recipient-verified email; full suite 1,216 tests green; browser acceptance 51/51; D2 page-level overflow + live job-source reachability open (12.10 E2E gate) |
| **12.8** | Employer Application | "Apply on Employer Site" flow — assisted apply (kit) — **VERIFIED** — spec: PHASE_12.8_SPEC.md |
| **12.9** | Application Tracking | Status-filtered list, status transitions, event timeline, action states — **VERIFIED** — spec: PHASE_12.9_SPEC.md |
| **12.10** | Final Shiplight E2E | **VERIFIED** — full browser E2E regression; D2 overflow resolved; live reachability confirmed (`live: true` from ARBEITNOW/REMOTIVE/OPENINGS-MCP); handoff recorded as an opening only |
| **B1–B3** | Cleanup Batches 1–3 | **COMPLETE** — dead code, repository hygiene, Maven dependency/config manifests |
| **B4** | Cleanup Batch 4 (documentation consistency) | **IN PROGRESS** — nine project documents reconciled against source and verified evidence; uncommitted |
| **B5** | Cleanup Batch 5 | **PENDING** — not started |

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

## Phase 12.7 — Application Package (VERIFIED)

> Detailed, reviewable specification: **PHASE_12.7_SPEC.md** (created during roadmap reconciliation).

- **Slice 1 — Prepared-Application review completeness** (`9009aae`, `matches.js` + `applications.js`): prepare-review modal renders the previously missing `suggestedAnswers` (Q&A), `candidateStrengths`, and `recommendation` + `matchScore` (advisor recommendation chip); footer copy makes the approval gate explicit ("Approving the package in Applications is required before any email can go out."); "Review in Applications" / "Edit in Applications" deep links.
- **Slice 2 — Application Package editing** (`351283e`, `applications.js` + `style.css`): edit deep link (`applications.html?application=<id>&edit=1`) auto-enters the Applications-page edit surface when edit-eligible (DRAFT/GENERATED/UNDER_REVIEW and APPROVED_FOR_APPLICATION — i.e. before sending); inline per-field validation, saving state + double-submit protection, reload-from-server after save, safe cancel; editing remains separate from approval.
  - **Verification**: 999 tests passed, no failures, errors, or skips; browser checks passed.
- **Slice 3 — Email recipient verification + safe sending** (`9275582`, backend + tests + UI): email/send accepts an optional `recipientEmail`; the recipient is user-entered and user-confirmed (required, editable, inline-validated); no guessed/substituted placeholder is ever used (absent → draft `REVIEW_REQUIRED`, invalid → 400 before the service runs); backend approval gate unchanged (stored `APPROVED_FOR_APPLICATION` **and** `approved=true` required, else `REJECTED`); simulated sends are labelled ("simulated — no SMTP configured. Nothing was actually mailed."); `REJECTED`/`FAILED` results surfaced verbatim.
  - **Verification**: 1,002 tests passed, no failures, errors, or skips; controlled browser checks passed using a route-intercepted mock email transport — no real email was sent.
- **Slice 4 — Documentation sync** (`cceccc3`, docs): `TASKS.md`, `DESIGN.md`, `ARCHITECTURE.md`, `PRD.md`, `MEMORY.md`, `RULES.md`, and `PHASE_12.7_SPEC.md` updated to record Slices 1–3.
- **Acceptance fixes** (`6a9f647`): H2 long-text persistence fix — the six prepared-content fields are mapped `TEXT` (no VARCHAR 255 truncation; H2 500 on prepare otherwise) — with the `JobApplicationLongTextFieldsPersistenceTest` regression (store + reload, values > 255 chars); plus two acceptance-discovered `applications.js` regressions fixed: the applications-page deep-link detail-visibility race, and the missing detail-view "Approve Application" button handler.
- **Final acceptance (2026-09-29, VERIFIED)**: full `mvn clean test` → **1,216 tests, 0 failures, 0 errors, 15 skipped**; browser acceptance A–P → **51/51 checks passed** (covering the two `applications.js` fixes above). Unchanged limitations: D2 page-level overflow ≤768px deferred (12.10); live job-source reachability UNVERIFIED; SMTP not configured — simulated sends labelled; no persisted email-sent flag; no independent mailbox-ownership verification; Phase 12.5 verification not recorded.
- No automatic email sending or employer application submission at any point.

---

## Phase 12.8 — Employer Application (VERIFIED)

> Detailed, reviewable specification: **PHASE_12.8_SPEC.md** (created 2026-09-29, COMPLETE AND VERIFIED 2026-09-30).

- "Apply on Employer Site" flow — **user-triggered, assisted apply**, reconciled with PRD §4:
  the user starts the flow from an approved package, reviews/edits every prepared value, and
  performs the final paste and submit on the employer site; the app never writes to or submits a
  page it does not serve, never touches CAPTCHA/MFA, never handles credentials.
- Assists only whitelisted supported fields (name, email, phone, location, headline, professional
  summary, skills), carried by the Apply Kit + one read-only `GET /api/v1/candidate/{candidateId}`
  endpoint (grounded by inspection — no front-end profile read path exists). Links are initially
  unsupported (the parser stores no GitHub/LinkedIn URLs).
- User review before submit; user alone submits on the employer site; safe decline for
  unsupported forms; client-side-only apply marker as the Phase 12.9 migration seam.
- No CAPTCHA/MFA bypass, no automation of employer-page DOM, no unattended filling.
- Commits: `d60f556` (spec), `c758dca` (Slice 1 — review surface + eligibility), `8718d5c`
  (Slice 2 — whitelist values + `GET /api/v1/candidate/{candidateId}` loopback binding),
  `8b2e966` (Slice 3 — final review + manual handoff + client-side marker + stale-package
  re-validation). The Apply Kit lives in `applications.js` (shared `modalShell`); no new static
  file.
- **Slice 2 security disposition**: `GET /api/v1/candidate/{candidateId}` is unauthenticated with
  no per-candidate ownership (sequential, enumerable IDs). Intended only for the trusted
  single-user local-first prototype — `server.address: 127.0.0.1` restricts remote/LAN access but
  does not isolate local processes. Networked/multi-user deployment is blocked pending a proper
  identity/ownership/transport-security design; no partial auth or ID obscurity added.
- **Verification (2026-09-30, VERIFIED)**: full `mvn clean test` → **1,227 tests, 0 failures,
  0 errors, 15 skipped**; browser acceptance checkpoints **A–Q → 67/67 checks passed** (two
  consecutive green runs) against route-intercepted fixtures (kit job + advisor-path job with
  valid `applicationUrl`, canned advisor response, mock/listing-only jobs for the decline path,
  local fixture page for the copy→paste round-trip). Suite total rose 1,216 → 1,227 with the
  Slice 2 `@WebMvcTest` coverage for the new candidate endpoint.
- **Live job-source reliability finding (new)**: the acceptance run (networked workstation) showed
  live Arbeitnow/REMOTIVE reachability is real but flaky — the advisor's per-request live
  `JobSearchService.findById` intermittently threw `JobNotFoundException` (repro: 30 identical
  advisor POSTs → 14×200/16×404). Backend reliability item for 12.10; not a kit defect; acceptance
  remains fixture-based and real employer sites are never automated.
- Unchanged deferred gates: D2 page-level overflow ≤768px → 12.10; live job-source reachability
  not claimed (UNVERIFIED in sandbox, observed flaky on networked run); SMTP unconfigured
  (simulated sends labelled); no persisted email-sent flag; no mailbox-ownership verification;
  Phase 12.5 verification outstanding.

---

## Phase 12.9 — Application Tracking (VERIFIED)

> Detailed, reviewable specification: **PHASE_12.9_SPEC.md** (Slice 0, created 2026-09-30, COMPLETE AND VERIFIED 2026-09-30).

- Applications page: status-filtered list, per-status badges, and status-filtered counts. The
  `?status=<ENUM>` filter is served by the existing list endpoint
  (`GET /api/v1/applications/candidate/{id}?status=`); an unknown status returns RFC 7807 **400**
  with the valid-status list, and the page defensively resets to All rather than showing an
  unusable error.
- Status transitions (Slice 2, `45afa38`): `POST .../{id}/approve`, `.../{id}/reject`,
  `.../email/send`, `.../handoff`. Approve/reject are idempotent (a repeat call does not rewrite
  `approvedAt`); approve is legal from DRAFT/GENERATED/UNDER_REVIEW, email-send only from
  APPROVED_FOR_APPLICATION (with explicit `approved: true`), handoff only after approval and
  only for absolute `http(s)` URLs. Every illegal transition returns **400** naming the current
  status. Concurrent edits return **409** (optimistic locking).
- Application detail view (exists since 12.7) gains the event timeline and status-derived action
  states (Slice 4, `95da0e9`): rows render **only** from persisted fields (prepared, last updated,
  approved, email-send attempt with its real-vs-simulated outcome, employer site opened), and
  terminal states (EMAIL_SENT/REJECTED/ARCHIVED) never offer edit/approve/send actions.
- **Honesty constraints enforced end-to-end**: the platform never auto-submits, never sends email
  unattended (an explicit recipient confirm is required), never fabricates a timeline event it
  cannot observe, and never calls transport acceptance "delivery" — a simulated send is labelled
  "Email send simulated — no SMTP configured. Nothing was actually mailed." and persists
  `emailSendResult = SENT_SIMULATED` with the status unchanged.
- Commits: `f21b09c` (Slice 0 — spec), `45afa38` (Slice 1 — status transitions + employer
  handoff), `28d49aa` (Slice 2 — status-filtered list), `95da0e9` (Slice 3 — event timeline +
  status action states).
- **Verification (2026-09-30, VERIFIED)**: full `mvn clean test` → **1,278 tests, 0 failures,
  0 errors, 15 skipped** (agent-core 135, logging 14, memory-service 39, tool-service 10,
  orchestrator 933, ui 132, rag-service 15). Browser acceptance → **10/10 UI checkpoints + 10/10
  API checkpoints passed** (status transitions incl. idempotency and illegal-transition 400s,
  email simulated-vs-sent truthfulness, handoff validation, filter counts/scoping/ordering, badges
  and per-status action visibility, timeline rows, empty/filtered-empty/failure-Retry states,
  confirm-dialog cancellation, responsive detail + review modal at 1440/768/390). A deterministic
  7-application / 2-candidate H2 fixture backs the API run.
- Unchanged deferred gates: D2 page-level overflow ≤768px → 12.10 (page-level overflow measured at
  51px @768 and 429px @390; the detail surface and review modal themselves fit); live job-source
  reachability / advisor intermittency not claimed; SMTP unconfigured (simulated sends labelled,
  never called delivered); opening an employer site is not proof of submission; candidate
  endpoints unauthenticated and ownership-free (loopback-only, as in 12.8); networked/multi-user
  deployment still needs a proper identity/ownership/transport-security design; Phase 12.5
  verification outstanding.

---

## Phase 12.10 — Final Shiplight E2E (COMPLETE AND VERIFIED)

> Commit: `1ba94cc` ("Phase 12.10 - MCP integration and verified employer handoff").

- Full browser E2E regression — Resume → Profile → Match → Analysis → Tailor → Prepare → Apply
- Regression against Phase 11.1/12.1 baselines
- **D2 page-level overflow resolved** — the gate deferred through 12.6–12.9 is closed.
- **Live job-source reachability verified** — the running app returned results from ARBEITNOW,
  REMOTIVE and OPENINGS-MCP with `live: true`.
- **Employer handoff verified as an opening, not a submission** — `employerOpenedAt`/`employerUrl`
  are persisted; the platform never claims a submission it cannot observe.

### Limitations that remain open after 12.10

- SMTP transport is not configured: application emails are `SENT_SIMULATED` and labelled.
- Candidate endpoints are unauthenticated and ownership-free; the app binds to `127.0.0.1` and is a
  trusted single-user local-first prototype. Networked/multi-user deployment needs an
  identity/ownership/transport-security design.
- Docker was unavailable in the latest verification run, so `init.sql` was never applied to a live
  PostgreSQL/pgvector container and the 15 Testcontainers tests were skipped.
- Phase 12.5 verification evidence is still not recorded.

---

## Current Phase 12.2 Blockers (For Tracking)

| Blocker | Severity | Mitigation |
|---------|----------|------------|
| Browser automation environment | High | Shiplight works but Spring Boot devtools causes process exit |
| Ollama cold-start/runtime | High | First inference ~290s; subsequent ~2-5s |
| Application Advisor modal DOM missing | Resolved | Historical — superseded by shared modal shell rendering |
| Prepared Application modal broken | Resolved | Historical — superseded by shared modal shell rendering |
| Career Agent "WAITING" artifact | Resolved | Frontend artifact, not backend bug; progress list removed (`7fb6e18`) |

---

## Completed in Phase 12.2 Prep (f47562b)

> Historical record. Timeouts have since changed: the server-side AI deadline is now
> `ollama.reasoning-timeout: 300s` and the client watchdog is 300s (`CLIENT_TIMEOUT_MS`).

- [x] Timeout increased at the time: 120s → 600s server, 150s → 630s client (later reduced to 300s / 300s)
- [x] Model references updated: `gemma3:4b` → `llama3.2:3b`
- [x] Cold-start comment added to `resume.js`
- [x] `.gitignore` updated with `node_modules/`
- [x] Test artifacts cleaned up
- [x] Checkpoint commit: f47562b

---

## Phase 12.2 Status (SUPERSEDED — historical snapshot)

> This table is the Phase 12.2 checkpoint view. Every ❌/⚠️ below was resolved later; it is kept
> for history only. Current state: all UI surfaces render through the shared modal shell, the
> keyword fields are gone, live job sources are the default, and the Career Agent artifact was
> removed. See the Phase Status table at the top.

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

> **SUPERSEDED**: the items below belonged to the legacy 12.2 investigation
> (INVESTIGATION-REPORT §8/§9.1) and are **all resolved**. The UI migrated to the single global
> modal shell (`modalShell.js`) — Application Advisor, Career Analysis, Application
> Readiness, prepared-application review, and ATS tailoring all render through it, and the
> dedicated `advisorReviewJobTitle`/`advisorReviewCompany`/`prepReviewOverlay` wrappers no longer
> exist in `matches.html`. `ApplicationAdvisorResponse` already carries `jobTitle`/`company` echo
> fields. Kept only for historical reference; do not execute.

**Legacy action list (pre-modal-shell, all done):**
1. `matches.html` — Add `advisorReviewJobTitle` / `advisorReviewCompany` header elements
2. De-duplicate 4 advisor DOM IDs (keep on `<ul>`, remove from wrapper `<div>`)
3. Restore `<div class="prep-review-overlay" id="prepReviewOverlay" hidden>` wrapper
4. `matches.js` — Null-guard new lookups; relax `data.applicationId != null` gate
5. `ApplicationAdvisorResponse` — Add `jobTitle`/`company` fields
6. `ApplicationAdvisorService.adviseFromDomain` — Populate from `Job`

**Current next action:** Cleanup Batch 4 (documentation consistency) is in progress and uncommitted.
Cleanup Batch 5 has not been started or scoped.

---

**END OF TASKS.md**