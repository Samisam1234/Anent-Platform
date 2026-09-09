# Investigation Report — agent-platform (investigation only, no code changed)

Branch: `arena/01a08566-anent-platform` · Base: `b5784b7` ("Complete logging phase 8.3") · Working tree clean.

Every claim below was produced by reading a file or running a command in this repo. Where a claim is
*not* verified it is marked **UNVERIFIED**.

---

## 1. Current architecture / modules

Root `pom.xml` declares 7 modules (Java 21, Spring Boot 3.4.2):

| Module | Package | State (verified) |
|---|---|---|
| `agent-core` | `com.agentplatform.core` | Real: `AiErrorClassifier`, `AiStatusService`, `GeminiConfig`, `OllamaChatModelFactory` |
| `orchestrator` | `com.agentplatform.orchestrator` | The bulk of the platform: `advisor/`, `agent/`, `application/`, `gap/`, `job/`, `matching/`, `resume/`, `tailoring/`, `service/` |
| `ui` | `com.agentplatform.ui` | Only bootable module; controllers + DTOs + `src/main/resources/static` (plain HTML/CSS/JS) |
| `memory-service` | `com.agentplatform.memory` | Real: in-memory + JPA/pgvector conversation stores |
| `tool-service` | `com.agentplatform.tools` | Real: `@Tool` beans (image/email/WhatsApp) |
| `logging` | `com.agentplatform.logging` | Real: `LoggingContext`, `PiiSanitizer` (phase 8.3) |
| `rag-service` | `com.agentplatform.rag` | Real but dormant (`rag.enabled: false`) |

`ui/AgentPlatformApplication` uses `@SpringBootApplication(scanBasePackages = "com.agentplatform")`,
and `ui/PersistenceConfig` holds `@EnableJpaRepositories`/`@EntityScan` — so **controllers living in
`orchestrator` are live endpoints** (notably `JobApplicationController`).

Frontend is 5 pages, all plain HTML + vanilla JS IIFEs — **no React anywhere** (verified: zero
`react` references in `static/`).

### Existing job-application flow pages

`index.html` (dashboard) → `resume.html` → `jobs.html` → `matches.html` → `applications.html`,
plus shared `jobDetails.js` and `careerAgent.js`.

---

## 2. Current Job Search flow

**Frontend** `ui/src/main/resources/static/jobs.html` + `jobs.js`
- On load `jobs.js:init()` calls `runSearch()` immediately.
- `buildPayload()` (jobs.js:194-203) sends `{keywords, location, experience, employmentType, limit}`
  to `POST /api/v1/jobs/search`.
- Filters present today: **Keywords & Skills** (jobs.html:86-92), **Location** (:93-99),
  **Experience** (`jobsExperienceSelect`), **Employment Type** (`jobsTypeSelect`), **Limit**.

**Backend** `ui/.../JobSearchController` → `orchestrator/.../job/JobSearchService.search()`
- Collects from all `JobSource` beans where `isAvailable()`, deduplicates
  (`JobDeduplicationService`), drops low-quality rows (`hasMinimalQuality`), then applies
  keyword/location/source/experience/employmentType/datePosted filters.
- **Nothing in `JobSearchService` or `JobSearchRequest` knows about a candidate profile.** There is no
  `candidateProfileId` on `JobSearchRequest` / `JobSearchRequestDto` (verified by reading both records).

---

## 3. Current Matches flow

**Frontend** `matches.html` + `matches.js`
- `init()` reads `agentplatform:candidateId` / `agentplatform:candidateName` from localStorage.
  No candidate → shows the "Upload Resume First" panel and hides the controls. With a candidate it
  **auto-fires `runMatch()`**.
- Payload (matches.js:143-150): `{candidateProfileId, keywords, location, limit, minScore, careerTrack}`
  to `POST /api/v1/jobs/match`.
- Filters present today: **Keywords & Skills** (matches.html:89-95), Location, Career Track,
  Min Match Score, Limit.

**Backend** `JobMatchController` → `JobMatchingService.matchJobs()`
- Resolves the stored profile (`CandidateProfilePersistenceService.getByIdOrThrow`).
- **Job discovery pre-filter uses the user's free-text keywords**:
  `new JobSearchRequest(request.keywords(), request.location(), ...)` (JobMatchingService.java:91).
- **Scoring already is profile-driven** — `evaluateJob(candidate, job)` runs
  `SkillMatchingEngine`, `RoleMatchingEngine`, `LocationMatchingEngine`,
  `ExperienceMatchingEngine`, `CareerTrackEngine`, `EducationMatchingEngine` against the parsed
  `CandidateProfile`, weighted via `JobMatchingConfig`. So the "profile intelligence" is already
  used for *ranking*; it is **not** used for *job discovery*.

---

## 4. Current Application Advisor flow

**Trigger** `matches.js` delegated `.application-advisor-btn` click handler → after a `confirm()`,
`POST /api/v1/jobs/advisor {candidateId, jobId}`. On 200 it runs (matches.js:497-498):

```js
showToast('Application Advisor completed.', 'success');
openAdvisorReview(data);          // ← throws on the next line, after the toast already showed
```

**Backend** `ui/.../ApplicationAdvisorController` → `orchestrator/.../advisor/ApplicationAdvisorService.advise()`
- Resolves profile by id and job by id, then computes a fully deterministic result:
  `CareerGapAnalysisService` → `ResumeTailoringAnalysisService` → `AtsReadinessAnalysis`, plus a
  single-job `JobMatchingService.matchProfileAgainstJobs(...)`.
- Composite score = `0.60 * ATS readiness + 0.40 * job match score`, clamped 0-100
  (ApplicationAdvisorService.java:~120). Recommendation bands 90/75/60/40.
- **The backend works end-to-end.** It returns `recommendation`, `applicationReadinessScore`,
  `strengths`, `concerns`, `recommendedActions`, `recommendedActionDetails`, `jobMatchScore`.

**The failure is 100% frontend** — see §8.

---

## 5. Current Prepared Application flow

**Trigger** `matches.js` `.application-prepare-btn` (only rendered when `m.matchScore >= 60`) →
`confirm()` → `POST /api/v1/applications/prepare {candidateId, jobId, jobTitle, company}`.

**Backend** `orchestrator/.../application/JobApplicationController.prepareApplication()`
- `validatePreparationRequest` re-resolves the job server-side and overwrites
  `jobTitle`/`company`/`location` from the real `Job` (so the client cannot fabricate them).
- Calls `JobApplicationPreparationService.prepareApplication(..., skipAi = isMockJob(jobId))`.
- Persists a `JobApplication` (`ApplicationStorageService`), sets status `GENERATED`,
  sets `result.applicationId`, returns `ApplicationPreparationResult`.
- `ApplicationPreparationResult` **does** carry `jobTitle`, `company`, `matchScore`,
  `tailoredProfessionalSummary`, `coverLetter`, `matchingSkills`, `missingSkills`,
  `resumeHighlights` — every field the modal reads.
- **Nothing is auto-submitted.** Approval is a separate explicit
  `POST /api/v1/applications/{id}/approve`. Safety rule intact.

**Gap found:** `JobApplicationPreparationService` **never reads the candidate profile.**
Its signature takes only `candidateId, jobId, jobTitle, company, location, customInstructions`.
- Mock jobs (`jobId` contains `"mock"`) → `skipAi=true` → `generateFallbackApplication(...)` returns
  hard-coded generic Java/Spring-Boot text and a hard-coded `matchScore` of 85.
- Non-mock jobs → `buildApplicationPrompt(jobTitle, company, location, customInstructions)` — the
  prompt contains **no candidate data at all**.
So even once the modal opens, the content is not derived from the parsed resume. (Note the sibling
`ApplicationPreparationService` in the same package *is* profile-driven, but it is only used by
`ApplicationEmailController` / `ApplicationAdvisorAgent`, not by `/applications/prepare`.)

---

## 6. Current Career Agent status flow

**Frontend** `careerAgent.js` (loaded on `matches.html`), triggered by `[data-career-agent]`.
- `run()` sets `body.innerHTML = buildProgressHtml({})` — an **empty** executions map — then
  `await fetch('/api/v1/agent/orchestrate')`, and only on resolution replaces the body with
  `buildResultsHtml(data)`.
- `buildProgressHtml` renders any missing agent as `{ status: 'WAITING' }`.

**Backend** `OrchestrationController` → `OrchestrationService.orchestrate()` →
`CareerAgentOrchestrator.orchestrateTracked()` — strictly **sequential and synchronous**, one
blocking HTTP round-trip. There is no run id, no polling endpoint, no SSE.

**Verdict on "stages stuck at `WAITING`": this is a frontend status/rendering artifact, not a
backend orchestration bug.**
- `AgentStatus` (backend enum) contains only `PENDING, RUNNING, COMPLETED, FAILED, SKIPPED` —
  **`WAITING` does not exist server-side.** It is invented by `careerAgent.js:buildProgressHtml`.
- Because the progress list is rendered once from `{}` and never updated, all five stages show
  `WAITING` for the entire duration of the request.
- It therefore *looks* stuck whenever the request is slow. `ResumeAgent`, `CareerAdvisorAgent` and
  `ApplicationAdvisorAgent` each optionally call `AgentReasoningService` (up to `MAX_AI_CALLS = 3`
  Ollama calls) with `ollama.reasoning-timeout: 120s` in `application.yml` — a cold CPU model load can
  legitimately hold the request for a long time. The orchestrator's own javadoc states
  "none remains `RUNNING` after any orchestration method returns", i.e. it cannot be server-stuck.

---

## 7. Job-source configuration and which source the UI actually uses

`ui/src/main/resources/application.yml`:

```yaml
job-sources:
  public-api:
    enabled: false
    base-url: https://remotive.com/api/remote-jobs
    api-key: ""
```

- `PublicApiJobSource.isAvailable()` returns `properties.isEnabled()` → **`false`**, so
  `JobSearchService` filters it out of `activeSources`.
- The only remaining source is `MockJobSource` (`isAvailable()` → `true`, `isLive()` → `false`).
- `JobSearchService` then returns `anyLive = false` and the message
  `"Live job source not configured. Returning development mock data."`
- `jobs.js:updateSourceBanner()` renders **"Development Mock Source Active"** and additionally fires
  a toast: *"Showing development mock jobs. Enable the public job source for live listings."*
- `matches.js:renderMatches()` renders **"Development Mock Source Active (MOCK_SOURCE)"**.

**So: the live/public adapter (`PublicApiJobSource`, Remotive, with `JobNormalizer` +
`JobUrlValidator` + dedup, 16 `@Test` cases in `PublicApiJobSourceTest`) is fully implemented but
switched off, and the mock catalog is the de-facto production experience.** This is the
requirements 6/7 gap. Remotive's public feed needs no API key, so `api-key` can stay empty.

**UNVERIFIED:** whether Remotive is reachable from the deployment target — this sandbox has no
outbound network (`curl` to `remotive.com` and `repo.maven.apache.org` both fail with
`SSL_ERROR_SYSCALL`), so I could not exercise the live adapter.

---

## 8. Exact cause of `Cannot set properties of null (setting 'textContent')`

**Root cause: `matches.js:663` reads an element id that does not exist in `matches.html`.**

```js
// ui/src/main/resources/static/matches.js
657:  const advisorReviewOverlay = document.getElementById('advisorReviewOverlay');
...
663:  document.getElementById('advisorReviewJobTitle').textContent = adv.jobTitle || '—';   // ← null
664:  document.getElementById('advisorReviewCompany').textContent  = adv.company  || '—';   // ← null
```

Verified by cross-referencing every `getElementById('…')` in the static assets against every
`id="…"` in the corresponding page (Node script, run this session):

```
### matches.html
  duplicate ids: [["advisorReviewStrengths",2],["advisorReviewDetails",2],
                  ["advisorReviewConcerns",2],["advisorReviewActions",2]]
  ids referenced by JS but ABSENT in HTML:
    matches.js -> #prepReviewOverlay
    matches.js -> #advisorReviewJobTitle
    matches.js -> #advisorReviewCompany
```
and `grep -c "advisorReviewJobTitle" matches.html` → **0**; same for `advisorReviewCompany` → **0**.

The advisor modal in `matches.html` (lines 186-258) declares only:
`advisorReviewOverlay, advisorReviewTitle, advisorReviewSubtitle, advisorReviewClose,
advisorReviewBanner, advisorReviewRecommendation, advisorReviewReadiness, advisorReviewJobMatch,
advisorReviewStrengths, advisorReviewConcerns, advisorReviewActions, advisorReviewDetails,
advisorReviewDone` — there is **no header row for Job Title / Company**.

**Consequence chain:** `openAdvisorReview()` throws at line 663, *before* line 690
(`advisorReviewOverlay.hidden = false`) — so **the Application Advisor modal never opens at all**,
even though `POST /api/v1/jobs/advisor` returned 200 and the success toast at line 497 already fired
("Application Advisor completed."). The user sees a green success toast, a console error, and no modal.

**Secondary cause:** the backend never sends those two fields either.
`ApplicationAdvisorResponse` has exactly 7 components
(`recommendation, applicationReadinessScore, strengths, concerns, recommendedActions,
recommendedActionDetails, jobMatchScore`) and `grep -rn "jobTitle\|company"` over the whole
`orchestrator/advisor/` package returns **zero hits**. So a fix must touch both sides: add the ids to
the markup *and* either add `jobTitle`/`company` to the response or populate them from the
`data-job-title`/`data-company` attributes the click handler already has on the button.

---

## 9. Other concrete inconsistencies (file / class / function / root cause)

### 9.1 Prepared Application modal is missing its overlay wrapper → "fields appear blank"

`ui/src/main/resources/static/matches.html:262-263`

```html
<!-- Prepared Application Review Modal -->
    <div class="prep-review-modal" role="dialog" ...>     ← the wrapper <div class="prep-review-overlay" id="prepReviewOverlay" hidden> is GONE
```

Compare the advisor modal at line 186, which does have
`<div class="advisor-review-overlay" id="advisorReviewOverlay" hidden>`.

- `style.css:178` defines `.prep-review-overlay{position:fixed;inset:0;…}` and `style.css:179` defines
  `.prep-review-modal{width:100%;max-width:640px;…}` — proving the wrapper was intended and that the
  inner box has **no positioning of its own**.
- Therefore `.prep-review-modal` renders **inline in normal document flow at the bottom of
  `matches.html`, permanently visible**, showing only its static placeholders: `—` for subtitle,
  job title, company, match score, summary and cover letter, and three empty `<ul>`s.
  That is precisely the reported "Prepared Application fields have appeared blank".
- `matches.js:550` `document.getElementById('prepReviewOverlay')` → `null`, so
  `matches.js:553` `if (!prepReviewOverlay) return;` makes `openPreparedReview()` a **silent no-op**.
  The `/api/v1/applications/prepare` call succeeds, the "Application prepared successfully." toast at
  `matches.js:540` fires, `openPreparedReview(data)` is called at line 541 and returns immediately —
  nothing opens.
- `closePreparedReview()`, the `#prepReviewClose`/`#prepReviewDone` handler and the Escape handler
  are all `null`-guarded too, so the orphaned box can never be dismissed.
- Additional latent bug in the same function: `matches.js:554`
  `const app = data && data.applicationId != null ? data : {};` — if the response ever lacks
  `applicationId` every field silently becomes `—`.

### 9.2 Four duplicate DOM ids in the advisor modal

`matches.html` 223/224, 232/233, 241/242, 250/251 — the same id is on both the wrapper `div` and the
inner `ul`:

```html
<div class="advisor-review-card" id="advisorReviewStrengths">
    <ul class="advisor-ul"        id="advisorReviewStrengths"></ul>
</div>
```

`getElementById` returns the first match in document order (the `div`), so
`matches.js:670-687` injects `<li>` elements into the `div` and leaves the `<ul>` empty. Same for
`advisorReviewConcerns`, `advisorReviewActions`, `advisorReviewDetails`.

### 9.3 `careerAgent.js` reads two fields the backend never sends

`careerAgent.js:292` → `data.jobMatchScore`; `careerAgent.js:303-307` → `data.recommendedActionDetails`.
`grep -c "jobMatchScore" ui/src/main/java/com/agentplatform/ui/dto/OrchestrationRunResponseDto.java`
→ **0**. `OrchestrationRunResponseDto` exposes only `runStatus, success, message, agentExecutions,
aiCallsUsed, toolCallsUsed, stoppingAgentType`. Result: the "Job Match" cell always renders **0** and
the "Recommended Action Details" section never renders.

### 9.4 No path from resume parsing into Job Search (requirement 2)

`resume.js:uploadResume()` on success renders the profile, stores
`agentplatform:candidateId`/`agentplatform:candidateName`, and shows a toast — then **stops**.
There is no "Continue to Job Search" CTA anywhere in `resume.html` or `resume.js`
(verified: the only navigation is the static header `<nav>`). The user must manually click **Jobs**.

### 9.5 Free-text "Keywords & Skills" present on both pages (requirements 3 and 8)

- `jobs.html:86-92` → `#jobsKeywordsInput`, consumed by `jobs.js:16, 65, 196-197, 264`.
- `matches.html:89-95` → `#matchesKeywordsInput`, consumed by `matches.js:25, 99, 110, 139, 146`.
- Removing the field from `matches.html` alone is **not** enough: `JobMatchingService.matchJobs()`
  line 91 forwards `request.keywords()` into the job-discovery pre-filter, so with keywords gone the
  discovery step becomes unfiltered. Requirement 5 (profile drives discovery) therefore needs a
  small server-side change: derive discovery keywords from the stored `CandidateProfile`
  (`skills` / `softwareSkills` / `hardwareSkills` / `preferredRoles`) when the caller supplies none.
  `SkillMatchingEngine` already builds exactly such a candidate skill pool and is the natural place
  to reuse.

### 9.6 Mock source is the default production experience (requirements 6 and 7)

See §7. The banner copy "Development Mock Source Active" is hard-coded as the default in three
places: `jobs.html:77` (`#jobsBannerTitle`) / `jobs.js:updateSourceBanner()` (plus the extra toast),
`matches.html:150` (`#matchesBannerTitle`) / `matches.js:renderMatches()`.

### 9.7 Pre-existing, out of the reported scope (flagging, not fixing without approval)

- `applications.js:41-42` binds `#saveEditBtn` / `#cancelEditBtn`, which do **not** exist in
  `applications.html`; both uses are `null`-guarded, so the Applications-page edit mode silently has
  no working Save/Cancel buttons.
- The `modal-open` body class is toggled in 8 places (`careerAgent.js:77,84`,
  `jobDetails.js:116,123`, `matches.js:583,589,691,697`) but **`style.css` contains no `.modal-open`
  rule** (verified: `grep -rn "modal-open" static/` returns only JS hits). Page scrolling is therefore
  never locked while any modal is open. Cosmetic; fix only if you want it.
- `AGENTS.md` is stale in two places: it describes `X-Candidate-Id` as a response header (the real
  `ResumeUploadController` returns `candidateId` in the JSON body) and calls `logging`/`rag-service`
  "empty placeholders" (both have real sources). It also still documents the mock catalog as the only
  job source.
- `MockJobSource` posting dates are `2026-08-2x` while today is 2026-09-09, so `datePosted` filters
  (`24h`/`week`) would now exclude every mock job. Not currently reachable from the UI (no date filter
  is exposed), so no user-visible symptom today.

---

## 10. Build / test verification status

**Could not run `mvn test`.** This sandbox has no JDK and no Maven and no outbound network:

```
$ mvn -v   → /bin/bash: line 1: mvn: command not found
$ java -version → /bin/bash: line 1: java: command not found
$ curl -sSI https://repo.maven.apache.org/maven2/  → curl: (35) OpenSSL SSL_connect: SSL_ERROR_SYSCALL
$ ls /usr/lib/jvm → (empty)
```

So the Java side is **UNVERIFIED by execution** and rests on reading the sources.

What *was* verified by execution this session: the static-asset id cross-reference (§8 / §9.1 / §9.2),
the `grep` counts for `advisorReviewJobTitle`, `advisorReviewCompany`, `jobMatchScore`,
`job-sources.*`, and the `git status` showing no modifications.

### Targeted tests that already exist and would be the regression set

| Area | Existing tests |
|---|---|
| Job search | `ui/.../JobSearchControllerTest` (13 `@Test`, incl. `keywords`/`source`/`location` binding), `orchestrator/.../job/JobSearchServiceWithPublicSourceTest`, `JobSearchServiceQualityTest`, `JobSearchServiceFindByIdTest` |
| Live source | `orchestrator/.../job/PublicApiJobSourceTest` (16 `@Test`), `JobUrlValidatorTest`, `JobNormalizerTest`, `JobDeduplicationServiceTest`, `MockJobSourceTest` |
| Matching | `ui/.../JobMatchControllerTest`, `orchestrator/.../matching/JobMatchingServiceTest` |
| Advisor | `ui/.../ApplicationAdvisorControllerTest` (13 cases), `orchestrator/.../advisor/ApplicationAdvisorServiceTest`, `ApplicationAdvisorResponseTest`, `ApplicationAdvisorRequestTest` |
| Prepared application | `orchestrator/.../application/JobApplicationControllerTest`, `JobApplicationPreparationServiceTest` (6 cases), `ApplicationStorageServiceTest`, `ApplicationPreparationServiceTest` |
| Career agent | `ui/.../OrchestrationControllerTest`, `orchestrator/.../agent/CareerAgentOrchestratorTest`, `OrchestrationRunTrackingTest`, `AgentStatusTest`, `AgentLifecycleLoggingTest`, `EndToEndWorkflowTest` |

74 test files in total. There are **no frontend/JS tests** in the repo, so the HTML/JS defects above
are currently invisible to `mvn test`.

---

## 11. Summary of what is actually broken vs. what is already correct

| # | Item | Status |
|---|---|---|
| 1 | Resume upload + parsing | **Works** — preserve as-is |
| 2 | Resume → Job Search hand-off | **Missing** — no CTA after successful parse |
| 3 | No free-text Keywords on Job Search | **Violated** — `#jobsKeywordsInput` present |
| 4 | Location / Experience / Employment Type only | Fields exist, but alongside keywords |
| 5 | Profile drives skill + job relevance | **Half** — drives *scoring*, not *discovery* |
| 6 | Live/public source as the normal source | **Violated** — `enabled: false`, mock is default |
| 7 | No "Development Mock Source Active" as primary UX | **Violated** — hard-coded default banner + toast |
| 8 | Matches reuses profile intelligence, no redundant keywords | **Violated** — `#matchesKeywordsInput` present |
| 9 | Application Advisor works end-to-end | **Backend OK, frontend broken** (`matches.js:663`) |
| 9 | Prepared Application fields populated | **Broken twice** — modal can't open (§9.1) *and* content ignores the profile (§5) |
| 10 | No auto-submission | **Intact** — approval is a separate explicit endpoint |
| — | Career Agent "stuck at WAITING" | **Frontend rendering artifact**, not a backend bug |

---

## 12. Proposed smallest-change fix set (awaiting approval — nothing implemented)

1. `matches.html` — add the missing `advisorReviewJobTitle` / `advisorReviewCompany` header elements;
   de-duplicate the four advisor ids (keep them on the `<ul>`, drop them from the wrapper `div`);
   restore the missing `<div class="prep-review-overlay" id="prepReviewOverlay" hidden>` wrapper.
2. `matches.js` — null-guard the two new lookups (or populate them from the button's existing
   `data-job-title` / `data-company`), and relax the `data.applicationId != null` gate in
   `openPreparedReview`.
3. `ApplicationAdvisorResponse` — add `jobTitle` / `company` (additive, keeps the 3 existing `of(...)`
   factories compiling), populated in `ApplicationAdvisorService.adviseFromDomain` from the `Job` it
   already has.
4. `JobApplicationPreparationService` / `JobApplicationController` — pass the resolved
   `CandidateProfile` into preparation so the generated summary / cover letter / matching & missing
   skills / highlights come from the parsed resume instead of the hard-coded Java template. No
   schema change: `JobApplication` already persists all of these columns.
5. `application.yml` — flip `job-sources.public-api.enabled` to `true` (env-overridable), and change
   the banner logic in `jobs.js` / `matches.js` so live is the default presentation and mock is the
   clearly-labelled fallback. Keep `MockJobSource` as the offline fallback.
6. `jobs.html` / `matches.html` — remove the Keywords & Skills field; when the caller supplies no
   keywords, derive discovery keywords from the stored `CandidateProfile` in
   `JobMatchingService.matchJobs()` (and equivalently for `/jobs/search` via an optional
   `candidateProfileId`). Scoring weights and formulas untouched.
7. `resume.js` / `resume.html` — add a "Continue to Job Search" CTA on successful parse.
8. `careerAgent.js` — relabel the placeholder state so it no longer reads as a stuck `WAITING`
   (backend `AgentStatus` has no such value), or drop the static progress list. No polling endpoint
   would be added without approval.
