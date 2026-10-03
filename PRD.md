# Product Requirements Document — agent-platform

> **Status**: CURRENT — reflects actual implemented product as of commit 00079a9 (Phase 12.10 COMPLETE AND VERIFIED; Cleanup Batches 1–3 complete)
> **Phase 11.1 frozen**: 0d141d6 | **Phase 12.1 verified**: 553eb76 | **Phase 12.2 prep**: f47562b | **Phase 12.3 verified**: a6eaf5c | **Phase 12.4 verified**: da933ac | **Phase 12.5 implemented (verification not recorded)**: 6f4b483 | **Phase 12.6 verified**: 92c0942, 055db64, bc457e2, 091f7c3, 0a02e22 | **Phase 12.7 verified**: 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | **Phase 12.8 verified**: d60f556, c758dca, 8718d5c, 8b2e966 | **Phase 12.9 verified**: f21b09c, 45afa38, 28d49aa, 95da0e9 | **Phase 12.10 verified**: 1ba94cc
> **Cleanup Batch 1**: 7fb6e18 | **Batch 2**: 620749c | **Batch 3**: 00079a9
> **Current local model**: llama3.2:3b (Ollama)

---

## 1. Product Vision

agent-platform is a local-first AI Career Agent platform that helps users turn a resume into actionable job matches, career analysis, and application materials — all running locally with Ollama LLMs. Cloud Gemini exists in code but is disabled by default and unconfigured, so it is a deferred option rather than a live fallback for resume parsing.

**Core principle**: The uploaded resume is the single source of truth. Nothing is invented; everything displayed derives from evidence in the resume or official job listing URLs.

---

## 2. User Journey (Implemented vs Planned)

| Step | Feature | Status | Notes |
|------|---------|--------|-------|
| 1 | **Resume Upload** (PDF/DOCX) → text extraction → LLM parsing → structured `CandidateProfile` | **IMPLEMENTED** | PDF/DOCX via PDFBox/POI; LLM parsing with deterministic fallback |
| 2 | **Profile Display** — structured candidate profile in browser | **IMPLEMENTED** | Profile page renders parsed data; some UI bugs exist (see §3) |
| 3 | **Job Search** — profile-driven search (skills/location/experience/employmentType) | **IMPLEMENTED** | Profile-driven search; free-text keyword field removed (Phase 12.3) |
| 4 | **Job Matches** — deterministic scoring, explainable results | **IMPLEMENTED** | Weighted scoring (skills/role/experience/track/location/education) |
| 4b | **Job Details** — official listing URL, source attribution | **IMPLEMENTED** | Job modal shows source URL, never fabricates employer URLs |
| 5 | **Career Analysis** — readiness score, gaps, recommendations | **IMPLEMENTED** | Backend works; renders via the shared modal shell (legacy DOM wrappers removed); Phase 12.5 UI-polish browser verification not recorded |
| 6 | **Application Readiness** — readiness score, gaps, recommended actions | **IMPLEMENTED** | Backend works; renders via the shared modal shell (advisor breakdown) |
| 7 | **ATS Resume Tailoring** — reorder/emphasize existing content only | **IMPLEMENTED** | Deterministic; preview modal + PDF/DOCX download; never invents skills/experience; core workflow + PDF/DOCX read-back verified (D2 page-level overflow at ≤768px deferred) |
| 8 | **Application Preparation** — tailored resume + cover letter + Q&A | **IMPLEMENTED and VERIFIED** | Prepare endpoint, profile-derived package generation, prepared-review modal (Q&A/strengths/recommendation), Applications-page editing, and email send with a user-entered/confirmed recipient are implemented (Slices 1–3) and verified: full suite 1,216 tests green, browser acceptance A–P 51/51 (2026-09-29); H2 long-text `TEXT` persistence fix + regression test + two `applications.js` regression fixes (deep-link detail-visibility race, missing detail-view approve handler) in commit 6a9f647. No SMTP is configured — email sends are simulated and labelled as such; the approval gate is unchanged |
| 9 | **Employer Application** — manual via official URL only | **IMPLEMENTED and VERIFIED** | "Apply on Employer Site" / "View Job Listing" buttons; no auto-submit. Phase 12.8 adds the user-triggered **Apply Kit** (review + copy per field, user performs the final paste and submit) for whitelisted fields (name/email/phone/location/headline/summary/skills) from the approved package + stored profile; verified 2026-09-30 — 1,227 tests green, browser acceptance A–Q 67/67. The platform never writes to or submits a page it does not serve |
| 10 | **Application Tracking** | **IMPLEMENTED and VERIFIED** | Applications page with status-filtered list, per-status badges and counts; server-enforced idempotent status transitions (`approve`, `reject`), approval-gated email send, and employer-site handoff recording; detail view gains an event timeline built only from persisted fields plus per-status action visibility. Verified 2026-09-30 — full suite 1,278 tests green, browser acceptance 10/10 API + 10/10 UI. The platform never auto-submits, never sends email unattended, never renders a timeline event it cannot observe, and never calls transport acceptance a delivery |
| 11 | **Final E2E + MCP + handoff verification** | **IMPLEMENTED and VERIFIED** | Phase 12.10 (`1ba94cc`): D2 page-level overflow resolved; live job-source reachability confirmed against ARBEITNOW/REMOTIVE/OPENINGS-MCP (`live: true`); employer-site handoff recorded server-side as an opening only, never a submission |

---

## 3. Core Requirements (Non-Negotiable)

| Requirement | Status | Details |
|-------------|--------|---------|
| **Resume = Source of Truth** | ✅ Enforced | All profile data derives from resume; nothing invented |
| **Evidence-Based Matching** | ✅ Implemented | Skills/role/experience/track/location/education weighted; evidence shown |
| **Real Job Listings Only** | ✅ Enforced | Live sources are the default: Remotive, Arbeitnow, Adzuna and OPENINGS-MCP are enabled in `application.yml`; `MockJobSource` is `enabled: false` and is only used when it is the sole wired source. Reachability confirmed in Phase 12.10 (`live: true` from ARBEITNOW/REMOTIVE/OPENINGS-MCP) |
| **Manual/User-Authorized Application** | ✅ Enforced | No auto-submit; explicit "Approve" step; "Apply on Employer Site" opens external URL |
| **No Fabricated Qualifications** | ✅ Enforced | Tailoring reorders existing content; never invents skills/experience |
| **Evidence-Based Career Tracks** | ✅ Enforced | Tracks inferred from resume evidence; UI over-inference fixed in 12.1 |
| **Explicit Keyword Behavior** | ✅ Enforced | Free-text keyword field removed (Phase 12.3); keywords derive from profile skills/roles |
| **Official/Source URLs Only** | ✅ Enforced | Job modal shows "View Job Listing" (source) vs "Apply on Employer Site"; never fabricated |
| **No Auto-Submit** | ✅ Enforced | Explicit "Approve for Application" step required |

---

## 4. Known Gaps / Planned / Blocked

| Item | Status | Details |
|------|--------|---------|
| Job Search: remove free-text keyword field | ✅ Done (12.3) | Removed; keywords derive from profile skills/roles |
| Live job sources enabled by default | ✅ Done | Remotive, Arbeitnow, Adzuna and OPENINGS-MCP are `enabled: true`; mock is `enabled: false`. The UI banner is live-first ("Live Job Source Active" / "Live Source Returned No Listings") |
| Career Analysis modal DOM fixes | **SUPERSEDED** | Legacy `advisorReviewJobTitle`/`advisorReviewCompany` wrappers removed; Career Analysis renders via the shared modal shell |
| Prepared Application modal wrapper | **SUPERSEDED** | Obsolete `prepReviewOverlay` wrapper; prepared-application review renders via the shared modal shell |
| Prepared Application content from profile | **RESOLVED** | Package is profile-derived (`JobApplicationPreparationService` builds from `CandidateProfile` + `Job`); no hardcoded Java template |
| Free-text keywords on Matches page | ✅ Done (12.3) | Field removed; `matches.html` states discovery is profile-driven and needs no keyword typing |
| Resume → Job Search CTA | **SUPERSEDED** | The post-upload CTA is "View My Matches" (`#continueToMatchesBtn`) — Matches is the primary discovery surface; there is deliberately no keyword box to fill |
| Career Agent "WAITING" UI artifact | **RESOLVED** | The invented placeholder state and the static progress list were removed (Cleanup Batch 1, `7fb6e18`); `careerAgent.js` now renders the deterministic advisor report |
| Duplicate DOM IDs in advisor modal | **SUPERSEDED** | Legacy four-duplicate-ID issue on the pre-modal-shell wrapper; advisor modal now renders via the shared modal shell (single body), with `jobTitle`/`company` on `ApplicationAdvisorResponse` |
| Live job source reachability | ✅ Verified (12.10) | Confirmed against a running app: results came back from ARBEITNOW, REMOTIVE and OPENINGS-MCP with `live: true`. The Phase 12.8 flakiness note (advisor per-request `JobSearchService.findById` intermittently throwing `JobNotFoundException`; repro 30 identical advisor POSTs → 14×200 / 16×404) was a backend reliability observation, not a reachability gap |
| SMTP transport for application email | **NOT CONFIGURED** | No SMTP configured; email sends are simulated and labelled in the UI ("Email send simulated — no SMTP configured. Nothing was actually mailed.") |
| Persisted email-sent flag | **PARTIAL (12.9)** | `emailSendAttemptedAt` + `emailSendResult` (`SENT` / `SENT_SIMULATED`) are now persisted, and a package already in `EMAIL_SENT` refuses further sends (400). While a package is still `APPROVED_FOR_APPLICATION` a repeat send is still permitted, so a full duplicate-send lock is still outstanding |
| Employer-site opening is not submission | **ENFORCED (12.9)** | Handoff records only that the employer page was opened (`employerOpenedAt`/`employerUrl`); the timeline row always says "submission not confirmed by this platform". The platform cannot observe or prove a submission |
| Independent mailbox-ownership verification | **NOT IMPLEMENTED** | The email recipient is user-entered and user-confirmed (manual confirmation of the address); no independent mailbox-ownership verification exists — out of scope |
| D2 page-level overflow (≤768px) | ✅ Resolved (12.10) | Was deferred through 12.6–12.9 (Phase 12.9 re-measured 51px at 768px and 429px at 390px on the Applications page); resolved in Phase 12.10 (`1ba94cc`) |
| End-to-end ATS Resume Tailoring browser verification | **DONE** | Core workflow verified (tailor 200, modal render 1440/768/390, PDF/DOCX download 200, close/reopen, 0 console/network errors) + H artifact read-back passed via PDFBox 3 / POI 5.2.5; the D2 page-level overflow that was deferred from this phase was resolved in 12.10 |
| Phase 12.5 verification evidence | **NOT RECORDED** | Career Analysis / Application Readiness UI polish was implemented (`6f4b483`) but has no spec, no tests, and no committed browser-verification record |
| Authentication / ownership / transport security | **DEFERRED** | The app binds to `127.0.0.1` and is a trusted single-user local-first prototype. Candidate endpoints are unauthenticated and ownership-free; a networked or multi-user deployment needs an identity/ownership/transport-security design |
| Docker / live PostgreSQL+pgvector verification | **NOT EXECUTED** | The Docker daemon was unavailable in the latest verification run, so `init.sql` was never applied to a live container and the 15 Testcontainers PostgreSQL/pgvector tests were skipped. The app itself runs on in-memory H2 by default |

---

## 5. Out of Scope (Not Planned)

- Automatic (unattended) form filling on employer sites — filling or submitting without user review and a user-triggered action
- Automatic application submission, including any CAPTCHA/MFA bypass
- React/SPA migration (vanilla HTML/CSS/JS only)
- Cloud-only deployment (local-first architecture)
- Multi-user / multi-tenant (single-user local app)

> **Assisted apply (Phase 12.8) is NOT excluded by the above**: the user starts the flow from an
> approved application package, reviews and edits every prepared value in the platform UI, and
> performs the final paste and submit on the employer site themselves. The platform never writes to
> or submits a page it does not serve, and never touches CAPTCHA/MFA or credentials.

---

## 6. Acceptance Criteria for "Complete"

A real user can:
1. Open Resume page → Upload real DOCX/PDF → Wait for processing → See Profile Ready
2. See clean personal info (name, email, phone, location, GitHub, LinkedIn)
3. See correct skills (technical, software, hardware/embedded)
4. See correct education, certifications, projects, experience
5. See validated career tracks (primary + optional secondary)
6. Continue to Matches → See profile-driven matches (no keywords needed)
7. Open match → See explainable match (matched/missing skills, strengths, concerns, factor scores)
8. Run Career Analysis → See readiness score, gaps, recommendations
9. Run ATS Resume Tailoring → See tailored resume (reordered existing content only)
10. Prepare Application → Review tailored resume + cover letter + Q&A → Approve
11. Click "Apply on Employer Site" → Opens official employer URL
12. Track application in Applications page → Filter by status → See status badge + event timeline + only the actions that status allows (no auto-submission, no unattended email)

**AND**: No SVG contamination, no stale profile, no invented data, no silent failures.