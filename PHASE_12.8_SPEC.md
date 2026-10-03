# PHASE 12.8 SPEC STATUS: COMPLETE AND VERIFIED — Apply Kit implemented and accepted (Slices 1–4, 2026-09-30)

## FULL SPECIFICATION — Phase 12.8 Employer Application (Assisted Apply)

### 1. Goal

Turn the existing **manual** "Apply on Employer Site" link into a **user-triggered, user-confirmed
assisted-apply experience**: from an approved application package, the platform prepares a
reviewable set of candidate values for a small whitelist of common employer-form fields, lets the
user review and edit every value, and transfers it to the employer form in the user's own browser
(the user performs the final paste and submit). The platform never writes to or submits a page it
does not serve, never interacts with CAPTCHAs/MFA, and never handles credentials.

Phase 12.8 delivers **assisted preparation and transfer only**. The employer form's DOM is never
automated, which keeps the phase inside the PRD "no automatic form filling / no automatic
submission" boundary while delivering the roadmap's assisted-fill intent.

The spec is grounded exclusively in existing code. Where a capability already exists it is marked
**EXISTS** and is reused, not rebuilt; where behavior is missing it is declared a **12.8 DELTA**.

### 2. Existing state (confirmed by inspection — EXISTS)

| Item | Evidence | Meaning for 12.8 |
|---|---|---|
| `jobLink.js` is the single source of truth for apply/listing URLs | `resolve()` + `actionHtml()` (jobLink.js:72-164) | Both the manual link and the kit MUST reuse it; labels/destinations cannot drift |
| `Job.applicationUrl` is nullable and never fabricated | `Job` record (Job.java:28-47); providers + `JobUrlValidatorTest`, `ArbeitnowJobSourceProviderTest`, `AdzunaJobSourceProviderTest`, `OpeningsMcpJobSourceProviderTest` | Only when a source supplies a real destination is "Apply on Employer Site" offered |
| `safeUrl` refuses loopback/private/fake hosts | jobLink.js:43-63 (`localhost`, RFC 1918, `example.`/`.test`/`mockjobs`) | The kit can only ever surface a validated, real-looking public https destination |
| Mock catalog jobs have NO external destination | jobLink.js:78-80 (`isMockJob` → "Application Link Unavailable") | Acceptance needs a fixture job, not the default mock catalog |
| `JobApplication` carries `jobId` | JobApplication.java:23 | The Applications detail can resolve the listing → `applicationUrl` via existing `GET /api/v1/jobs/{id}` (JobDetailsController) |
| Approved package + approval gate exist | `POST /api/v1/applications/{id}/approve` → `APPROVED_FOR_APPLICATION`; Applications page detail (applications.js) | Eligibility anchor for the kit, mirroring the email gate |
| Package content (review/edit/email) verified | Phase 12.7 VERIFIED (51/51 browser checks, 1,216 tests) | `tailoredProfessionalSummary`, `coverLetter`, `matchingSkills`, `missingSkills`, `candidateStrengths`, `resumeHighlights` all available on `GET /api/v1/applications/{id}` |
| `CandidateProfile` fields | CandidateProfile.java:12-45 — `name, email, phone, location, education, skills, experience, internships, projects, certifications, softwareSkills, hardwareSkills, preferredRoles, preferredLocations, resumeEvidence, careerTrackEvidence` | Whitelist data sources; see §4 |
| **No** `headline` field in the profile | CandidateProfile.java (record) | Headline has no direct stored source (§4 DELTA) |
| **No** GitHub/LinkedIn URL fields | CandidateProfile.java + resume parser template (ResumeProfileService.java:24-46) | "Links" have **no trusted source** → initially unsupported (§4 DELTA) |
| **No front-end candidate-profile read endpoint** | `ui/controller/*`: no `@GetMapping` returns `CandidateProfile` (only jobs/{id}, ai/status, custom/status) | The kit needs one small read-only endpoint (§6 DELTA, grounded by inspection) |
| Email send gate | `ApplicationEmailController` — stored `APPROVED_FOR_APPLICATION` **and** `approved=true`, else 400/REJECTED; simulated sends labelled | Template for the kit's eligibility gate and for NO-auto-action behaviour |
| No JS test framework; browser acceptance via committed harness | RULES.md §6; Phase 12.6/12.7 acceptance runs | 12.8 UI is verified by browser acceptance checkpoints, not JUnit |
| Sandbox has no outbound network | PRD Known Gaps / 12.7 spec §9 | Fixture-based acceptance only; real employer sites are never automated |

### 3. Policy boundary (scope resolution)

The roadmap (TASKS.md "Phase 12.8 — Employer Application") calls for "browser automation for form
filling (supported fields only)" + "user review before submit". PRD §4 excludes "automatic form
filling on employer sites" and "automatic application submission (CAPTCHA/MFA bypass)". Per
RULES.md §9 precedence (RULES → PRD → ARCHITECTURE → DESIGN → TASKS → MEMORY) the PRD governs.

**Resolved scope — user-triggered, assisted form preparation:**

- The user explicitly starts the assisted flow from an approved application package.
- The platform prepares values only for the §4 whitelist, all reviewable and editable in the kit.
- Values are transferred to the employer form **by the user** (per-field copy/read-aloud; the user
  pastes). The user, and only the user, performs the final submit on the employer site.
- The platform never writes to the DOM of a page it does not serve, never auto-submits, never
  touches CAPTCHA/MFA, never stores or exposes credentials.
- Unsupported forms are handled safely and by construction: the kit is form-agnostic; the employer
  page is never auto-touched, so there is no path for a stray write or a hidden-field mutation.

A second reason it is a **12.8 DELTA non-goal** to drive employer pages directly: the application is
static vanilla JS serving its own origin; cross-origin DOM access from this app to an employer page
is not technically available (and would require an extension or embedded browser — out of scope).

**PRD wording clarification (narrow, applied to PRD §4):** the existing exclusion bullets are
reworded to name **unattended** automation as out of scope, and a note is added stating that
user-triggered, user-confirmed assisted preparation (Phase 12.8) remains inside the approved
boundary. Nothing else in PRD changes.

### 4. Supported-field whitelist (12.8 DELTA — the kit's data contract)

For each field: trusted source, allowed input type, mapping rule, validation, and behaviour when
data is absent or ambiguous. Values always come from one of two approved sources — the stored
`CandidateProfile` and the stored approved `JobApplication` — never from free text, never guessed.

| Field | Trusted source | Type | Mapping / validation | Absent or ambiguous |
|---|---|---|---|---|
| **Name** | `CandidateProfile.name` | text | verbatim; non-empty string; length ≤ 200 | omit the field (do not guess); user may type it themselves |
| **Email** | `CandidateProfile.email` | text (email) | verbatim; must match the same validation family as `ApplicationEmailService.isValidRecipient`; lowercased for display | omit + inline warning; never substitute a placeholder address |
| **Phone** | `CandidateProfile.phone` | text (tel) | verbatim as parsed (no reformatting) | omit; user may fill by hand |
| **Location** | `CandidateProfile.location`; if blank, `preferredLocations[0]` | text | verbatim, single line | both blank → omit |
| **Headline** **(DELTA)** | not stored — derived verbatim from `CandidateProfile.preferredRoles`, joined `", "` | text | roles copied as-is (they are resume-evidence-derived); length ≤ 200 | no roles → omit; never invent a job title |
| **Professional summary** | `JobApplication.generatedResumeSummary` (the package's `tailoredProfessionalSummary` at prepare time) | textarea | verbatim; length ≤ 4,000 | blank/stale → kit warns "package has no summary — regenerate or edit in Applications", field omitted |
| **Skills** | `CandidateProfile.skills` (canonical, whole-resume) | text | comma-joined, de-duplicated, stable order; ≤ 2,000 chars (truncate the tail with a visible notice) | empty → omit; job-specific `matchingSkills` not auto-inserted (keeps the fill generic) |
| **Links** **(DELTA — initially unsupported)** | **no trusted source** (inspection: the parse pipeline stores no GitHub/LinkedIn URLs) | — | — | field not offered; documented gap; enable in a later phase only if resume URL extraction is added |

Rules that apply to every field:

- Do not populate unknown, hidden, unsupported, or sensitive fields (e.g. passwords, CVV, SSN).
- Do not invent answers for employer-specific questions (e.g. "salary expectation", "referral
  code") — those are never in the whitelist.
- Safe decline: if a required field's value cannot be matched confidently to an approved source,
  the field is simply not offered; the kit never writes a best-guess.
- Every value is shown with its source label (e.g. "from your resume profile" vs "from your
  prepared application") so the user can judge provenance.

### 5. End-to-end workflow (12.8 DELTA except the underscored EXISTS steps)

1. **Manual apply link (EXISTS)** — everywhere `jobLink.actionHtml` renders, "Apply on Employer
   Site" keeps working exactly as today: validated employer URL, new tab, labelled honestly.
2. **Assisted-apply entry point (DELTA)** — on the Applications page detail of an
   `APPROVED_FOR_APPLICATION` package, a distinct "Assisted Apply" control (secondary action next
   to the existing external link) opens the Apply Kit in the shared `modalShell`.
3. **Employer URL provenance (EXISTS + DELTA guard)** — the kit resolves the job from the
   package's `jobId` via `GET /api/v1/jobs/{id}` and passes it through `jobLink.resolve()`; if
   `kind !== 'employer'`, the kit is disabled with the reason from `jobLink` (mock or listing-only
   jobs, or unavailable link) and the manual "View Job Listing" fallback is shown. The URL is never
   constructed, guessed, or fabricated.
4. **Eligibility checks (DELTA)** — at kit open: application id resolves (else 404-style error +
   recovery), `applicationStatus === APPROVED_FOR_APPLICATION` (else kit blocks with the current
   status and a pointer to Approval in Applications), candidate profile resolves (else error), job
   resolves to an employer destination.
5. **Explain the whitelist (DELTA)** — the kit shows exactly which fields it is preparing, which
   of them are coming from the profile, and which from the package; unsupported/unmatched fields
   are shown as "not pre-filled — fill manually if needed".
6. **Fill (prepare) + review + edit (DELTA)** — every prepared value is editable in the review
   list before use; edits live only in the current kit session.
7. **Transfer (DELTA, user-driven)** — the kit opens the employer page in a new tab (same
   validated URL as the manual link); for each field a one-click **copy** control copies the
   (possibly edited) value to the clipboard with a "copied" confirmation; the user pastes each
   field into the employer form. Nothing is written into the employer page by the app.
8. **Final submission (user only)** — the user submits on the employer site. No automatic
   submit, ever.
9. **Safe exit / cancellation / unsupported (DELTA)** — closing the modal or "Cancel" discards the
   edited kit values; returning keeps the manual link as the always-available fallback; a
   "Reset kit values" action restores the server-side values. Because the employer page is never
   auto-touched, "unsupported form" needs no detection — there is no risk path.

### 6. Architecture & implementation slices

Grounding: plain HTML/CSS/Vanilla JS (IIFE) on the static frontend, Spring Boot backend for the one
read-only endpoint. No new frontend framework, no new modal architecture (reuse `modalShell`), no
email changes, no status-transition changes.

**Slice 1 — Apply review surface + eligibility.** Add the "Assisted Apply" control to the
Applications detail for `APPROVED_FOR_APPLICATION` packages and the Apply Kit overlay in the shared
`modalShell`: employer URL provenance via `jobLink` (resolved from package `jobId` -> `GET
/api/v1/jobs/{id}`), eligibility checks, whitelist explanation, safe decline when the package is
not approved / the job has no employer destination / is a mock listing.
Changes: `applications.js`, `modalShell` reuse, `style.css` (kit styles), no backend.
Acceptance: eligibility matrix (approved/not-approved/listing-only/mock/404) renders the kit or a
clear reason; manual apply link is byte-for-byte unchanged.
Tests: browser acceptance only. Dependencies: 12.7 Applications page + `jobLink`.

**Slice 2 — Whitelist values + candidate read endpoint.** Add the one read-only backend endpoint
`GET /api/v1/candidate/{candidateId}` returning the `CandidateProfile` (RFC 7807 404 when missing,
safe generic 500; no secrets; slice-tested) — justified by inspection: no front-end candidate-read
path exists. Build the §4 mapping in `applyKit.js` (name/email/phone/location/headline/summary/
skills), per-field source labels, inline edits, per-field copy controls (clipboard API with
fallback), "Reset kit values", absence handling, and safe decline for unsupported/unmatched
fields. Mock jobs and listing-only jobs show the reason + manual fallback (no kit).
Changes: `applyKit.js` (new), `applications.js`, `ui` controller + one DTO, `JobApplicationPrep`
nothing; tests: `@WebMvcTest` for the new endpoint, browser acceptance for the mapping.
Acceptance: every supported field with valid profile data maps correctly and copies the
server-derived (or user-edited) value; blank profile fields omit the field with a warning; no
best-guess ever produced. Dependencies: Slice 1; `CandidateProfilePersistenceService`.

**Slice 3 — Final review + manual submission + minimal recording.** Final review interstitial
before the user leaves the kit to the employer page ("values above will not be written or
submitted by this app — YOU submit on the employer site"); explicit "Copy all fields" primary
action; explicit user acknowledgement. Optionally write a **client-side only** marker under
`agentplatform:applyKit:<applicationId>` (opened-at timestamp, job URL, edited values snapshot)
purely for UX continuity; it is NOT a status change and NOT a backend write, and it is explicitly
the seam Phase 12.9 will migrate into real tracking. Re-validate the application status when the
user returns to the kit (stale package → block with a "package changed" message and a reload).
Changes: `applyKit.js`, `applications.js`. No backend.
Acceptance: no automation path exists (P-style check below); a stale package is detected and
blocked; the marker is only written client-side.
Tests: browser acceptance only. Dependencies: Slices 1–2.

**Slice 2 security disposition (bounded).** `GET /api/v1/candidate/{candidateId}` is
unauthenticated and has no per-candidate ownership authorization; candidate IDs are sequential and
enumerable. The endpoint is intended only for the trusted, single-user, local-first prototype:
`server.address: 127.0.0.1` in `application.yml` restricts remote/LAN access but does not isolate
local processes or operating-system users. Networked or multi-user deployment is blocked pending a
proper identity, ownership, and transport-security design. No partial authentication, fake
ownership checks, ID obscurity, or rate-limit workarounds are added.

**Slice 4 — Documentation sync + phase-level acceptance.** Update TASKS/PRD-principle docs if
needed (only what changes), run the full §7 acceptance checklist, full `mvn clean test` (hermetic),
record evidence in the roadmap docs, commit, and stop for review. Phase 12.8 is marked VERIFIED
only after this acceptance evidence exists — writing this spec does not make the phase complete.

### 7. Test & browser acceptance (A–P style, fixture-based)

**Local employer-form fixture.** The sandbox has no outbound network and real employer sites must
never be automated. Because 12.8 never writes to the employer page anyway, acceptance drives the
**Apply Kit in this app's own origin**; the fixture consists of (a) a route-intercepted fixture job
whose `applicationUrl` is a valid public-style https URL passing `jobLink.safeUrl` (reusing the
harness's route-interception pattern from the 12.7 email acceptance) and (b) a minimal local
`sandbox/employer-form.html` render of the same fields, used only to let the user/acceptance verify
a copy→paste round-trip deterministically. Mock-catalog jobs (`kind 'none'`) exercise the decline
path. Live job-source reachability is NOT claimed by any of this.

Checklist (letters kept from the 12.6/12.7 convention):

- A. Manual "Apply on Employer Site" link on Jobs, Matches, Match Details, Applications renders and
      opens identically to today (regression vs `jobLink.actionHtml`).
- B. "Assisted Apply" control appears for an `APPROVED_FOR_APPLICATION` package, and only there.
- C. Kit opens in the shared `modalShell` with the resolved employer URL + its provenance (source
      name) and no fabricated segments.
- D. Mock / listing-only / missing job → kit disabled with the exact `jobLink` reason + manual
      fallback.
- E. Approved→used after approval only; unapproved/REJECTED/ARCHIVED package → kit blocks with
      status + "Approve in Applications" pointer.
- F. Supported fields (name/email/phone/location/headline/summary/skills) populate from the
      approved sources with correct source labels.
- G. Blank/absent profile fields → field omitted with "not pre-filled" note; no placeholder, no
      guess (values ≥ 255 chars re-verified via the 12.7 H2 regression path).
- H. User edit of any value in the kit survives to the copy control; "Reset kit values" restores
      server values.
- I. Copy per field returns the edited value to the clipboard and shows confirmation (fixture
      page paste round-trip).
- J. Three open/close cycles → no stale body/footer/duplicate listeners (same convention as 12.6).
- K. 1440px · L. 768px · M. 390px — kit has no horizontal overflow *inside the modal*.
- N. Console errors = 0 (happy path) · O. failed network requests = 0.
- P. No automated submit and no automated write to any page outside this app; user acknowledgement
      is required before the kit hands off to the employer tab; no auto-email.
- Q. Credential handling = none: no storage, no exposure, no password/CVV/SSN fields offered.

**Accepted — Slice 4 acceptance (2026-09-30, VERIFIED):** full `mvn clean test` — **1,227 tests,
0 failures, 0 errors, 15 skipped**; browser acceptance — checkpoints **A–Q, 67/67 checks passed**
(two consecutive green runs; P/Q cross-checks: no auto-submit, no network to the employer origin,
no credential fields offered). The kit's job and the advisor-path job received route-intercepted
fixture listings (`applicationUrl` passing `jobLink.safeUrl` so the kit enables; a second
`REMOTIVE` listing for the prepared-application second app) and the advisor POST on that second app
was fulfilled with a canned valid `ApplicationAdvisorResponse` — the Application Advisor is a live
backend feature the kit flow reuses, not part of 12.8 acceptance, so its calls are stubbed in the
hermetic run. Mock/listing-only jobs drove the decline checks (D). The local
`sandbox/employer-form.html` style fixture page was used for the deterministic copy→paste round-trip
(I).

### 8. Dependencies and deferred gates

- **Prerequisite (EXISTS):** Phase 12.7 approved application package + stored `CandidateProfile`
  (both verified). The package and the profile are the only candidate-data sources (§4/§5).
- **Reused (EXISTS):** `Job.applicationUrl` null-safety + `jobLink.js` resolution, the Applications
  approval gate, `GET /api/v1/jobs/{id}` for URL provenance, the shared `modalShell`, and the
  12.6/12.7 browser-acceptance harness.
- **Backend delta (grounded):** one read-only `GET /api/v1/candidate/{candidateId}` — no other new
  endpoints are introduced.
- **D2 page-level mobile overflow (≤768px):** stays a **Phase 12.10** item; it does not block 12.8,
  but the modal-level fit constraint (K–M) still applies to the kit.
- **Live employer-site / job-source reachability:** remains **UNVERIFIED**; the local fixture does
  not prove external reachability, and its Phase 12.10 gate is retained. Note added from the 12.8
  acceptance run (a networked workstation, unlike the offline sandbox): live Arbeitnow (and REMOTIVE
  through the advisor path) did serve real listings, so network reachability is real, but single-job
  lookups proved flaky — the advisor's per-request live `JobSearchService.findById` intermittently
  threw `JobNotFoundException` (reproduced: 30 identical advisor POSTs → 14×200 / 16×404) even
  though the same `GET /api/v1/jobs/{id}` resolved seconds later. That is a backend reliability
  finding to pick up at 12.10, not a kit defect. Acceptance remains fixture-based, and real employer
  sites are never automated.
- **Phase 12.5 verification:** remains outstanding and is not a 12.8 dependency; it is re-verified
  by the 12.10 full E2E regression.
- **Phase 12.9 (Application Tracking):** the client-side apply marker (Slice 3) is its migration
  seam; 12.8 does not implement tracking.

### 9. Non-goals

No automation of employer-page DOM; no auto-submit or unattended filling; no CAPTCHA/MFA
interaction; no credential handling; no fabricated URLs, qualifications, links, or answers; no new
frontend framework or JS test framework; no new status transitions (12.9 owns tracking); no SMTP /
email change; no D2 fix; no job-search/match-scoring change; no storage of external form contents.

### 10. Commits (executed)

- `d60f556` — Phase 12.8 - define assisted employer application workflow (spec)
- `c758dca` — Phase 12.8 - add assisted apply review surface and eligibility (Slice 1)
- `8718d5c` — Phase 12.8 - add candidate Apply Kit data with loopback binding (Slice 2, `GET
  /api/v1/candidate/{candidateId}` + kit whitelist mapping)
- `8b2e966` — Phase 12.8 - add Apply Kit final review and manual handoff (Slice 3: final-review
  interstitial, copy-all, acknowledgement, client-side marker, stale-package re-validation)

All four are committed and re-verified in HEAD. Slice 4 (docs sync + acceptance evidence, this
document) is in the working tree. Acceptance evidence (2026-09-30): full `mvn clean test` →
**1,227 tests, 0 failures, 0 errors, 15 skipped**; browser acceptance A–Q → **67/67 checks passed**
(two consecutive green runs). Phase 12.8 is **VERIFIED** — this evidence exists and is recorded
here; see TASKS.md / MEMORY.md for the per-slice history.