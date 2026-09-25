# PHASE 12.6 SPEC STATUS: READY FOR IMPLEMENTATION

## FULL REVISED SPECIFICATION — Phase 12.6 ATS Resume Tailoring

### 1. Goal

Existing deterministic tailoring analysis + existing tested draft model, taken end-to-end to real files:

- **Preview** — the existing "ATS Resume Tailoring" modal (single global `modalShell`) renders the analysis **and** the draft.
- **PDF download** — `POST /api/v1/resume/tailor/pdf` → real PDF built with PDFBox 3.0.3.
- **DOCX download** — `POST /api/v1/resume/tailor/docx` → real DOCX built with POI 5.2.5.

No invented qualifications at any step. No new dependencies. No new tailoring algorithm. Original `TASKS.md` requirement kept verbatim — PDF and DOCX are real binary products, not TXT/HTML reductions.

### 2. Inputs

| Input | Source | Notes |
|---|---|---|
| `candidateId: Long` | `ApplicationAdvisorRequest` body (reused, `validate()` unchanged) | → `CandidateProfilePersistenceService.getByIdOrThrow()` → `CandidateProfile` (name/email/phone/location used for document header) |
| `jobId: String` (non-blank, ≤200) | same body | → `JobSearchService.findById()` → `Job` |
| `ResumeTailoringAnalysis` | `CareerGapAnalysisService.analyze(profile, job)` then `ResumeTailoringAnalysisService.analyze(profile, job, gap)` — same authoritative gap source as the Application Advisor, so features can never disagree | Also powers the preview (matched/missing, highlighted skills, `atsReadiness`) |
| `TailoredResumeDraft` | `TailoredResumeDraftService.generate(profile, job, analysis)` (Phase 4.5, unchanged) | Single source of truth for document content: `professionalSummary`, `orderedSkills`, `highlightedProjects`, `highlightedExperience`, `highlightedInternships`, `sectionOrder`, `origin`, `warnings` |

Only the document-generation step and its wiring are new. Generators consume the draft; **no tailoring logic is duplicated**.

### 3. Preview requirements

The existing tailoring modal (`matches.js:738`, global `modalShell`) shows at minimum:

- Tailored professional summary (`draft.professionalSummary`)
- Recommended section order (`analysis.recommendedSectionOrder` + `draft.sectionOrder`, filtered to content-bearing sections)
- Ordered skills (`draft.orderedSkills`)
- Highlighted skills (`analysis.highlightedSkills` — already rendered)
- Highlighted projects / experience / internships (`draft.highlightedProjects|Experience|Internships`, rendered only when non-empty)
- `draft.warnings` as the bottom safety note

No second modal architecture. One handler, one dialog; content upgraded from `buildTailoringHtml(analysis)` to a wrapper rendering both. `modalShell.setFooter(...)` (modalShell.js:137) adds the two download buttons after the JSON resolves.

### 4. PDF/DOCX requirements (backend, exact)

**Dependency status (recorded from repository; nothing added):**
- `org.apache.pdfbox:pdfbox:3.0.3` — root `pom.xml:111`, compile scope in `orchestrator/pom.xml:66`. Generation-capable in 3.0.3.
- `org.apache.poi:poi-ooxml:5.2.5` — root `pom.xml:118`, compile scope in `orchestrator/pom.xml:73`. Generation-capable (`XWPFDocument` writes as well as reads).

**PDFBox 3.x font API — exact (issue 1 resolved):** PDFBox 3.x **removed the static `PDType1Font.HELVETICA` constants**; the Standard-14 construction is `new PDType1Font(Standard14Fonts.FontName.HELVETICA)`. This repository already demonstrates the exact 3.x usage in `ResumeParserServiceTest.java:53`. The implementation must use precisely:

```java
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
...
cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
```

No other font-class formulation is permitted in the specification. (`PDType1Font` is used only wrapped as `new PDType1Font(Standard14Fonts.FontName.HELVETICA)` — the single documented Helvetica path for 3.0.3.)

**New components — `orchestrator/src/main/java/com/agentplatform/orchestrator/document/`:**
1. `GeneratedResumeDocument` (record): `(byte[] bytes, String contentType, String filename)`.
2. `ResumeDocumentGenerator` (interface): `GeneratedResumeDocument generate(TailoredResumeDraft draft, CandidateProfile profile, Job job)`.
3. `PdfResumeDocumentGenerator` (`@Component`): `new PDDocument()` → pages via `PDPage`/`PDPageContentStream`; header (name · email · phone · location, non-blank parts only), professional summary paragraph, then each content-bearing section per `draft.sectionOrder` (Skills/Experience/Internships/Projects/Certifications/Education) as heading + bulleted lines, `draft.warnings` footer; `doc.save(out)` → bytes.
   - `ponytail:` naive max-width line split for text layout; extra pages for overflow. Helvetica (Standard-14) is ASCII-safe; CJK/special glyphs are out of scope for the current parse pipeline — noted with upgrade path (load a real font) in the impl comment.
   - No timestamps are written → **deterministic PDF bytes**.
4. `DocxResumeDocumentGenerator` (`@Component`): `new XWPFDocument()` → same layout via `XWPFParagraph`/`XWPFRun` (headings bold, spacing), `draft.warnings` footer; write via `ByteArrayOutputStream`.
   - POI embeds `docProps/core.xml` creation metadata → **DOCX bytes are NOT byte-deterministic** (content identical, timestamps differ). Explicitly documented, not tested.

Both reuse `TailoredResumeDraft` only; `DraftOrigin.DETERMINISTIC` preserved.

**MIME types:** PDF `application/pdf`; DOCX `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (same constant already used in `ResumeParserService:30` and `ResumeUploadController:69`).

**Content-Disposition / filename:** `attachment; filename="{slug}-tailored-resume.pdf|.docx"`. Safe filename = lowercase ASCII slug of the job title (`[^a-z0-9]+` → `-`, trimmed of leading/trailing `-`, capped ≈60 chars); fallback `tailored-resume` when blank. No user-controlled path characters.

**No persistence.** Documents are built per-request from a freshly recomputed pipeline and discarded after the response. Nothing is stored server-side.

### 5. HTTP API

Three endpoints, one shared private resolution helper in `ResumeTailoringController` (extract the current resolve block `tailor()` lines ~76-86 into `private TailoredResumeDraft resolveAndBuild(ApplicationAdvisorRequest)` so the analysis→draft path is literally the same code for JSON/PDF/DOCX):

1. `POST /api/v1/resume/tailor` → `200 application/json`, body = `ResumeTailoringResponse{analysis, draft}`.
2. `POST /api/v1/resume/tailor/pdf` → `200 application/pdf`, `Content-Disposition: attachment; filename="{slug}-tailored-resume.pdf"`, non-empty body.
3. `POST /api/v1/resume/tailor/docx` → `200 application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `Content-Disposition: attachment; filename="{slug}-tailored-resume.docx"`, non-empty body.

All three take the identical `ApplicationAdvisorRequest{candidateId, jobId}` + `validate()` body. Errors unchanged (RFC 7807 via `GlobalExceptionHandler`): 404 candidate (`CandidateProfileNotFoundException`), 404 job (`JobNotFoundException`), 400 invalid, 500 safe-generic on unexpected.

**Contract wording — response-shape change (issue 2 resolved, option B verified):**

> **response-shape change from top-level `ResumeTailoringAnalysis` to `{analysis, draft}`; all known consumers will be updated/verified in the same phase.**

Consumer audit (repository-wide):
- `POST /api/v1/resume/tailor` — sole HTTP consumer is `matches.js:745` (`fetch`), rendered by `buildTailoringHtml(data)` at `matches.js:757,838`. No `ResumeTailoringControllerTest` exists today.
- `ResumeTailoringAnalysis` — referenced only Java-side: `ui/.../ResumeTailoringController.java` (compile-time, updated in-phase) and orchestrator services/tests (`ResumeTailoringAnalysisServiceTest`, `TailoredResumeDraftServiceTest`, `ApplicationAdvisorServiceTest`, agent tests) — all construct/consume the record directly; none touches the JSON shape.
- `resume-tailor` / `buildTailoringHtml` — only `matches.js` (lines 385, 728, 745, 757, 838, 1199) plus CSS classes in `style.css:979-990` (unaffected).
- No other frontend file, no other controller, no external consumer references the endpoint.

Conclusion: **no compatibility hazard.** The single in-repo consumer (`matches.js`) is updated in the same phase and made tolerant (`data.analysis ?? data`), so a stale cached page degrades to today's analysis-only render rather than breaking. Three-endpoint design retained.

### 6. Controller tests

New `ui/src/test/java/com/agentplatform/ui/controller/ResumeTailoringControllerTest.java` — `@WebMvcTest(controllers = {ResumeTailoringController.class, GlobalExceptionHandler.class})`, `@MockBean` all six collaborators, cloned from the `ApplicationAdvisorControllerTest` pattern (257 lines).

Required cases:
1. Valid request → 200 JSON, `{analysis, draft}` present (see §7 exact JSONPaths)
2. Unknown candidate → 404 `Candidate Profile Not Found` (RFC 7807)
3. Unknown job → 404 `Job Not Found`
4. Invalid request: missing/null candidateId, negative candidateId, blank jobId, oversized jobId (>200), malformed JSON → 400 `Bad Request`
5. **PDF success** → 200, `contentType == application/pdf`, `Content-Disposition` attachment with `.pdf` + `tailored-resume`, `getContentAsByteArray().length > 0`
6. **DOCX success** → 200, `contentType == application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `Content-Disposition` `.docx`, non-empty body
7. **PDF generator throws → safe 500** (issue 4): `PdfResumeDocumentGenerator.generate(...)` throws → `500`, `$.title == "Internal Server Error"`, generic detail, and the raw body contains **no** exception message/class name/stack trace (` at `)
8. **DOCX generator throws → safe 500** (issue 4): same assertion for the DOCX path
9. Unexpected service error → 500 safe-generic, no internals leaked
10. Malformed/unexpected error detail hygiene (mirror `advise_unexpectedError_returns500Safe` exact assertions: no secret text, no `IllegalStateException`, no ` at `)

### 7. Exact JSONPath assertions (issue 5 resolved)

Actual `ResumeTailoringAnalysis` field names (from `orchestrator/.../tailoring/ResumeTailoringAnalysis.java`) — **note:** `AtsReadinessAnalysis` carries the score; there is **no** `recommendation`, `score`, `jobTitle`, or `company` field on this record (those belong to the advisor/job-match DTOs). Assertions use the real record components:

```jsonc
// analysis — exact paths
$.analysis.candidateId        == 1
$.analysis.jobId              == "job-1"
$.analysis.matchedRequiredSkills[0]   // non-empty for the fixture
$.analysis.missingRequiredSkills       // array present
$.analysis.highlightedSkills[0].canonicalSkill  // string
$.analysis.recommendedSectionOrder[0]  // e.g. "SUMMARY"
$.analysis.atsReadiness.score          // number
$.analysis.atsReadiness.label          // string
```

Draft assertions (actual `TailoredResumeDraft` components, confirmed from `orchestrator/.../tailoring/TailoredResumeDraft.java`):

```jsonc
$.draft.professionalSummary        // non-blank string
$.draft.orderedSkills              // array, first element string
$.draft.sectionOrder               // array of ResumeSection enums
$.draft.highlightedProjects        // array
$.draft.highlightedExperience      // array
$.draft.highlightedInternships     // array
$.draft.origin                     == "DETERMINISTIC"     // REQUIRED assertion
$.draft.warnings                   // array, non-empty
```

### 8. Safety requirements

Unchanged and enforced throughout: reorder/rephrase existing content only; never invent skills/employers/projects/certifications/education/years; missing requirements surface only in `missingRequirements`/`warnings`, never in the draft or documents; `DraftOrigin.DETERMINISTIC`; no auto-apply, no auto-email, no automatic submission, no persistence. `RULES.md:36,71` / `PRD.md:44` hold.

### 9. UI action placement

Keep the exact two existing entry points — match card (`matches.js:385`) and Match Details footer (`matches.js:1199`) — both through the single delegated handler (`matches.js:727-766`). **No** new Tailor Resume button in Career Analysis (`careerAgent.js` untouched) or elsewhere.

### 10. Recompute semantics (issue 3 resolved — explicit)

- **PDF download recomputes the deterministic tailoring pipeline for that request** — new `CandidateProfile`→`Job`→gap→analysis→draft per request.
- **DOCX download recomputes the deterministic tailoring pipeline for that request** — same, independently.
- **No caching and no persistence occur** — the draft is never stored between preview and download; each of the three endpoints is self-contained and stateless.
- Because the pipeline is fully deterministic (`TailoredResumeDraftService` and `ResumeTailoringAnalysisService` are pure, no timestamps/randomness/network), equivalent inputs yield equivalent content across preview, PDF, and DOCX. The spec never implies the draft is retained between requests.

### 11. Browser checkpoints (unchanged from approved set)

A visible button (card + footer) · B 200 on tailoring · C preview modal renders analysis + draft · D section-order preview non-empty · E highlighted skills/projects render · F PDF download file exists, non-empty · G DOCX download exists, non-empty · H downloaded PDF (read back via PDFBox `PDFTextStripper`) and DOCX (read back via POI `XWPFDocument`) contain candidate name + summary phrase + ≥1 ordered skill + warnings footer · I close/reopen clean · J repeated opens (3×) leave no stale body/footer/duplicate listeners · K 1440px · L 768px · M 390px (no horizontal overflow) · N console errors = 0 (happy path) · O failed network requests = 0 · P no `/api/v1/applications`/email endpoint hit.

### 12. Tests (full gate)

- `ResumeTailoringAnalysisServiceTest` — **unchanged** ✓
- `TailoredResumeDraftServiceTest` — **unchanged** ✓
- `ResumeTailoringControllerTest` — **added** (§6/§7)
- `PdfResumeDocumentGeneratorTest` — **added**: non-empty bytes; starts `%PDF-`; re-read via `PDFTextStripper` and assert name/summary/skill/section/warnings present; **byte-determinism**: two `generate()` calls produce identical `byte[]`
- `DocxResumeDocumentGeneratorTest` — **added**: non-empty bytes; starts `PK`; re-read via `XWPFDocument` and assert summary/sections/content present; **byte-equality explicitly NOT asserted**
- Null/empty inputs → valid empty document or safe default, no throw
- Full `mvn clean test` green; `git diff --check` clean; no existing test weakened/skipped

### 13. Non-goals

No new tailoring algorithm; no ATS-scoring change; no career-analysis-scoring change; no LLM-provider change; no job-search change; no new frontend framework; no new modal architecture; no DB persistence; no auto-apply/email/submission; no D2 mobile-header fix; no unrelated `JobApplication` sibling-column hardening; no TXT/HTML reduction of PDF/DOCX; no new dependencies.

### 14. Commits (proposed, NOT executed)

1. **Backend + tests** — `orchestrator/document/` (record, interface, PDF impl, DOCX impl) + `ResumeTailoringResponse` + controller (shared resolve helper, 2 new endpoints, wrapper return) + `ResumeTailoringControllerTest` + both generator tests. → `Phase 12.6 - tailored resume PDF/DOCX document generation + controller tests`
2. **Frontend** — `matches.js`: `data.analysis ?? data` unwrap, draft-preview rendering, download handler (POST → blob → object URL → `<a download>` → revoke), `modalShell.setFooter` buttons; `style.css` only if existing `.btn-*`/`.tailoring-item` classes don't cover it. → `Phase 12.6 - tailored resume preview + PDF/DOCX download UI`
3. **Docs sync** — `TASKS.md`, `DESIGN.md`, `ARCHITECTURE.md`, `PRD.md`. → `Phase 12.6 - sync docs`

### 15. Architectural risk (low, acknowledged)

- PDFBox text layout is a naive width-split; ASCII-safe; page-overflow handled by extra pages; CJK glyphs out of scope (upgrade path noted).
- POI DOCX embeds core-property timestamps → content-identical but not byte-identical across runs; only PDF byte-determinism is tested.
- JSON contract is a response-shape change, not additive; the only consumer (`matches.js`) is updated in-phase with a `data.analysis ?? data` guard for stale caches.

---

## Resolved issues

1. **PDFBox API** — Resolved. PDFBox 3.0.3 uses `new PDType1Font(Standard14Fonts.FontName.HELVETICA)` (static `PDType1Font.HELVETICA` does not exist in 3.x); exact imports specified, and this repo's own `ResumeParserServiceTest.java:53` already proves the pattern. Single formulation, no alternatives.
2. **`/tailor` response contract** — Resolved (option B). Full-repo audit found the sole external consumer is `matches.js:745`/`838`; no controller test or secondary consumer exists. Wording corrected to **"response-shape change … all known consumers updated/verified in the same phase"**, with an in-phase `data.analysis ?? data` guard. No unsafe consumer discovered.
3. **Recompute semantics** — Resolved. PDF and DOCX each independently recompute the full deterministic pipeline per request; no caching or persistence; determinism guarantees equivalent content for equivalent inputs; no draft storage implied.
4. **Binary failure handling** — Resolved. Explicit controller-test cases for PDF-generator-throws and DOCX-generator-throws → safe HTTP 500 RFC 7807, verified with the no-stacktrace/no-internals assertions used by `advise_unexpectedError_returns500Safe`.
5. **JSONPath assertions** — Resolved. Exact paths against the real record components, including the required `$.draft.origin == "DETERMINISTIC"`. Corrected the spec runner's examples: `ResumeTailoringAnalysis` has no `recommendation`/`score`/`jobTitle`/`company` (those live on the advisor/job-match DTOs); score is under `$.analysis.atsReadiness.score`.
6. **Response-record location** — Resolved. Repository evidence: HTTP/API-boundary response records live in `ui/.../ui/dto/` (`ChatResponse`, `AgentProcessResponse`, `JobMatchResponseDto` — note `JobMatchResponseDto` actually resides in `ui/dto`, not orchestrator); service-owned responses live beside their service (`ApplicationAdvisorResponse` in `orchestrator/advisor`, `AiStatusResponse` in `agent-core/ai`). `ResumeTailoringResponse` is a transport-only composite, so it follows the **`ui/src/main/java/com/agentplatform/ui/dto/ResumeTailoringResponse.java`** convention (the user-facing API-shape home next to the UI controller boundary) — matching the HTTP-layer-wrapper rationale.