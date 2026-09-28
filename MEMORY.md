# Memory — agent-platform

> **Status**: CURRENT — historical context as of commit bc457e2 (Phase 12.6 implemented — ATS Resume Tailoring preview + PDF/DOCX download UI)
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
| Phase 12.6 | 92c0942, 055db64, bc457e2 | 2026-09-28 | ATS Resume Tailoring — spec (92c0942), backend + tests (055db64), frontend preview + PDF/DOCX download UI (bc457e2); end-to-end browser verification **pending** |

---

## 2. Model Decision History

| Date | Decision | Context |
|------|----------|---------|
| 2026-09-19 | **llama3.2:3b** = current local model | Ollama pulled models: `llama3.2:3b`, `qwen2.5:1.5b`, `nomic-embed-text` |
| 2026-09-19 | **gemma3:4b NOT active** | Was default in config; never pulled; caused 503 on first run |
| 2026-09-19 | **Edge0 NOT in runtime** | Not part of current architecture |
| 2026-09-20 | Timeout increased | `reasoning-timeout: 120s → 600s`; client `150s → 630s` |

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
| Phase 12.6 (ATS Tailoring) | 92c0942, 055db64, bc457e2 | IMPLEMENTED | Backend document generation (PDF/DOCX), controller with 3 endpoints, controller tests, generator tests, frontend preview modal with download buttons |

**Note**: Phase 11.1 MCP is "frozen" in the sense that its **code is complete and tested**; only configuration changes (enable/disable, URLs, timeouts) are allowed. The code itself is not modified.

---

## 4. Previous Resume Parsing Problems & Fixes

| Problem | Root Cause | Fix | Commit |
|---------|------------|-----|--------|
| `gemma3:4b` not found → 503 | Model not pulled; was default in config | Changed default to `llama3.2:3b` | f47562b |
| Cold start timeout (120s) | First inference ~290s on CPU | `reasoning-timeout: 120s → 600s`; client `150s → 630s` | f47562b |
| SVG contamination in contact fields | `resume.js` parsing | Fixed in Phase 12.1 | 553eb76 |
| Career track over-inference | Single keyword → track | Threshold raised (≥3.0 primary, ≥2.0 secondary) | 553eb76 |
| Experience/Projects empty | Section detection failed | Fixed section heading detection | 553eb76 |
| Education pollution (KEY STRENGTHS) | Section stop words missing | Added stop words | f47562b |

---

## 5. Important Environment / Testing Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No JDK in sandbox** | Cannot run `mvn test` in this environment | Run locally |
| **No Maven in sandbox** | Cannot build/test in sandbox | Run locally |
| **No outbound network** | Cannot reach Maven Central, Docker Hub, external Ollama | Run locally |
| **Ollama cold start** | First inference ~290s (model loading) | Pre-warm: `ollama run llama3.2:3b "hi"` |
| **Ollama model loading** | First inference slow while model loads into memory | Keep Ollama running |
| **Resume upload timeout** | 300s client, 600s server — cold start exceeds | Increased to 600s/630s |
| **DOCX test file** | Must be created programmatically (POI) | `CreateTestDocxManual` test |
| **No frontend tests** | 74 backend tests, 0 frontend tests | Manual/Shiplight verification |
| **Spring Boot devtools issue** | Process exits after startup in PowerShell | Run detached: `Start-Process cmd "/c mvn -pl ui spring-boot:run" -WindowStyle Hidden` |

---

## 6. Verified Test Rules (Invariant)

| Rule | Standard |
|------|----------|
| `mvn clean test` | **Must pass**: Failures=0, Errors=0 |
| `git diff --check` | Clean (CRLF warnings OK on Windows) |
| No test weakening | Never weaken, delete, or skip tests |
| Browser verification | Shiplight/Playwright required for frontend flows |
| No commit without verification | Tests + browser check required |

---

## 7. Frozen Architecture Boundaries

| Boundary | Commit | What's Frozen |
|----------|--------|---------------|
| Phase 8 (Logging) | — | LoggingContext, PiiSanitizer, MDC runId |
| Phase 9 | — | — |
| Phase 10 | — | — |
| Phase 11.1 (MCP) | 0d141d6 | OPENINGS-MCP integration; config-only changes |
| Phase 12.1 | 553eb76 | Evidence-based resume parsing, MCP job search |
| Phase 12.6 (ATS Tailoring) | 92c0942, 055db64, bc457e2 | Backend document generation (PDF/DOCX), controller with 3 endpoints, controller tests, generator tests, frontend preview modal with download buttons |

---

## 8. Verified Test Suite (Invariant)

| Module | Tests | Failures | Errors | Skipped |
|--------|-------|----------|--------|---------|
| agent-core | 33 | 0 | 0 | 0 |
| orchestrator | 867 | 0 | 0 | 0 |
| ui | 101 | 0 | 0 | 0 |
| rag-service | 15 (6 skipped) | 0 | 0 | 6 |
| logging | 14 | 0 | 0 | 0 |
| memory-service | 39 (9 skipped) | 0 | 0 | 9 |
| tool-service | 10 | 0 | 0 | 0 |
| **TOTAL** | **~1085** | **0** | **0** | **15** |

---

## 9. Previous Resume Parsing Problems (For Context)

| Problem | Symptom | Root Cause | Resolution |
|---------|---------|------------|------------|
| `gemma3:4b` not found | 503 on first upload | Model not pulled | Default → `llama3.2:3b` |
| Cold start timeout | 120s server timeout | First load ~290s | Timeout → 600s |
| SVG contamination | "svgsami7.dolls@gmail.comsvg..." | Parser artifact | Fixed in 12.1 |
| Career track over-inference | 4 tracks from 1 keyword | Threshold 2.0 | Threshold 3.0/2.0 |
| Experience/Projects empty | Section detection failed | "PROFESSIONAL SUMMARY" matched "PROJECTS" | Heading detection at line start |
| Education pollution | "KEY STRENGTHS" in education | Stop words missing | Added "key strengths", "strengths", etc. |
| Preferred roles over-inference | 4 tracks from mixed skills | Single keyword → track | Threshold 3.0/2.0 |

---

## 10. Phase 12.2 Preparation Checkpoint (f47562b)

| Change | File | Description |
|--------|------|-------------|
| Timeout increase | `application.yml` | `reasoning-timeout: 120s → 600s` |
| Timeout increase | `resume.js` | `CLIENT_TIMEOUT_MS: 150000 → 630000` |
| Model update | `application.yml`, `resume.js`, `OllamaProperties.java`, `OllamaChatModelFactory.java`, `AiErrorClassifier.java` | `gemma3:4b` → `llama3.2:3b` |
| Cold-start comment | `resume.js` | Document timeout rationale |
| `.gitignore` | `.gitignore` | Added `node_modules/` |
| Test cleanup | — | Removed `Samiuddin_IT_B.Tech.docx`, `TestResumeExtractor.java`, `runtime-*.log` |

---

## 11. Verified Test Results (Invariant)

```
mvn clean test → BUILD SUCCESS
Total tests: ~1085
Failures: 0
Errors: 0
Skipped: 15 (pgvector/Testcontainers)
```

```
git diff --check → Clean (CRLF warnings only)
```

---

## 11. Open Issues for Phase 12.2 Implementation

| Issue | Priority | Effort |
|-------|----------|--------|
| Application Advisor modal DOM missing | HIGH | ~2h |
| Prepared Application modal wrapper missing | HIGH | ~1h |
| Career Agent "WAITING" artifact | MEDIUM | ~1h |
| Prepared Application content from profile | HIGH | ~4h |
| Job Search: remove keywords field | MEDIUM | ~2h |
| Live job sources enabled by default | MEDIUM | ~1h |
| Resume → Job Search CTA | LOW | ~1h |
| Career Agent "WAITING" label | LOW | ~1h |

---

**END OF MEMORY.md**