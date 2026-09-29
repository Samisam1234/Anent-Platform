# PHASE 12.7 SPEC STATUS: IMPLEMENTED — Slices 1–3 (acceptance verification pending)

## FULL REVISED SPECIFICATION — Phase 12.7 Application Package

### 1. Goal

The **prepared-application backend and most of the flow already exist** in this repository
(profile-derived, not a hardcoded template). Phase 12.7 takes the existing flow to a
complete, reviewable Application Package: the prepared-review modal rendered from a real
`ApplicationPreparationResult`, a user-verifiable email recipient before any send, an
explicit approval gate, and edit support — with no invented candidate data at any step.

The spec is grounded exclusively in existing code; where a capability already exists it is
marked **EXISTS** and is not re-built, it is only wired/completed. Where behavior is missing
it is declared a **12.7 DELTA**.

### 2. Inputs (all exist)

| Input | Source | Notes |
|---|---|---|
| `candidateId: Long` | `ApplicationPrepareRequest{candidateId, jobId, jobTitle, company, location, customInstructions}` (existing, validated) | → `CandidateProfilePersistenceService.getByIdOrThrow()` → `CandidateProfile` |
| `jobId: String` | same body | → `JobSearchService.findById()` → `Job`; mock jobs run the deterministic pipeline (`skipAi = isMockJob`) |
| `ApplicationPreparationResult` | `JobApplicationPreparationService.prepareApplication(profile, job, jobTitle, company, location, customInstructions, skipAi)` | EXISTING record: `applicationId, candidateId, jobId, jobTitle, company, tailoredProfessionalSummary, coverLetter, suggestedAnswers, matchingSkills, missingSkills, candidateStrengths, resumeHighlights, matchScore, recommendation, status("GENERATED"), createdAt`. Profile+job driven; deterministic fallback when no AI; never invents; `matchScore` = deterministic skill-coverage |
| `JobApplication` | `ApplicationStorageService.store(...)` (status `GENERATED`) | persisted per prepare; read back by `GET /api/v1/applications/{id}` / `GET /api/v1/applications/candidate/{candidateId}` |

### 3. Prepared-review modal — rendering gap (12.7 DELTA, frontend-only)

`openPreparedReview(result)` (`matches.js:623`, single global `modalShell`) already renders:
summary chips (company, skill coverage, **"Prepared for your review — not submitted"**),
`matchingSkills`, `missingSkills`, tailored summary + Copy, cover letter + Copy,
`resumeHighlights`, and the safety footer ("Nothing has been submitted. Review, then apply
on the employer's own site.").

**The modal does NOT render the other `ApplicationPreparationResult` fields.** Deltas:

1. Render `suggestedAnswers` (comma-joined → split into an ordered Q&A list) as a
   "Suggested answers" section — **Q&A kept behind the same Copy affordance**; no Q&A
   auto-injection anywhere.
2. Render `candidateStrengths` as a "Strengths to emphasise" list.
3. Render `recommendation` (existing `RecommendationLabel`/`recommendationTone`
   helpers at `matches.js:1271-1284`) + `matchScore`.
4. Keep every section optional (render only when non-empty) — `listOrEmpty` already
   exists for exactly this.

No new modal architecture, no new frontend framework. One handler, one dialog, upgraded
content — same shape as the 12.6 preview upgrade.

### 4. Approval + apply flow (most exists)

- **EXISTS** — `POST /api/v1/applications/{applicationId}/approve` → status
  `APPROVED_FOR_APPLICATION` + `approvedAt`; `POST .../reject` → `REJECTED`.
  `ApplicationStatus` enum: `DRAFT, GENERATED, UNDER_REVIEW, APPROVED_FOR_APPLICATION,
  REJECTED, ARCHIVED`.
- **EXISTS** — Applications page (`applications.js`) shows it all: status labels map
  (`APPROVED_FOR_APPLICATION` → "Approved for application · You approved this package.
  Apply on the employer site to send it."), approve/reject buttons (confirm dialog first),
  deep-link to the employer site once approved (`applications.js:240`).
- **12.7 DELTA** — prepare-review modal links to Applications ("Review in Applications")
  but has **no direct Approve action**. Keep approval **centralized on the Applications
  page** (single source of truth), and make the prepared-review modal's link explicit:
  "Nothing has been submitted. Approving the package in Applications is required before
  any email can go out." (Matches the safety rule "package approval ≠ employer
  submission".) Do NOT add a second approve button that could drift from the page.
- **EXISTS** — edit is on the Applications page (`PUT /api/v1/applications/{id}` —
  `ApplicationUpdates{coverLetter, professionalSummary, applicationAnswers}`, edit modal
  at `applications.js`). **12.7 DELTA** (lazy): add one "Edit" action in the prepare-review
  modal that deep-links to the Applications page edit for that `applicationId` — do not
  build a second editing surface. If the edit-in-place is wanted later, it is a follow-up,
  not part of 12.7.

### 5. Email — recipient verification (12.7 DELTA, the real gap)

Current state (verified): `ApplicationEmailController` at
`/api/v1/applications/email/send` takes only `{applicationId, approved}`; it **requires**
the stored status to be `APPROVED_FOR_APPLICATION` **and** `approved == true`; it builds
the `ApplicationEmailDraft` server-side with a **placeholder recipient** (`hiring@company.com`,
`"Hiring Manager"`) and companion warnings; `ApplicationEmailService.send(draft, approved)`
guards on `approved`, validates a syntactically valid recipient, falls back to a simulated
`SENT` when no `EmailTools` is wired, and **never sends without explicit approval**
(`ApplicationSendResult`: `SENT | REJECTED | FAILED`).

Safety rule requires a **user-entered, user-verified recipient** — never a guessed address.
Deltas:

1. Accept an optional `recipientEmail` on the email/send body (extend the existing DTO;
   keep `approved` mandatory).
   - When provided and syntactically valid → override the placeholder, drop the
     placeholder warning, draft status `READY_TO_SEND`.
   - When absent → draft stays `REVIEW_REQUIRED` with the existing warning; the service
     **must not** substitute the placeholder as a real address for any send path.
2. Applications page send-email modal gains a required recipient field, pre-filled from the
   candidate profile email when available but always editable — the user confirms the
   address before `POST`. Pre-filled value is a convenience only; an invalid/blank value
   blocks send with an inline message.
3. No change to the approval gate: email/send without a stored `APPROVED_FOR_APPLICATION`
   and `approved == true` still returns `REJECTED`.
4. The send result is surfaced verbatim (`SENT | REJECTED | FAILED`) with **no** fake
   success when no mail transport is configured (simulated `SENT` is labelled as such in
   the UI, e.g. "simulated — no SMTP configured", because `tools.email.from` may be unset
   and only a stub `EmailTools` exists).

### 6. HTTP API — delta list

| Endpoint | Exists? | Phase 12.7 |
|---|---|---|
| `POST /api/v1/applications/prepare` | EXISTS (returns `ApplicationPreparationResult`) | unchanged |
| `GET /api/v1/applications/{id}` | EXISTS | unchanged |
| `GET /api/v1/applications/candidate/{candidateId}` | EXISTS | unchanged |
| `PUT /api/v1/applications/{id}` | EXISTS | unchanged |
| `POST /api/v1/applications/{id}/approve` | EXISTS | unchanged |
| `POST /api/v1/applications/{id}/reject` | EXISTS | unchanged |
| `POST /api/v1/applications/email/send` | EXISTS | DTO gains optional `recipientEmail`; service resolves placeholder/review-state per §5 |

Errors unchanged (RFC 7807 `GlobalExceptionHandler`): 400 invalid/`IllegalArgumentException`,
404 candidate/application, 500 safe-generic. No new endpoints needed for 12.7.

### 7. Tests

Existing gates (must stay green — **do not weaken**; `mvn clean test` hermetic, no
`@SpringBootTest`):

- `orchestrator/application/JobApplicationPreparationServiceTest` ✓
- `orchestrator/application/ApplicationStorageServiceTest` ✓
- `orchestrator/application/JobApplicationControllerTest` ✓
- `orchestrator/application/JobApplicationLongAnswersPersistenceTest` ✓
- `orchestrator/application/ApplicationEmailServiceTest` ✓
- `ui/controller/ApplicationEmailControllerTest` ✓

New tests to add (**12.7 DELTA only — no other new suites**):

1. `ApplicationEmailControllerTest` extensions (the delta is backend):
   - email/send with valid `recipientEmail` + stored `APPROVED_FOR_APPLICATION` + `approved=true`
     → `SENT`, and draft has no placeholder warning (mocked service asserted with the real
     address).
   - email/send with `recipientEmail` present but syntactically invalid → **400** and the
     service never invoked.
   - email/send without `approved` / without stored approved status → `REJECTED` (existing
     behavior re-asserted, unchanged).
2. If `ApplicationEmailDraft`/service refusal logic changes, extend `ApplicationEmailServiceTest`
   minimally for the placeholder rule (§5.1 second bullet).

The prepared-review modal rendering deltas (§3) are UI; verification is the §8 browser pass,
not JUnit (no JS test framework exists in this repo — do not introduce one).

### 8. Browser checkpoints

A. Prepare button visible on a live match card + match details footer ·
B. `POST /api/v1/applications/prepare` returns 200 and an `applicationId` ·
C. prepared-review modal in the global `modalShell` renders company/skills/summary/CV +
   **new** Q&A, strengths, recommendation sections ·
D. empty skills lists render as "none found" (no raw emptiness) ·
E. approve + edit actions on Applications page open cleanly from the prepare modal "Review in
   Applications" → deep link ·
F. email/send modal requires a recipient; blank/invalid blocks with inline message ·
G. unapproved application → email/send shows `REJECTED` result ·
H. approved application + valid recipient → `SENT` unit result shown (simulated when no SMTP) ·
I. close/reopen clean ·
J. repeated opens (3×) leave no stale body/footer/duplicate listeners ·
K. 1440px · L. 768px · M. 390px — no horizontal overflow *inside the modal* ·
N. console errors = 0 (happy path) · O. failed network requests = 0 ·
P. no auto-submit / no auto-email fired without approval.

### 9. Gates (unchanged, not claimed resolved here)

- **D2** — page-level horizontal overflow at ≤768px (`scrollWidth` > client) is **deferred
  to Phase 12.10** (full responsive E2E). 12.7 only verifies the modal itself does not
  overflow (M). Do NOT claim D2 resolved in this phase.
- **Live job-source reachability** — PRD Known Gaps: "Live job source reachability |
  UNVERIFIED | Sandbox has no outbound network". Remains **UNVERIFIED**; not claimed, not
  touched by 12.7.
- **Phase 12.5 verification not recorded** — see roadmap docs; do not promote.

### 10. Safety requirements (unchanged, enforced)

Never invent candidate data, cover-letter claims, skills, employers, or years; Q&A is
suggested and user-editable, never auto-submitted; no auto-email without explicit approval;
approval ≠ employer submission (Approving only enables the email send, it does not apply);
simulated send is never disguised as a real send; the Gemini key is never logged/exposed and
never returned from an endpoint; email recipient is user-entered/verified, never a guessed
address. `RULES.md` / `PRD.md` hold.

### 11. Non-goals

No new apply/autofill-to-employer-site engine; no auto-submission; no second modal
architecture; no new frontend framework or JS test framework; no LLM-provider change; no
job-search/match-score change; no new storage schema (`JobApplication` exists, `ddl-auto:
update`); no email transport provisioning (stub `EmailTools` remains the send path); no D2
fix; no resume-tailoring change (Phase 12.6 frozen/verified).

### 12. Commits (EXECUTED — Slices 1–3; docs sync applied, not yet committed)

The phase was delivered as three implementation slices (not the two-commit split originally
proposed; §4/§5 deltas were grouped by user-visible slice instead):

1. **Slice 1 — prepared-review completeness** (`9009aae`): `matches.js` renders
   `suggestedAnswers`/`candidateStrengths`/`recommendation`+`matchScore` in the prepared-review
   modal; explicit approval-gate footer copy; "Review in Applications" / "Edit in Applications"
   deep links.
2. **Slice 2 — editing flow** (`351283e`): edit deep link (`?edit=1`) + edit-eligible status
   behavior, inline validation, saving state, double-submit protection, server reload after
   save, safe cancel, editing separate from approval. **Verification**: 999 tests passed, no
   failures/errors/skips; browser checks passed.
3. **Slice 3 — recipient verification + safe sending** (`9275582`): backend (DTO
   `recipientEmail`, `ApplicationEmailService.isValidRecipient`, `SENT_SIMULATED`, status-string
   result mapping) + `confirmDialog` input support + `applications.js` recipient field and
   simulated/REJECTED/FAILED labelling + tests. **Verification**: 1,002 tests passed, no
   failures/errors/skips; controlled browser checks passed (route-intercepted mock email
   transport; no real email sent).
4. **Docs sync (planned `Phase 12.7 - sync docs` commit)**: `TASKS.md`, `DESIGN.md`,
   `ARCHITECTURE.md`, `PRD.md`, `MEMORY.md`, `RULES.md`, and this section updated — **applied
   but not yet committed** (awaits review).

Remaining planned work (NOT executed): full §8 checkpoint pass A–P as a committed browser
acceptance run, and the Phase 12.10 E2E gate (D2 page-level overflow, live job-source
reachability). Phase 12.7 is not marked complete until final acceptance criteria are verified.

### 13. Architectural risk (low, acknowledged)

- The recipient delta widens an existing DTO but is additive and optional; the approval
  gate and `REJECTED` semantics are unchanged, so no compatibility hazard for existing
  `ApplicationEmailControllerTest` cases.
- Simulated send without SMTP is already the runtime reality (`MailConfig`/`EmailTools`
  stub); the spec only makes that honesty visible in the UI.
- Section rendering in the prepare modal is additive (each section optional); the
  `data.analysis ?? data`-style tolerance pattern from 12.6 is reused for absent fields.

---

## Resolved issues

1. **"Prepared Application modal wrapper missing"** — Resolved by inspection: legacy
   `prepReviewOverlay`/`advisorReviewJobTitle`/`advisorReviewCompany` wrappers no longer
   exist; the UI renders through the single global `modalShell` (`matches.js:667`),
   `ApplicationAdvisorResponse` already carries `jobTitle`/`company`, and docs referencing
   the old DOM are stale (roadmap docs corrected in the reconciliation pass; **code is
   untouched**).
2. **"Editable Application Package"** — Resolved (lazy): editing already exists via
   `PUT /api/v1/applications/{id}` on the Applications page. 12.7 deep-links to it; a
   separate inline edit surface is explicitly out of scope.
3. **Email recipient placeholder** — Resolved as §5: additive optional `recipientEmail`
   with mandatory user verification; placeholder never becomes a real send address.
4. **Missing review sections** — Resolved: the modal renders every
   `ApplicationPreparationResult` field it currently omits (Q&A, strengths,
   recommendation, matchScore), each optional and non-empty-gated.
5. **Approve placement** — Resolved: approval stays centralized on the Applications page
   (existing endpoint + confirm dialog + status labels); the prepare modal only links, so
   the single source of truth cannot drift.