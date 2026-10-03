# Memory — agent-platform

> **Status**: CURRENT — historical context as of commit 00079a9 (Phase 12.10 COMPLETE AND VERIFIED; Cleanup Batches 1–3 complete)
> **Purpose**: Historical context, not authority over current rules. See RULES.md for current constraints.

---

## 1. Verified Checkpoints

| Phase | Commit | Date | Description |
|-------|--------|------|-------------|
| Phase 11.1 (MCP) | 0d141d6 | 2026-09-19 | OPENINGS-MCP job source provider with keyword plumbing |
| Phase 12.1 | 553eb76 | 2026-09-19 | Evidence-based resume parsing, MCP job search |
| Phase 12.2 prep | f47562b | 2026-09-20 | Local AI runtime, timeouts, model config |
| Phase 12.3 | a6eaf5c | 2026-09-20 | Job search flow, live job sources |
| Phase 12.4 | da933ac | 2026-09-24 | Match Details — Job Details modal visual polish; live browser verified |
| Phase 12.5 | 6f4b483 | 2026-09-25 | Career Analysis + Readiness UI polish — CSS + markup only (`matches.js`, `style.css`); no spec, no tests; browser E2E B1–B8 passed; full suite 1,304 Java + 24 JS tests green → **VERIFIED** |
| Phase 12.6 | 92c0942, 055db64, bc457e2, 091f7c3, 0a02e22 | 2026-09-28 | ATS Resume Tailoring — spec (92c0942), backend + tests (055db64), frontend preview + PDF/DOCX download UI (bc457e2), docs sync (091f7c3), PDF-determinism/D2 doc follow-up (0a02e22); core workflow + PDF/DOCX read-back verified 2026-09-29; D2 page-level overflow at ≤768px deferred to 12.10 |
| Phase 12.7 | 9009aae, 351283e, 9275582, cceccc3, 6a9f647 | 2026-09-29 | Application Package — prepared-review sections (9009aae); editing flow (351283e, 999 tests + browser checks); email recipient verification + simulated-send labelling (9275582, 1,002 tests + controlled browser checks with route-intercepted mock email transport, no real email sent); docs sync (cceccc3). **VERIFIED 2026-09-29**: H2 long-text `TEXT` persistence fix + `JobApplicationLongTextFieldsPersistenceTest` + two `applications.js` regression fixes (deep-link detail-visibility race, missing detail-view approve handler) — `6a9f647`; full `mvn clean test` 1,216 tests, 0 failures/0 errors/15 skipped; browser acceptance A–P 51/51 checks passed. D2 page-level overflow ≤768px deferred (12.10); live job-source reachability UNVERIFIED; SMTP not configured (simulated sends labelled) |
| Phase 12.8 | d60f556, c758dca, 8718d5c, 8b2e966 | 2026-09-30 | Employer Application — Apply Kit assisted apply. Spec (d60f556); Slice 1 review surface + eligibility in the shared modal shell (c758dca); Slice 2 whitelisted kit values + read-only `GET /api/v1/candidate/{candidateId}` with loopback binding + `@WebMvcTest` coverage (8718d5c, suite 1,216→1,227); Slice 3 final review + manual handoff + client-side apply marker + stale-package re-validation (8b2e966). **VERIFIED 2026-09-30**: full `mvn clean test` 1,227 tests, 0 failures/0 errors/15 skipped; browser acceptance checkpoints A–Q 67/67 checks passed (two consecutive green runs) against route-intercepted fixtures (kit job + advisor-path job with valid `applicationUrl`, canned advisor response, mock/listing-only decline paths, fixture page copy→paste round-trip). Kit lives in `applications.js`; no new static file. New finding: live single-job lookups are flaky on networked runs (advisor `JobNotFoundException` race, repro 14×200/16×404) — backend reliability item for 12.10, not a kit defect. Deferred gates unchanged: D2 page-level overflow ≤768px (12.10), live reachability not claimed, SMTP not configured, no sent-flag, no mailbox-ownership verification, Phase 12.5 verified |
| Phase 12.9 | f21b09c, 45afa38, 28d49aa, 95da0e9 | 2026-09-30 | Application Tracking. Spec (f21b09c); Slice 1 status transitions + employer handoff (`45afa38` — approve/reject idempotent, email-send requires approval + explicit `approved:true`, handoff requires approval and an absolute http(s) URL, illegal transitions 400, optimistic-lock 409); Slice 2 status-filtered list (`28d49aa` — `?status=<ENUM>` on `GET /api/v1/applications/candidate/{id}`, unknown status 400 + defensive reset to All); Slice 3 event timeline + per-status action states (`95da0e9` — timeline rows only from persisted fields; terminal states offer no edit/approve/send). **VERIFIED 2026-09-30**: full `mvn clean test` 1,278 tests, 0 failures/0 errors/15 skipped (agent-core 135, logging 14, memory-service 39, tool-service 10, orchestrator 933, ui 132, rag-service 15); browser acceptance 10/10 API checkpoints + 10/10 UI checkpoints against a deterministic 7-application/2-candidate H2 fixture. `ApplicationEmailService` has a no-arg ctor, so the live wiring has `emailTools = null` and every send returns `SENT_SIMULATED` deterministically (status unchanged, `emailSendResult` persisted) — a real `SENT` is only reachable in unit tests with the tools-wired ctor. 409 evidence is hermetic: `GlobalExceptionHandlerTest.optimisticLockMapsTo409Conflict`. Deferred gates unchanged: D2 page-level overflow ≤768px (51px @768, 429px @390; detail surface + review modal fit) → 12.10, live job-source reachability/advisor intermittency, SMTP unconfigured, employer-site opening is not proof of submission, candidate endpoints unauthenticated/ownership-free (loopback-only), networked deployment deferred, Phase 12.5 verified |
| Phase 12.10 | 1ba94cc | 2026-10-01 | Final Shiplight E2E — full browser E2E regression; **D2 page-level overflow resolved** (the gate deferred through 12.6–12.9); **live job-source reachability verified** (results from ARBEITNOW, REMOTIVE and OPENINGS-MCP with `live: true`); employer handoff recorded server-side as an opening only, never a submission |
| Cleanup Batch 1 | 7fb6e18 | 2026-10-02 | Confirmed dead code removed; advisor score rendering fixed with regression tests |
| Cleanup Batch 2 | 620749c | 2026-10-02 | Repository hygiene — one tracked root `.gitignore`, modernize hook scripts tracked, runtime logs ignored |
| Cleanup Batch 3 | 00079a9 | 2026-10-02 | Maven dependency/config manifest cleanup; dependency tree remained version/scope-identical; `mvn test` 1,301 tests (15 skipped) + 24 JS tests green |
| Cleanup Batch 4 | b45bb4c | 2026-10-02 | Documentation consistency across the nine project documents (docs only, no source/config changes) |
| Cleanup Batch 5 | *(uncommitted)* | — | Synthetic resume fixture (generator + pinned parse test) replaces the personal CV; 17 browser E2E scripts moved from `ui/target/` to tracked `scripts/e2e/` with `os.tmpdir()` output and `E2E_*` overrides; `playwright` declared directly, unused `patchright` removed. Verified: 1,304 Java + 24 JS tests green, real-browser `e2e:browser` B1–B8 all PASS |

---

## 2. Model Decision History

| Date | Decision | Context |
|------|----------|---------|
| 2026-09-19 | **llama3.2:3b** = current local model | Ollama pulled models: `llama3.2:3b`, `qwen2.5:1.5b`, `nomic-embed-text` |
| 2026-09-19 | **gemma3:4b NOT active** | Was default in config; never pulled; caused 503 on first run |
| 2026-09-19 | **Edge0 NOT in runtime** | Not part of current architecture |
| 2026-09-20 | Timeout increased | `reasoning-timeout: 120s → 600s`; client `150s → 630s` (historical — both are now **300s**) |
| 2026-10-02 | Timeout reduced to 300s | Current values: `ollama.reasoning-timeout: 300s` in `application.yml`, `CLIENT_TIMEOUT_MS = 300000` in `resume.js`. `OllamaChatModelFactory` falls back to 2 minutes when the property is unset |
| 2026-10-02 | Gemini deferred | `gemini.enabled=false` by code default and there is **no** `gemini` block in `application.yml`; resume parsing runs on Ollama. The key would come from `GEMINI_API_KEY` via `GeminiProperties` if it were ever enabled |

**Key Decision**: `llama3.2:3b` is the supported local model. `gemma3:4b` was never pulled and caused 503 errors. Do not reintroduce unless explicitly decided.

---

## 3. Frozen Architecture Boundaries

| Boundary | Commit | Status | What's Frozen |
|----------|--------|--------|---------------|
| Phase 8 (Logging) | — | FROZEN | LoggingContext, PiiSanitizer, MDC runId |
| Phase 9 | — | FROZEN | — |
| Phase 10 | — | FROZEN | — |
| Phase 11.1 (MCP) | 0d141d6 | FROZEN | OPENINGS-MCP integration; config-only changes |
| Phase 12.1 | 553eb76 | FROZEN | Evidence-based resume parsing, MCP job search |
| Phase 12.6 (ATS Tailoring) | 92c0942, 055db64, bc457e2, 091f7c3, 0a02e22 | VERIFIED | Backend document generation (PDF/DOCX), controller with 3 endpoints, controller tests, generator tests, frontend preview modal with download buttons; core workflow + PDF/DOCX read-back verified 2026-09-29; D2 page-level overflow resolved in 12.10 |
| Phase 12.10 | 1ba94cc | VERIFIED | Final E2E regression, live job-source reachability confirmed, employer handoff recorded as an opening only |

**Note**: Phase 11.1 MCP is "frozen" in the sense that its **code is complete and tested**; only configuration changes (enable/disable, URLs, timeouts) are allowed. The code itself is not modified.

---

## 4. Previous Resume Parsing Problems & Fixes

> Historical (Phase 12.2, commit `f47562b`). Timeout values shown are what was set at the time —
> both were later reduced to 300s server / 300s client (see §2).

| Problem | Root Cause | Fix | Commit |
|---------|------------|-----|--------|
| `gemma3:4b` not found → 503 | Model not pulled; was default in config | Changed default to `llama3.2:3b` | f47562b |
| Cold start timeout (120s) | First inference ~290s on CPU | Raised to `reasoning-timeout: 600s`; client `150s → 630s` (now 300s / 300s) | f47562b |
| SVG contamination in contact fields | `resume.js` parsing | Fixed in Phase 12.1 | 553eb76 |
| Career track over-inference | Single keyword → track | Threshold raised (≥3.0 primary, ≥2.0 secondary) | 553eb76 |
| Experience/Projects empty | Section detection failed | Fixed section heading detection | 553eb76 |
| Education pollution (KEY STRENGTHS) | Section stop words missing | Added stop words | f47562b |

---

## 5. Important Environment / Testing Limitations

> Rows marked *(historical)* describe the offline sandbox of the Phase 12.2 investigation and are
> kept for context — they no longer describe the current development machine.

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No JDK in sandbox** *(historical)* | Could not run `mvn test` in that environment | Run locally |
| **No Maven in sandbox** *(historical)* | Could not build/test in that sandbox | Run locally |
| **No outbound network** *(historical)* | Could not reach Maven Central, Docker Hub, external Ollama | Run locally |
| **Ollama cold start** | First inference ~290s (model loading) | Pre-warm: `ollama run llama3.2:3b "hi"` |
| **Ollama model loading** | First inference slow while model loads into memory | Keep Ollama running |
| **Resume upload timeout** | 300s client + 300s server; a cold CPU load can exceed it | Pre-warm Ollama; the page offers a cancel button |
| **DOCX test file** | Must be created programmatically (POI) | `CreateTestDocxManual` test |
| **Frontend tests** | 24 JS tests exist in `ui/src/test/js` (`node --test`) — coverage is partial, not exhaustive | Browser verification still required for user flows |
| **Docker unavailable (latest run)** | `init.sql` never applied to a live PostgreSQL/pgvector container; the 15 Testcontainers tests were skipped | App runs on in-memory H2 by default; PostgreSQL remains opt-in |
| **Spring Boot devtools issue** | Process exits after startup in PowerShell | Run detached: `Start-Process cmd "/c mvn -pl ui spring-boot:run" -WindowStyle Hidden` |

---

## 6. Verified Test Rules (Invariant)

| Rule | Standard |
|------|----------|
| `mvn clean test` | **Must pass**: Failures=0, Errors=0 |
| `git diff --check` | Clean (CRLF warnings OK on Windows) |
| No test weakening | Never weaken, delete, or skip tests |
| Browser verification | Shiplight/Playwright required for frontend flows — scripts live in `scripts/e2e/` (`npm run e2e:*`), not in `ui/target/` |
| No commit without verification | Tests + browser check required |

---

---

## 7. Verified Test Suite (Invariant)

Latest verified run — Cleanup Batch 3 (`00079a9`, `mvn test`):

| Module | Tests | Failures | Errors | Skipped |
|--------|-------|----------|--------|---------|
| agent-core | 135 | 0 | 0 | 0 |
| logging | 14 | 0 | 0 | 0 |
| memory-service | 39 | 0 | 0 | 9 |
| tool-service | 10 | 0 | 0 | 0 |
| orchestrator | 959 | 0 | 0 | 0 |
| ui | 132 | 0 | 0 | 0 |
| rag-service | 15 | 0 | 0 | 6 |
| **TOTAL (Java)** | **1,304** | **0** | **0** | **15** |
| **JavaScript** (`node --test`) | **24** | **0** | — | **0** |

> The 15 skipped tests are the Testcontainers PostgreSQL/pgvector tests (9 in memory-service, 6 in
> rag-service) — they require Docker, which was unavailable in the latest verification run.
>
> Prior verified runs: Phase 12.9 (2026-09-30) 1,278 tests; Phase 12.8 (2026-09-30) 1,227 tests;
> Phase 12.7 (2026-09-29) 1,216 tests — each 0 failures, 0 errors, 15 skipped.

---

## 8. Phase 12.2 Preparation Checkpoint (f47562b)

> Historical. The timeout values recorded here (600s server / 630s client) were later reduced to
> 300s / 300s — see §2.

| Change | File | Description |
|--------|------|-------------|
| Timeout increase | `application.yml` | `reasoning-timeout: 120s → 600s` (since reduced to 300s) |
| Timeout increase | `resume.js` | `CLIENT_TIMEOUT_MS: 150000 → 630000` (since reduced to 300000) |
| Model update | `application.yml`, `resume.js`, `OllamaProperties.java`, `OllamaChatModelFactory.java`, `AiErrorClassifier.java` | `gemma3:4b` → `llama3.2:3b` |
| Cold-start comment | `resume.js` | Document timeout rationale |
| `.gitignore` | `.gitignore` | Added `node_modules/` (module-level ignores later removed in Batch 2) |
| Test cleanup | — | Removed `Samiuddin_IT_B.Tech.docx`, `TestResumeExtractor.java`, `runtime-*.log` |
| Batch 5 — synthetic fixture | `orchestrator/.../SampleResumeFixture.java`, `SampleResumeFixtureTest.java`, `orchestrator/src/test/resources/fixtures/sample-resume.docx` | The personal CV above was still tracked in Git despite that earlier note. Replaced by an entirely fictional fixture (Jordan Sample); `SampleResumeFixture` regenerates it, `SampleResumeFixtureTest` pins the parse (888 chars, 11 skills, 5 experience, 1 education/project/certification, 11 evidence rows). Verified over real HTTP and in the real browser |
| Batch 5 — E2E scripts | `scripts/e2e/` (17 scripts + README), `package.json` | Browser scripts were authored inside `ui/target/`, so `mvn clean` destroyed them. Moved to `scripts/e2e/` with `os.tmpdir()` output, the synthetic fixture as the default upload, and `E2E_*` env overrides. `playwright` is now a declared dependency; unused `patchright` removed |

---

## 9. Verified Test Results (Invariant)

```
mvn test → BUILD SUCCESS (Cleanup Batch 5; 1,304 = Batch 3's 1,301 + 3 SampleResumeFixtureTest)
Total tests: 1,304 (0 failures, 0 errors)
Skipped: 15 (Testcontainers PostgreSQL/pgvector — Docker unavailable)
JavaScript: npm run test:js → 24/24 passed
Browser: npm run e2e:browser → B1–B8 all PASS (real UI, real backend, synthetic fixture)
```

Note: `PersistentConversationStoreTest.tearDown` can throw
`TransientObjectException` when its `deleteAll()`/`flush()` ordering lands badly — a pre-existing
flake, unrelated to the fixture or script work. It passed 3/3 in isolation and the full suite is
green; do not "fix" it by weakening the test.

Prior verified runs — Phase 12.9 (2026-09-30): 1,278 tests, 0 failures, 0 errors, 15 skipped;
browser acceptance 10/10 API + 10/10 UI. Phase 12.8 (2026-09-30): 1,227 tests; Phase 12.7
(2026-09-29): 1,216 tests.

```
git diff --check → Clean (CRLF warnings only)
```

---

## 10. Phase 12.2 Issues — Resolution Status

> All resolved; kept for traceability. Current open gaps are tracked in PRD §4.

| Issue | Priority | Effort | Resolution |
|-------|----------|--------|------------|
| Application Advisor modal DOM missing | HIGH | ~2h | **SUPERSEDED** — renders via the shared modal shell |
| Prepared Application modal wrapper missing | HIGH | ~1h | **SUPERSEDED** — obsolete `prepReviewOverlay` |
| Career Agent "WAITING" artifact | MEDIUM | ~1h | **RESOLVED** — progress list removed (`7fb6e18`) |
| Prepared Application content from profile | HIGH | ~4h | **RESOLVED** — profile-derived package |
| Job Search: remove keywords field | MEDIUM | ~2h | **DONE (12.3)** |
| Live job sources enabled by default | MEDIUM | ~1h | **DONE (12.3)** — verified live in 12.10 |
| Resume → Job Search CTA | LOW | ~1h | **SUPERSEDED** — "View My Matches" CTA |

---

**END OF MEMORY.md**