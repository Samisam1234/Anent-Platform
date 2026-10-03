# PHASE 12.9 SPEC STATUS: COMPLETE AND VERIFIED — implemented and accepted 2026-09-30 (see §11 verification record)

## FULL SPECIFICATION — Phase 12.9 Application Tracking

### 1. Goal

Turn the prepared-application lifecycle into an observable, truthful tracking surface: a
**status-filtered application list**, a **validated status-transition model**, and a
**persisted, honest record of the events the platform can actually observe** — preparation,
editing, approval, email-send attempts (real vs simulated), and the employer-site handoff.

The phase has one non-negotiable honesty rule: **the platform only records events it can
observe, and it never claims or implies that an employer application was submitted.** An
employer page opening is an observed event; a submission on that page is not observably by this
platform, so no status, timestamp, or UI copy may assert it.

Phase 12.9 does **not**: automate employer sites, detect submissions, add CAPTCHA/MFA or
credential handling, send email unattended, or treat the Phase 12.8 client-side Apply Kit marker
as authoritative history.

The spec is grounded exclusively in existing code (HEAD `434b7f7`). Where a capability already
exists it is marked **EXISTS** and is reused; where behavior is missing it is declared a
**12.9 DELTA**.

### 2. Existing state (confirmed by inspection — EXISTS)

| Item | Evidence | Meaning for 12.9 |
|---|---|---|
| `ApplicationStatus` enum: `DRAFT, GENERATED, UNDER_REVIEW, APPROVED_FOR_APPLICATION, REJECTED, ARCHIVED` | `orchestrator/.../application/ApplicationStatus.java:9-16` | No `SENT`/`EMAIL_SENT` value exists; `UNDER_REVIEW` and `ARCHIVED` are **never written by any production path** (only enum + frontend `STATUS_LABELS` + CSS). Redundant `getStatus()` (L24-26) mirrors `name()` |
| `JobApplication` columns: `id, candidateId, jobId, jobTitle, company, location, applicationStatus, generatedResumeSummary, coverLetter, applicationAnswers, candidateStrengths, matchingSkills, missingSkills, resumeHighlights, matchScore, recommendation, createdAt (updatable=false), updatedAt (manual), approvedAt (approve only)` | `orchestrator/.../application/JobApplication.java:11-80` | No `emailSend*`, `employerOpenedAt`, or `@Version`. Preparation timestamps = `createdAt`; edits bump `updatedAt`; approval = `approvedAt` |
| Status writes today: `DRAFT` (ctor L78), `GENERATED` (prepare), `APPROVED_FOR_APPLICATION`/`REJECTED` (storage service) | `JobApplicationController.java:66`, `ApplicationStorageService.java:94,111` | Produce/consume as below |
| `findByCandidateId(Long)` only finder; no status/order/`Pageable` | `JobApplicationRepository.java:10` | No filter or deterministic ordering server-side |
| `getApplicationsByCandidate` returns raw list, no filter/order | `JobApplicationController.java:143-147` | List API needs an optional `status` param + deterministic order |
| `approve()` / `reject()` **unconditional** — no source-status guard, no matrix, no transition exception | `ApplicationStorageService.java:90-115` | A `REJECTED` or `ARCHIVED` record can be re-approved today; reject can hit any status. 12.9 introduces the first server-side transition enforcement |
| `IllegalArgumentException` → RFC 7807 400 | `ui/.../controller/GlobalExceptionHandler.java:76-88` | Reused for invalid transitions and invalid filter values; no new exception type needed |
| Applications controllers return **empty-body 404** on missing ids | `JobApplicationController.java:131-134` | Keep unchanged; 12.9 does not change 404 semantics |
| Email send gates: must be `APPROVED_FOR_APPLICATION`, `approved==true`, syntactically-valid recipient; returns `ApplicationSendResult` and **persists nothing** | `ui/.../controller/ApplicationEmailController.java:45-101`; `ApplicationSendResult.java` (`SENT`/`SENT_SIMULATED` both carry status string `"SENT"`, distinguished by `simulated` flag) | The "no persisted email-sent flag" gate (`TASKS.md:173`) is a 12.9 dependency; outcome recording is a 12.9 DELTA |
| `ApplicationSendResult.SENT` vs `SENT_SIMULATED`: identical status string, flag differs | `ApplicationSendResult.java:25-38` | Persist the **result string** + `simulated` disambiguation so real vs simulated is never conflated |
| Apply Kit handoff writes **client-only** marker `agentplatform:applyKit:<applicationId>` (`openedAt`/`jobUrl`/`values`, plus `reviewedAt`/`employerOpenedAt` on handoff); **zero readers** exist; explicitly "never a submission record" | `applications.js:519-535, 683-697` (`writeKitMarker`, `kitHandoff`); `PHASE_12.8_SPEC.md:160` | The marker is the documented 12.9 migration seam. It is **write-only dead state** today — nothing reads it, so removal loses nothing and legacy values need no migration. It must never become authoritative history |
| Kit URL provenance: `window.jobLink.resolve(job)` via `jobLink.safeUrl`; job resolved from package `jobId` via `GET /api/v1/jobs/{id}` | `applications.js:751-783` | The employer URL the kit hands off is already validated/provenanced client-side; the server records only that validated string |
| Status re-validation on kit return compares `applicationStatus`/`updatedAt`/`generatedResumeSummary` → `blocked-stale` | `applications.js:537-551` | Already consumes server state; will naturally notice any 12.9 status change |
| `STATUS_LABELS` maps the six statuses to `{label, cls, hint}`; badges `.badge-draft|generated|under-review|approved|rejected|archived` | `applications.js:61-68`, `style.css:88-92,963` | No `badge-email-sent`; no timeline surface |
| Applications toolbar has title + count only; no filter control; `loadApplications` renders every row, count = `data.length` | `applications.html:81-84`, `applications.js:1347-1409` | No status filter in UI; reusable `.filter-*` pattern exists on Jobs/Matches |
| Detail view is an inline section (`#applicationDetailSection`) + shared-`modalShell` review modal | `applications.html:94-195`, `applications.js:321-401, 901-1013` | "Application detail view" roadmap bullet is **already satisfied**; 12.9 only adds the timeline |
| Tests: `JobApplicationControllerTest` (approve/reject happy + 404), `ApplicationStorageServiceTest` (`Nested` approve/reject), `JobApplicationLongTextFieldsPersistenceTest`, `ApplicationPreparationServiceTest` (draft never `SENT`), `ApplicationEmailControllerTest` (status gates, SENT/SENT_SIMULATED) | `orchestrator/src/test/...` | No matrix/guard tests exist (no guard to test); no filter tests; no email-outcome-persistence tests |
| No JS test framework; acceptance via committed browser harness | `PHASE_12.8_SPEC.md`, RULES.md §6 | 12.9 UI verified by browser acceptance checkpoints |

### 3. Policy boundary and the honesty rule

PRD non-negotiables that constrain 12.9: "Manual/User-Authorized Application — Enforced",
"No Auto-Submit — Enforced", "Official/Source URLs Only" (`PRD.md:43-45`). The Phase 12.8 rule
stands: the platform only assists preparation and transfer; it never writes to or submits a page
it does not serve.

**Distinctions the spec is built on (exact meanings):**

| Thing | Meaning | Observable by this platform? | Represented as |
|---|---|---|---|
| Prepared application package | A profile+job-derived package was created (`GENERATED`) | Yes | status `GENERATED` |
| Approval to proceed | User explicitly approved the package | Yes | status `APPROVED_FOR_APPLICATION` + `approvedAt` |
| Email send attempt | An email-send request passed all gates and the transport accepted it | Yes | `emailSendAttemptedAt` + `emailSendResult` |
| Confirmed email delivery | The recipient truly received it | **No** (no read-receipt/DMARC; out of scope) | Never represented; `SENT` is transport acceptance only |
| Employer-site page opened | User pressed the kit handoff and the tab opened | Yes | `employerOpenedAt` + `employerUrl` (event only) |
| Employer application submitted | The user actually submitted on the employer site | **No, ever** | Never represented; UI copy prohibits implying it |

### 4. Status model and transition matrix (12.9 DELTA)

#### 4.1 Status values

Keep the six existing values unchanged. Add **one** value:

- **`EMAIL_SENT`** — the package's email was sent via a **real (non-simulated)** send that the
  transport accepted (`ApplicationSendResult` result string `SENT`, `simulated == false`).

**Why `EMAIL_SENT` and not a bare `SENT`:** the roadmap shorthand `DRAFT → GENERATED → APPROVED →
SENT` (`TASKS.md`) reads, in an employer-application context, as "application sent to employer".
That is precisely the claim the platform cannot make. `EMAIL_SENT` states what the event actually
was. The roadmap's bare `SENT` is interpreted as `EMAIL_SENT` from here on.

`UNDER_REVIEW` and `ARCHIVED` remain in the enum for schema stability (frontend `STATUS_LABELS`
and consumers already reference them) but are **reserved**: no production path writes them.
Transitions treat them as defined below.

#### 4.2 Transition matrix

Actions are defined by endpoint. A transition not listed is **invalid** and returns
**400 RFC 7807** (throw `IllegalArgumentException` from the service → `GlobalExceptionHandler`
`handleIllegalArgument`, message naming the source status).

| From \ Target | `GENERATED` | `UNDER_REVIEW` | `APPROVED_FOR_APPLICATION` | `REJECTED` | `ARCHIVED` | `EMAIL_SENT` |
|---|---|---|---|---|---|---|
| `DRAFT` | prepare (EXISTS) | — | ✗ | ✗ | ✗ | ✗ |
| `GENERATED` | — | — | approve | reject | ✗ | ✗ |
| `UNDER_REVIEW` | — | — | approve | reject | ✗ | ✗ |
| `APPROVED_FOR_APPLICATION` | ✗ | — | approve (idempotent) | reject | ✗ | email send, real `SENT` |
| `REJECTED` | ✗ | — | ✗ | reject (idempotent) | ✗ | ✗ |
| `ARCHIVED` | ✗ | — | ✗ | ✗ | — (terminal) | ✗ |
| `EMAIL_SENT` | ✗ | — | ✗ | ✗ | ✗ | — (terminal) |

Rules:

- **prepare** — unchanged: `POST /api/v1/applications/prepare` creates a fresh `GENERATED` row.
- **approve** — allowed from `GENERATED` / `UNDER_REVIEW`; **idempotent** from
  `APPROVED_FOR_APPLICATION` (200, entity unchanged, `approvedAt` not rewritten). Any other
  source → 400.
- **reject** — allowed from `GENERATED` / `UNDER_REVIEW` / `APPROVED_FOR_APPLICATION`;
  **idempotent** from `REJECTED` (200, unchanged). Any other source → 400.
- **email send** — existing gate (`APPROVED_FOR_APPLICATION` + `approved==true` + valid
  recipient) stays. Real `SENT` result → transition to `EMAIL_SENT` (terminal). `SENT_SIMULATED`
  result → **no status change** (re-send stays possible while SMTP is unconfigured). Send when
  already `EMAIL_SENT` → 400 "email already sent". `REJECTED`/`FAILED` results → 400, status
  untouched (as today).
- **Terminal states**: `EMAIL_SENT`, `REJECTED`, `ARCHIVED`. No transition may leave a terminal
  state. Re-opening a rejected package for a new attempt is done by preparing a **fresh**
  application, not by mutating the rejected row.
- **Behavior change (intended, spec'd):** today `approve()` re-approves any status (including
  `REJECTED`/`ARCHIVED`) silently. 12.9 closes that: those become 400s. This is a deliberate
  honesty fix, covered by tests.

#### 4.3 Why no "applied/submitted" status

A user pressing "Open employer site to apply" and even pasting every field is **not** evidence a
form was submitted. Any status like `APPLIED`/`SUBMITTED` would be fabricated. The handoff event
(§6) is the ceiling of what tracking can record honestly.

### 5. Email outcome semantics (real vs simulated) — precise definitions

Persisted fields on `job_applications` (12.9 DELTA; see §7):

- **`emailSendAttemptedAt`** (datetime, nullable) — set on every **accepted** send attempt
  (passing all gates and returning `SENT` or `SENT_SIMULATED`). Repeated accepted attempts
  overwrite it (simulated re-sends) — it means "last accepted send attempt".
- **`emailSendResult`** (varchar, nullable) — the `ApplicationSendResult` status string of the
  last accepted attempt: `SENT` (real, `simulated=false`) or `SENT_SIMULATED` (nothing mailed).
  The `simulated` flag is derivable from `SENT_SIMULATED`, so no separate boolean column.*

Do **not** add a field named `sentAt`/`emailSentAt` that reads as "definitely delivered": the
transport result is acceptance, not delivery. `emailSendAttemptedAt` + `emailSendResult` plus the
**status** `EMAIL_SENT` (reached only via real `SENT`) is the maximum truthful representation.

Transitions: real `SENT` also sets status `EMAIL_SENT` and bumps `updatedAt` (keeps `approvedAt`
unchanged). Simulated leaves status `APPROVED_FOR_APPLICATION`.

`REJECTED`/`FAILED` sends: 400 to the client, nothing persisted, status unchanged — nothing was
sent, so no record is warranted.

UI/API labels (mandatory wording):
- Real `SENT`: "Email send reported by email service" (never "delivered").
- `SENT_SIMULATED`: "Email send simulated — no SMTP configured. Nothing was actually mailed."
- `EMAIL_SENT` badge hint: "Package email sent (reported by email service); employer submission
  not confirmed."

### 6. Employer handoff event recording (12.9 DELTA)

#### 6.1 Design

New endpoint `POST /api/v1/applications/{applicationId}/handoff`, body `{ "url": "<https url>" }`.

- **Eligibility**: application must exist (else empty-body 404, matching current controller
  semantics); status must be `APPROVED_FOR_APPLICATION` (else 400 via `IllegalArgumentException`).
- **URL validation (server-side, conservative)**: non-blank; parseable absolute URI; scheme
  `https` or `http`; non-empty host. This is a server-side approximation of the client-side
  `jobLink.safeUrl` family — it only sanity-checks the string the kit already resolved; the
  server never fetches or reaches the employer origin. Invalid → 400.
- **Recording**: set `employerOpenedAt = now` (overwrites — it is "last opened") and
  `employerUrl = validated url`; bump `updatedAt`. **No status change. No submission claim.**
  No additional browsing information, no polling, no later access to the employer site.
- **Audit data**: `applicationId`, validated `url`, timestamp. Nothing else.
- **Client flow** (`applications.js kitHandoff`): after the existing acknowledgement checkbox,
  the flow (1) POSTs the handoff (best-effort, fire-and-forget), then (2) `window.open`s the
  kit's already-validated target URL. If the POST fails, a non-blocking toast ("could not record
  this opening") is shown and the external open still proceeds — recording must never block the
  user from applying.
- **Marker supersession**: after Slice 2, `writeKitMarker` is **removed** — the server row is the
  only history. Legacy `agentplatform:applyKit:` values in localStorage are ignored (zero readers
  today; nothing is lost, nothing is migrated, and they are never treated as authoritative).

### 7. Persistence changes (12.9 DELTA)

New columns on the existing `job_applications` table (via `ddl-auto: update`, consistent with
prepared-content `TEXT` precedent):

| Column | Type | Nullable | Meaning |
|---|---|---|---|
| `email_send_attempted_at` | `DATETIME` | yes | Last accepted email-send attempt (§5) |
| `email_send_result` | `VARCHAR(20)` | yes | `SENT` or `SENT_SIMULATED` (§5) |
| `employer_opened_at` | `DATETIME` | yes | Last employer-site handoff event (§6) |
| `employer_url` | `VARCHAR(2048)` | yes | Validated handoff URL (§6) |
| `version` | `BIGINT` | no (`@Version`) | Optimistic-lock backstop for concurrent transitions |

Existing columns reused unchanged: `createdAt` (preparation), `updatedAt` (edits + all
transitions), `approvedAt` (approval).

**Server-persisted events**: preparation, edits, approval, accepted send attempts, real-send
status, handoff open. **Client-only**: the (now-removed) localStorage kit marker and any UI
preference (e.g. the saved model selector); these are never authoritative.

**Consistency / idempotency / retry:**
- The email-send outcome + status transition happen in **one transaction** (single
  `@Transactional` service method called with the transport result). Documented residual window:
  a crash between transport success and commit loses the outcome — the record stays
  `APPROVED_FOR_APPLICATION`, the user re-sends, and (real path) a duplicate is an acknowledged
  edge; the UI never claims delivery so no false history exists.
- **Duplicate send**: status `EMAIL_SENT` → further sends return 400 "email already sent"
  (no transport invocation). Simulated state stays `APPROVED_FOR_APPLICATION`, so re-sends are
  legitimate and each accepted attempt is recorded.
- **Optimistic concurrency**: `@Version` aborts a stale concurrent write
  (`ObjectOptimisticLockingFailureException` → map to **409 Conflict** via a new small
  `@ExceptionHandler`, mirroring `GlobalExceptionHandler`). Applies to approve/update/send/handoff.

### 8. Backend API changes (minimal; grounded in existing controllers)

| Endpoint | Change |
|---|---|
| `GET /api/v1/applications/candidate/{candidateId}` | Add optional `?status=` query param (enum name, case-insensitive). Blank/absent → all. Unknown value → 400. Ordered **`updatedAt DESC, id DESC`** (deterministic). Response stays `List<JobApplication>` |
| `POST /api/v1/applications/{id}/approve` | Guard via matrix §4.2; 400 on invalid transition (was unconditional) |
| `POST /api/v1/applications/{id}/reject` | Guard via matrix §4.2; 400 on invalid transition |
| `POST /api/v1/applications/email/send` | Persist outcome (§5) + transition to `EMAIL_SENT` on real `SENT`; 400 "already sent" from `EMAIL_SENT`. No endpoint invented — this is an **extension** of the existing email send |
| `POST /api/v1/applications/{id}/handoff` | **New, minimal** — the only genuinely new endpoint: no existing route fits recording "employer tab opened" without conflating approval or transport semantics (§6) |

Repository additions (`JobApplicationRepository`): `List<JobApplication>
findByCandidateIdOrderByUpdatedAtDescIdDesc(Long candidateId)` and the same finder filtered by
status (Spring Data `And` + ordering) — or a single `findByCandidateId(Long, Sort)`; the slice
picks whichever keeps the derived finders explicit and testable.

**No plans**: no pagination (candidate application counts are small; YAGNI), no PATCH status, no
`/archive` endpoint (ARCHIVED stays reserved), no new exception type, no change to 404 semantics,
no change to prepare/update contracts.

### 9. Applications UI and timeline (12.9 DELTA)

- **Toolbar filter** (`applications.html` toolbar, `applications.js loadApplications`): a
  `<select id="applicationsStatusFilter">` with "All Applications" + one option per status
  (labels from `STATUS_LABELS`, badge-colored). Change → re-fetch with `?status=`. Count shows the
  **filtered** result length. Invalid value → fall back to "All" + toast (defensive; server 400s
  anyway).
- **Badges**: add `STATUS_LABELS.EMAIL_SENT = {label: "Email sent", cls: "email-sent",
  hint: honest §5 hint}` and `.badge-email-sent` CSS. Existing badges untouched.
- **Detail timeline** (new block in `#applicationDetailSection`, rendered only from non-null
  persisted fields):
  - "Prepared" — `createdAt`
  - "Last updated" — `updatedAt` (when ≠ `createdAt`)
  - "Approved for application" — `approvedAt`
  - "Email send attempt" — `emailSendAttemptedAt` + result label (§5 mandatory wording)
  - "Employer site opened" — `employerOpenedAt` + `employerUrl` link + **mandatory suffix**
    "— submission not confirmed by this platform"
- **Copy rule**: the UI must never present any status, badge, or timeline row as a confirmed
  employer submission. The word "submitted" is prohibited in status/hint/timeline/empty-state
  copy. "applied" only appears in product names ("Assisted Apply", "Apply Kit").
- **Action buttons per status** (`updateActionButtons` extension):
  - `DRAFT` / `GENERATED` / `UNDER_REVIEW`: Edit, Approve
  - `APPROVED_FOR_APPLICATION`: Edit, Assisted Apply, Send email (unchanged)
  - `EMAIL_SENT`: no package action buttons (terminal; no resend)
  - `REJECTED` / `ARCHIVED`: no action buttons
- **States**: empty → existing `#applicationsEmpty`; filtered-empty → "No applications with this
  status" + "Show all" button; loading → existing fetch with a disabled filter/count update;
  error → toast + "Retry" re-invoking `loadApplications`.

### 10. Implementation slices

Each slice is independently implementable, testable, reviewable, and commit-able. Convention
follows 12.6–12.8 (spec first, docs-sync last). **All five slices are executed**; Slices 1 and 2
shipped as a single commit (`45afa38`), and the executed mapping is recorded in §14.

**Slice 1 — Status model, transition matrix, truthful email outcome tracking.**
- Changes: `ApplicationStatus` (+`EMAIL_SENT`); `JobApplication` (+`emailSendAttemptedAt`,
  `emailSendResult`, `version`); `ApplicationStorageService` approve/reject guards; email send
  outcome persistence + real-`SENT` transition + "already sent" 400 (extend
  `ApplicationEmailController` + service); new 409 handler.
- Reuse: existing `GlobalExceptionHandler` (400 path), existing email gate/`buildDraftFromApplication`.
- Tests: transition-matrix unit tests (every allowed/invalid pair, idempotent cases, terminal
  immutability); email outcome tests (real `SENT` → `EMAIL_SENT` + persisted result; simulated →
  no status change + persisted; `REJECTED`/`FAILED` → 400 + unchanged row); `ApplicationStatus`
  value-set test; send-when-`EMAIL_SENT` 400.
- Browser checkpoints: (via fixture + mock transport) real-send path transitions badge to
  "Email sent"; simulated send leaves status approved and re-send remains available; timeline
  wording matches §5.
- Dependencies: none beyond 12.8. Acceptance: matrix enforced; outcomes persisted truthfully.

**Slice 2 — Server-side employer handoff event recording, integrated with the Apply Kit.**
- Changes: `JobApplication` (+`employerOpenedAt`, `employerUrl`); `ApplicationStorageService`
  handoff method; `JobApplicationController` `POST /{id}/handoff`; `applications.js kitHandoff`
  POSTs then opens the validated tab; remove `writeKitMarker`.
- Reuse: kit's `s.target` URL (already `jobLink.safeUrl`-approved), acknowledgement gate.
- Tests: handoff 200 (event persisted, no status change, no submission fields), 404, 400
  not-approved, 400 bad URL, idempotent re-open overwrites timestamp.
- Browser checkpoints: acknowledged handoff → timeline "Employer site opened" row with
  "-submission not confirmed" and URL; POST failure → toast + tab still opens; marker key no
  longer written.
- Acceptance: handoff recorded without submission claims; marker superseded.

**Slice 3 — Status-filtered Applications list + deterministic ordering.**
- Changes: `JobApplicationRepository` finders; `JobApplicationController` `?status=` (validate →
  400) + `updatedAt DESC, id DESC`; `applications.html` filter `<select>`; `applications.js`
  filter fetch + filtered count.
- Reuse: `.filter-*` CSS/markup pattern (Jobs/Matches), `STATUS_LABELS`.
- Tests: controller filter (each status, blank=all, unknown→400, ordering deterministic);
  repository slice test.
- Browser checkpoints: filter each reachable status → only matching cards + count; "All" restores;
  invalid option falls back to All.
- Acceptance: list is filterable and deterministically ordered.

**Slice 4 — Badges, timeline, action-state behavior.**
- Changes: `STATUS_LABELS.EMAIL_SENT`, `.badge-email-sent`, timeline block rendering in
  `viewApplication`, `updateActionButtons` per §9.
- Tests: none backend (UI only); browser acceptance.
- Browser checkpoints: EMAIL_SENT badge; timeline rows appear only for non-null events; prohibited
  copy absent (grep assertion "submitted"); button sets per status; modal/board fit 1440/768/390.
- Acceptance: honest, complete tracking UI.

**Slice 5 — Documentation sync + full Phase 12.9 acceptance.**
- Changes: this spec → COMPLETE AND VERIFIED; update `TASKS.md`, `PRD.md` (journey row 10),
  `RULES.md` (phase tables), `ARCHITECTURE.md` (data-flow + phase row), `DESIGN.md` (timeline +
  verification evidence), `MEMORY.md` (checkpoints + totals).
- Verification: full `mvn clean test` (hermetic gate), full browser acceptance run (A–P-style),
  record evidence; commit; stop for review.

### 11. Verification

- **Unit**: `ApplicationStorageServiceTest` matrix expansion (all pairs), timestamp semantics;
  `ApplicationStatus` value-set test.
- **Repository**: `@DataJpaTest` for new finders (filter + ordering).
- **Controller**: `JobApplicationControllerTest` for `?status=` (valid/blank/unknown), approve/
  reject 400-on-invalid-transition; `ApplicationEmailControllerTest` for real-vs-simulated
  outcome persistence, rejected/failed non-persistence, already-sent 400; new handoff tests
  (200/404/400, no submission fields).
- **Phase gate**: full `mvn clean test` — Failures=0, Errors=0 (`RULES.md` invariant).
- **Browser acceptance** (hermetic): route-intercepted fixtures (kit job + advisor-path job with
  valid `applicationUrl`, canned advisor response) + **mock/route-intercepted email transport** so
  both real and simulated outcomes are exercised deterministically — **no real email, no external
  employer-site automation, no network required**. Checkpoints cover filter/badges/timeline/
  action-state/handoff/wording per §9-§10.

#### Verification record (2026-09-30 — Phase 12.9 COMPLETE AND VERIFIED)

- **Phase gate**: full `mvn clean test` → **BUILD SUCCESS — 1,278 tests, 0 failures, 0 errors,
  15 skipped** (agent-core 135, logging 14, memory-service 39 with 9 skipped, tool-service 10,
  orchestrator 933, ui 132, rag-service 15 with 6 skipped; the 15 skips are the pgvector
  profile-gated and `RagServiceTest` cases carried from earlier phases).
- **Browser acceptance, API axis — 10/10 checkpoints passed** against a deterministic fixture
  (7 applications over 2 candidates, seeded through the H2 console): baseline ordering
  (`updatedAt` DESC, id DESC tie-break), all status filters + unknown-status 400 naming every
  valid status, candidate scoping, approve (valid, idempotent — a repeat with a 1.2 s pause does not
  rewrite `approvedAt` — from `UNDER_REVIEW`, and 400 from `REJECTED`/`EMAIL_SENT`/`ARCHIVED`),
  reject (idempotent, 400 from terminals, and 200 from `APPROVED_FOR_APPLICATION`), email send
  (simulated 200, repeat allowed while unapproved, already-`EMAIL_SENT` 400, unapproved 400,
  missing-approval-flag 400), persisted truthfulness (status stays `APPROVED_FOR_APPLICATION` with
  `emailSendResult = SENT_SIMULATED`), handoff (first write, overwrite, 400 for a relative URL and
  for `ftp`, 400 when not approved), and final ordering.
- **Browser acceptance, UI axis — 10/10 checkpoints passed**: list order + per-status badges +
  card actions; per-status detail timeline and action visibility via deep links (EMAIL_SENT,
  REJECTED-with-simulated-email-and-handoff, ARCHIVED, APPROVED-with-handoff, APPROVED clean);
  filter counts, filtered-empty + Show all, invalid-filter fallback toast; candidate scoping; valid
  approve through the shared confirm dialog (`#msTitle` = "Approve for manual submission" →
  disabled "Approved" chip → success toast); first-run empty state; responsive detail + review
  modal at 1440/768/390; reload preserving the deep-linked detail; a route-aborted load showing
  "Could not load applications." + Retry that recovers; and a copy scan proving every "submitted"
  mention in the served `applications.js` is a negation with no "delivered" claim anywhere.
- **Cancellation** (supplementary, `slice5-cancel-qa.mjs` — 1/1): opening "Send Email" on an
  approved application shows the recipient confirm dialog (`#msTitle` = "Send application email",
  required recipient input, "Send email" accept); cancelling closes the overlay with no error
  toast and leaves the row untouched (`status`, `emailSendAttemptedAt`, `emailSendResult` all
  identical before/after) — no send is attempted on cancel. Zero console errors in that run.
- **Preparation** was not re-driven live in this slice: the deterministic 12.9 fixture contains
  `job_applications` rows without backing `candidate_profiles` rows, and `prepare` correctly
  requires a stored profile, so a live call returns 404 `CandidateProfileNotFoundException`.
  Preparation is therefore evidenced by the hermetic orchestrator suite (933 tests) and the
  Phase 12.7 acceptance, while package **retrieval** (list + single detail) is verified live here.
- **Optimistic lock 409** is covered hermetically by
  `GlobalExceptionHandlerTest.optimisticLockMapsTo409Conflict` (mapping
  `ObjectOptimisticLockingFailureException` → RFC 7807 409); a live two-tab race was not
  manufactured for acceptance.
- **Real-vs-simulated email**: live sends deterministically return `SENT_SIMULATED` because
  `ApplicationEmailService` is constructed without the email tools (`emailTools == null`); the real
  `SENT` path is exercised by the hermetic email unit tests. Acceptance observed the simulated
  path end-to-end and asserted it is labelled and persisted, never described as delivery.
- **Two console errors were intentional**: the 400 from the injected stale status filter and the
  aborted request used to exercise the Retry path. No other console or network errors occurred.
- **Deferred gates unchanged** (§12): D2 page-level overflow re-measured on the Applications page
  (51px @768, 429px @390; the detail surface and review modal fit at all three widths) — still a
  12.10 item; live job-source reachability / advisor intermittency not claimed; SMTP unconfigured;
  employer-site opening is never proof of submission; candidate endpoints unauthenticated and
  ownership-free (loopback-only); networked/multi-user deployment still blocked; Phase 12.5
  verification outstanding.

### 12. Security and deferred gates (preserved — not resolved by this spec)

- **Loopback-only**: `GET /api/v1/candidate/{candidateId}` and all application endpoints remain
  unauthenticated and ownership-free; `server.address: 127.0.0.1` keeps the trusted single-user
  local-first prototype isolated. **Networked/multi-user deployment remains blocked** pending a
  real identity, candidate-ownership, and transport-security design. No partial auth, ID
  obscurity, or rate-limit workarounds are added (`PHASE_12.8_SPEC.md:168-174` carried forward).
- **No automatic employer-application submission** or unattended employer-site interaction, at any
  point.
- **No automatic email sending** without explicit user approval — the `approve` + `approved==true`
  + user-confirmed recipient gates (12.7) are unchanged.
- **D2 page-level mobile overflow (≤768px)** remains deferred to Phase 12.10 (modal/board-level
  fit is still required).
- **Live job-source reachability and advisor lookup reliability** remain unverified/unreliable as
  reported in 12.8 (advisor single-job `JobNotFoundException` race) — a 12.10 item.
- **Phase 12.5 verification** remains outstanding for the Phase 12.10 regression gate.
- **SMTP unconfigured**: in the default environment the real-send path is only exercised via the
  route-intercepted transport in acceptance; users see simulated-labeled outcomes and
  `EMAIL_SENT` is not reachable without a configured transport.
- No deferred item is marked resolved without new evidence.

### 13. Non-goals

No submission detection or claim of it; no automation of employer-page DOM; no CAPTCHA/MFA or
credentials; no real email delivery outside acceptance's mocked transport; no new frontend/JS
framework; no status-history table (the persisted timestamps + matrix are the history); no
pagination; no `/archive` or PATCH-status endpoint; no removal of reserved enum values; no D2 fix;
no live job-source changes; no change to 404 semantics; no collection of browsing information
beyond the single validated handoff URL + timestamp.

### 14. Commits (as executed)

Actual commit subjects, in order:
- `f21b09c` — `Phase 12.9 - add application tracking specification` (Slice 0)
- `45afa38` — `Phase 12.9 - add status transitions and employer handoff` (Slices 1–2 merged:
  transition matrix, email-outcome persistence, handoff recording)
- `28d49aa` — `Phase 12.9 - add status-filtered application list` (Slice 3)
- `95da0e9` — `Phase 12.9 - add application timeline and status action states` (Slice 4)

Slice 5 (documentation sync + acceptance record) is committed as
`Phase 12.9 - documentation sync and acceptance evidence`; it changes documentation only, with no
source or test edits.

Phase 12.9 is **COMPLETE AND VERIFIED** as of 2026-09-30 on the evidence recorded in §11.