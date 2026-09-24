# Design — agent-platform

> **Status**: CURRENT — reflects actual UX as of commit da933ac (Phase 12.4 — Match Details UI polish)
> **Phase 11.1 frozen**: 0d141d6 | **Phase 12.1 verified**: 553eb76 | **Phase 12.2 prep**: f47562b | **Phase 12.3 verified**: a6eaf5c | **Phase 12.4 verified**: da933ac
> **Current local model**: llama3.2:3b (Ollama)

---

## 1. Visual Style (Implemented)

| Aspect | Specification |
|--------|---------------|
| **Color Palette** | Professional SaaS: `--bg-primary:#0f1115`, `--bg-secondary:#161a20`, `--bg-card:#1b2027`, `--accent:#4f7cff`, `--success:#2fbf71`, `--warning:#e0a020`, `--danger:#e05252` |
| **Typography** | `--font-sans:'Plus Jakarta Sans'`, `--font-mono:'JetBrains Mono'` (Google Fonts) |
| **Spacing** | `--radius:6px`, `--radius-lg:10px`, `--shadow:0 1px 2px rgba(0,0,0,.24), 0 8px 24px rgba(0,0,0,.20)` |
| **Layout** | Sticky header (z-index 100), centered content max-width 1280px, responsive grid |
| **Icons** | Inline SVG (no icon fonts), 18-24px standard size |

---

## 2. Page Inventory (Implemented)

| Page | File | State | Description |
|------|------|-------|-------------|
| **Dashboard** | `index.html` | **IMPLEMENTED** | Welcome hero, 3-card nav (Resume/Matches/Applications), Career Fit panel |
| **Resume** | `resume.html` | **IMPLEMENTED** | 4-step upload → review → analyzing → profile; drag-drop, progress stages |
| **Matches** | `matches.html` | **IMPLEMENTED** | Candidate banner, filters, match cards, modal actions |
| **Jobs** | `jobs.html` | **IMPLEMENTED** | Search form, job cards, source banner |
| **Applications** | `applications.html` | **IMPLEMENTED** | List/Detail view, edit mode, approve/send email |
| **Custom AI** | `custom.html` | **IMPLEMENTED** | Structured task runner (summarize/extract/classify/general) |

---

## 3. Resume Upload (Implemented)

| State | UI Behavior |
|-------|-------------|
| **Empty** | Drag-drop zone + file input; "Analyze resume" disabled |
| **File Selected** | File card shows name/size/type; "Analyze resume" enabled |
| **Analyzing** | 3-stage progress (Reading → AI Structuring → Saving), elapsed timer, progress bar (capped at 92% until response), cancel button |
| **Success** | Result banner (green/amber), profile sections, "View My Matches" CTA |
| **Error** | Alert banner with title, message, actionable hints; retry button |

**Timeout Handling**: Client 630s, Server 600s. First-run cold start ~290s. Page shows "up to 10 minutes" notice.

**Issues**: None (resume flow works)

---

## 3. Profile Display (Implemented — with UI Bugs)

### Rendered Sections (in order)
1. **Header** — Name, contact row (email/phone/location icons), Profile ID badge
2. **Preferred Roles** — Chip list
3. **Skills** — Chip list
4. **Software Skills** — Chip list
5. **Hardware Skills** — Chip list
6. **Education** — List
7. **Experience** — List
8. **Internships** — List
9. **Projects** — List
10. **Certifications** — List

### Known UI Bugs (From INVESTIGATION-REPORT)

| Bug | Location | Impact |
|-----|----------|--------|
| **Missing `advisorReviewJobTitle` / `advisorReviewCompany`** | `matches.html` advisor modal | Career Analysis modal throws, never opens |
| **Missing `prepReviewOverlay` wrapper** | `matches.html` prepared application modal | Modal renders inline, fields blank, can't dismiss |
| **Duplicate DOM IDs** | `matches.html` advisor modal (4 pairs) | Content injected into wrapper `<div>` instead of `<ul>` |
| **Missing `prepReviewOverlay` guard** | `matches.js:550` | `openPreparedReview()` silent no-op |
| **SVG contamination in contact fields** | `resume.js` parsing | Email/phone/location show "svg..." garbage |

---

## 4. Matches Page (Implemented)

### Layout
- **Candidate Banner** — Name, "Matching as [Name]" or "No profile"
- **Filters** — Location (text), Career Track (select), Min Score (select), Limit (select)
- **Actions** — "Find My Matches" (primary), "Reset"
- **Source Banner** — "Live Job Source Active" / "Live Source Returned No Listings"
- **Results** — Count, active filter chips, match cards grid

### Match Card (Rendered)
- **Header** — Job title, company
- **Score Strip** — Large score (0-100), recommendation badge
- **Meta Row** — Location, Experience, Employment Type, Career Track
- **Explanation** — Text
- **Skills** — Required matched/missing, Preferred matched/missing (color-coded chips)
- **Strengths/Concerns** — Green/red lists with icons
- **Actions** — "Match Details", "Career Analysis", "Tailor Resume", "Prepare Application" (score≥60), "View Job Listing"/"Apply on Employer Site"

### Known Issues
- "Development Mock Source Active" banner hardcoded default
- Keywords & Skills filter present (should be removed per PRD)
- Match Details modal uses non-existent `#advisorReviewJobTitle`/`#advisorReviewCompany`

---

## 5. Job Details Modal (Implemented)

- **Trigger** — "Match Details" / "View Details" buttons
- **Content** — Title, company, meta row (location, exp, type, date, track), description, required/preferred skills
- **Source Row** — Source name
- **Footer** — "Check Match" (navigates to matches.html?jobId=), source link

---

## 6. Career Analysis Modal (Backend OK / Frontend Broken)

### Backend Returns
```json
{
  "recommendation": "STRONGLY_RECOMMENDED|RECOMMENDED|APPLY_WITH_IMPROVEMENTS|LOW_PRIORITY|NOT_RECOMMENDED",
  "applicationReadinessScore": 0-100,
  "jobMatchScore": 0-100,
  "scoreBreakdown": { atsReadinessScore, jobMatchScore, skillFitScore, roleFitScore, ... },
  "strengths": [...],
  "concerns": [...],
  "recommendedActionDetails": [{type, focus, description}],
  "recommendationExplanation": "..."
}
```

### Frontend Rendering (Broken)
- Missing `advisorReviewJobTitle` / `advisorReviewCompany` header elements
- Duplicate DOM IDs cause content injection into wrapper `<div>` instead of `<ul>`
- `matches.js:663` throws on `document.getElementById('advisorReviewJobTitle').textContent`

---

## 6. Prepared Application Modal (Backend OK / Frontend Broken)

### Backend Returns
```json
{
  "applicationId": 1,
  "jobId": "...",
  "jobTitle": "...",
  "company": "...",
  "matchScore": 85,
  "tailoredProfessionalSummary": "...",
  "coverLetter": "...",
  "applicationAnswers": "...",
  "matchingSkills": [...],
  "missingSkills": [...],
  "resumeHighlights": [...],
  "recommendation": "RECOMMENDED",
  "recommendationExplanation": "..."
}
```

### Frontend Rendering (Broken)
- **Missing** `<div class="prep-review-overlay" id="prepReviewOverlay" hidden>` wrapper
- Modal renders inline at bottom of page, fields show `—`
- `matches.js:550` `getElementById('prepReviewOverlay')` → null → silent no-op
- Four duplicate DOM IDs in advisor modal

---

## 7. Career Agent Modal (Backend OK / Frontend Artifact)

### Current Behavior
- Progress list rendered once from empty `{}` → all stages show `WAITING`
- `AgentStatus` backend enum: `PENDING, RUNNING, COMPLETED, FAILED, SKIPPED` — **no `WAITING`**
- `careerAgent.js:buildProgressHtml` invents `WAITING` status
- List never updated; all stages show `WAITING` for entire request duration

### Required Fix
- Relabel placeholder state (e.g., "Pending" / "Queued")
- Or drop static progress list until SSE/polling added (not approved)

---

## 8. Resume Upload States (Implemented)

| State | Visual |
|-------|--------|
| **Upload** | Drag-drop zone, file input, "Analyze resume" (disabled until file) |
| **Review** | File card (name, size, type), "Analyze resume" enabled |
| **Analyzing** | 3-stage progress, elapsed timer, progress bar (capped 92%), cancel |
| **Success** | Green banner, profile sections, "View My Matches" CTA |
| **Error** | Red banner, actionable hints, retry button |

---

## 10. Visual Components (Implemented)

| Component | Classes | Usage |
|-----------|---------|-------|
| **Button Primary** | `.btn-primary` | Primary actions |
| **Button Secondary** | `.btn-secondary` | Secondary actions |
| **Button View Details** | `.btn-view-job.btn-view-details` | Job/Job Details |
| **Button Check Match** | `.btn-check-match` | Match card |
| **Button Apply** | `.btn-apply` | Prepare Application |
| **Button Advisor** | `.btn-advisor` | Career Analysis |
| **Button Tailor** | `.btn-tailor` | ATS Tailoring |
| **Button Source Listing** | `.btn-source-listing` | View Job Listing |
| **Button Apply External** | `.btn-apply-external` | Apply on Employer Site |
| **Skill Tag** | `.skill-tag` + variants | Skills display |
| **Match Score Strip** | `.match-score-strip` | Score bar + badge |
| **Match Card** | `.match-card` | Job match display |
| **Modal Shell** | `.ms-overlay`, `.ms-card` | All modals |
| **Toast** | `.toast`, `.toast-error` | Notifications |
| **Chip** | `.summary-chip` | Filter/summary labels |

---

## 11. Color/Status Semantics

| Semantic | Class | Color |
|----------|-------|-------|
| **Success/Good** | `.is-good`, `.level-strong`, `.fit-high` | Green (`--success`) |
| **Warning/Medium** | `.is-caution`, `.level-developing`, `.fit-medium` | Yellow (`--warning`) |
| **Danger/Bad** | `.is-gap`, `.is-low`, `.fit-low` | Red (`--danger`) |
| **Neutral/Missing** | `.is-limited`, `.is-muted` | Muted (`--text-muted`) |

---

## 12. States Not Yet Implemented (Planned)

| Feature | Page | Status |
|---------|------|--------|
| "Continue to Job Search" CTA after profile ready | `resume.html` | **PLANNED** |
| Remove Keywords & Skills from Job Search | `jobs.html` | **PLANNED** |
| Remove Keywords & Skills from Matches | `matches.html` | **PLANNED** |
| Career Analysis modal DOM fixes | `matches.html` + `matches.js` | **PLANNED** |
| Prepared Application overlay wrapper | `matches.html` | **PLANNED** |
| Prepared Application content from profile | `matches.js` + backend | **PLANNED** |
| Career Agent "WAITING" relabel | `careerAgent.js` | **PLANNED** |
| Live job sources enabled by default | `application.yml` + `jobs.js` | **PLANNED** |
| Resume → Job Search CTA | `resume.html` / `resume.js` | **PLANNED** |

---

## 11. Stale/Deprecated UI Elements (To Remove)

| Element | Location | Action |
|---------|----------|--------|
| `#jobsKeywordsInput` | `jobs.html` + `jobs.js` | **REMOVED (12.3)** |
| `#matchesKeywordsInput` | `matches.html` + `matches.js` | **REMOVED (12.3)** |
| "Development Mock Source Active" default banner | `jobs.js` / `matches.js` | **REPLACE** with live-first logic |
| `#jobsKeywordsInput` in `buildPayload` | `jobs.js` | **REMOVE** |
| `#matchesKeywordsInput` in payload | `matches.js` | **REMOVE** |
| Duplicate DOM IDs in advisor modal | `matches.html` | **FIX** (de-duplicate) |
| Missing `advisorReviewJobTitle`/`Company` | `matches.html` | **ADD** |
| Missing `prepReviewOverlay` | `matches.html` | **ADD** |

---

## 12. Visual Style Reference (No Changes)

- **Professional SaaS** — neutral slate surfaces, single accent (`--accent:#4f7cff`), layered shadows
- **No futuristic/glowing effects** — no neon, no animated gradients, no particle backgrounds
- **Consistent spacing** — 8px base unit, 6px/10px radius
- **Accessible** — focus-visible outlines, ARIA labels, semantic HTML
- **Responsive** — mobile-first, breakpoints at 640px/768px/1024px

---

**END OF DESIGN.md**