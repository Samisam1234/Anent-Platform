# Product Requirements Document — agent-platform

> **Status**: CURRENT — reflects actual implemented product as of commit 0a02e22 (Phase 12.6 — ATS Resume Tailoring verified; Phase 12.7 planned)
> **Phase 11.1 frozen**: 0d141d6 | **Phase 12.1 verified**: 553eb76 | **Phase 12.2 prep**: f47562b | **Phase 12.3 verified**: a6eaf5c | **Phase 12.4 verified**: da933ac | **Phase 12.5 implemented (verification not recorded)**: 6f4b483 | **Phase 12.6 verified**: 0a02e22 | **Phase 12.7 planned**
> **Current local model**: llama3.2:3b (Ollama)

---

## 1. Product Vision

agent-platform is a local-first AI Career Agent platform that helps users turn a resume into actionable job matches, career analysis, and application materials — all running locally with Ollama LLMs, with optional cloud Gemini fallback for resume parsing.

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
| 8 | **Application Preparation** — tailored resume + cover letter + Q&A | **PARTIAL** | Prepare endpoint, profile-derived package generation (`JobApplicationPreparationService`), and review via the shared modal shell exist; remaining Phase 12.7 deltas (Q&A/strengths/recommendation review sections, edit deep-link, email recipient verification) defined in PHASE_12.7_SPEC.md |
| 9 | **Employer Application** — manual via official URL only | **IMPLEMENTED** | "Apply on Employer Site" / "View Job Listing" buttons; no auto-submit |
| 10 | **Application Tracking** | **PLANNED** | Not yet implemented |

---

## 3. Core Requirements (Non-Negotiable)

| Requirement | Status | Details |
|-------------|--------|---------|
| **Resume = Source of Truth** | ✅ Enforced | All profile data derives from resume; nothing invented |
| **Evidence-Based Matching** | ✅ Implemented | Skills/role/experience/track/location/education weighted; evidence shown |
| **Real Job Listings Only** | Partial | Mock source is default (config disabled); live sources exist but disabled |
| **Official/Source URLs Only** | ✅ Enforced | Job modal shows source URL; employer application URL only when provided |
| **Manual/User-Authorized Application** | ✅ Enforced | No auto-submit; explicit "Approve" step; "Apply on Employer Site" opens external URL |
| **No Fabricated Qualifications** | ✅ Enforced | Tailoring reorders existing content; never invents skills/experience |
| **No Auto-Submit** | ✅ Enforced | Explicit "Approve for Application" step required |
| **Evidence-Based Career Tracks** | Partial | Tracks inferred from resume evidence; UI over-infers (fixed in 12.1) |
| **Explicit Keyword Behavior** | ✅ Enforced | Free-text keyword field removed (Phase 12.3); keywords derive from profile skills/roles |
| **Official/Source URLs Only** | ✅ | Job modal shows "View Job Listing" (source) vs "Apply on Employer Site" |

---

## 3. Known Gaps / Planned / Blocked

| Item | Status | Details |
|------|--------|---------|
| Job Search: remove free-text keyword field | ✅ Done (12.3) | Removed; keywords derive from profile skills/roles |
| Live job sources enabled by default | **PLANNED** | `job-sources.public-api.enabled: false` currently |
| Career Analysis modal DOM fixes | **SUPERSEDED** | Legacy `advisorReviewJobTitle`/`advisorReviewCompany` wrappers removed; Career Analysis renders via the shared modal shell |
| Prepared Application modal wrapper | **SUPERSEDED** | Obsolete `prepReviewOverlay` wrapper; prepared-application review renders via the shared modal shell |
| Prepared Application content from profile | **RESOLVED** | Package is profile-derived (`JobApplicationPreparationService` builds from `CandidateProfile` + `Job`); no hardcoded Java template |
| Free-text keywords on Matches page | **PLANNED** | Should derive from profile |
| Resume → Job Search CTA | **PLANNED** | No "Continue to Job Search" after profile ready |
| Career Agent "WAITING" UI artifact | **PLANNED** | Rename/drop placeholder state |
| Duplicate DOM IDs in advisor modal | **SUPERSEDED** | Legacy four-duplicate-ID issue on the pre-modal-shell wrapper; advisor modal now renders via the shared modal shell (single body), with `jobTitle`/`company` on `ApplicationAdvisorResponse` |
| Live job source reachability | **UNVERIFIED** | Sandbox has no outbound network |
| End-to-end ATS Resume Tailoring browser verification | **DONE** | Core workflow verified (tailor 200, modal render 1440/768/390, PDF/DOCX download 200, close/reopen, 0 console/network errors) + H artifact read-back passed via PDFBox 3 / POI 5.2.5; D2 page-level overflow at 768/390px deferred pending 12.10 |

---

## 4. Out of Scope (Not Planned)

- Automatic application submission (CAPTCHA/MFA bypass)
- Automatic form filling on employer sites
- React/SPA migration (vanilla HTML/CSS/JS only)
- Cloud-only deployment (local-first architecture)
- Multi-user / multi-tenant (single-user local app)

---

## 5. Acceptance Criteria for "Complete"

A real user can:
1. Open Resume page → Upload real DOCX/PDF → Wait for processing → See Profile Ready
2. See clean personal info (name, email, phone, location, GitHub, LinkedIn)
3. See correct skills (technical, software, hardware/embedded)
4. See correct education, certifications, projects, experience
3. See validated career tracks (primary + optional secondary)
4. Continue to Job Search → See profile-driven matches (no keywords needed)
5. Open match → See explainable match (matched/missing skills, strengths, concerns, factor scores)
4. Run Career Analysis → See readiness score, gaps, recommendations
5. Run ATS Resume Tailoring → See tailored resume (reordered existing content only)
5. Prepare Application → Review tailored resume + cover letter + Q&A → Approve
5. Click "Apply on Employer Site" → Opens official employer URL
5. Track application in Applications page

**AND**: No SVG contamination, no stale profile, no invented data, no silent failures.