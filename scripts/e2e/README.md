# Browser E2E / diagnostic scripts

Hand-written Playwright scripts that drive the **real** UI and backend (`http://127.0.0.1:8080`).
They were previously authored directly inside `ui/target/`, which meant `mvn clean` destroyed
them and they were never tracked. Cleanup Batch 5 moved them here so they survive and can be
reviewed.

Nothing in this directory is part of `mvn test` — these are manual/live verification scripts.

## Requirements

1. The app must be running and bound to loopback:
   `mvn -pl ui spring-boot:run` (H2 in-memory is enough).
2. Playwright is a **direct** dependency of the repository (`package.json`), so
   `npm install` at the repo root is enough — these scripts `import { chromium } from 'playwright'`.
   Install the browser binaries once with `npx playwright install chromium`.
3. Ollama (or whatever `ollama.chat-model` points at) must be reachable for the resume-upload
   scripts. Phase 11.1 MCP-backed search additionally needs the OPENINGS-MCP server on
   `http://localhost:9000/` (`job-sources.openings-mcp.enabled=true`).

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `E2E_BASE` | `http://127.0.0.1:8080` | App base URL (`e2e-browser.mjs` only; the others hardcode the loopback base) |
| `E2E_RESUME` | the tracked synthetic fixture | Resume file to upload |
| `E2E_CANDIDATE_NAME` | `Jordan Sample` | Candidate name the scripts seed into `localStorage` |
| `E2E_OUT` | `%TEMP%\agent-platform-e2e` | Root output directory (`e2e-browser.mjs` only) |

All evidence — screenshots, JSON reports, captured SSE files — is written under
`os.tmpdir()/agent-platform-e2e/…`, never into the repository.

## The synthetic resume fixture

`orchestrator/src/test/resources/fixtures/sample-resume.docx` — entirely fictional
(candidate, email, phone, employers, university). It replaced a personal CV that used to be
tracked at the repository root and uploaded by these scripts.

Regenerate it after editing `SampleResumeFixture.LINES`:

```
mvn -pl orchestrator -am test-compile -DskipTests
mvn -pl orchestrator dependency:build-classpath "-Dmdep.outputFile=target\fixture-cp.txt"
java -cp "orchestrator\target\test-classes;orchestrator\target\classes;$(Get-Content orchestrator\target\fixture-cp.txt -Raw)" `
     com.agentplatform.orchestrator.resume.SampleResumeFixture orchestrator\src\test\resources\fixtures\sample-resume.docx
mvn -pl orchestrator test -Dtest=SampleResumeFixtureTest
```

## Scripts

| Script | Phase | What it does |
|---|---|---|
| `e2e-browser.mjs` | 12.10 | Full browser E2E regression: resume upload → profile → matches → analysis → tailor → prepare → apply |
| `phase11-mcp-handoff.mjs` | 11.1 / 12.10 | MCP-backed employer handoff through the real UI. `node scripts/e2e/phase11-mcp-handoff.mjs <candidateId>` |
| `e2e-apps-detail.mjs` | 12.8/12.9 | Applications detail modal, kit + timeline surfaces |
| `ui-transitions.mjs` | 12.9 | Status transitions driven through the UI |
| `f5-timeline.mjs` | 12.9 | Event timeline rendering |
| `timeline-probe.mjs` | 12.9 | Timeline DOM probe |
| `ui-pdf-upload.mjs` | — | Live PDF upload (builds a real one-page PDF, uploads it, reports what parsed) |
| `e2e-resume-diag.mjs` | — | Resume upload diagnostics with a timeline of steps |
| `e14.mjs`, `e14-followup.mjs` | 12.9 | 6 pages × 3 widths overflow sweep (D2 gate) |
| `f4-actions.mjs`, `f4-widths.mjs`, `f4-kitstate.mjs`, `f4-doubleclick.mjs` | 12.8 | Apply Kit action/width/state checks |
| `diag-mcp-stages.mjs`, `diag-mcp-stages2.mjs`, `diag-mcp-filter.mjs` | 11.1 | Offline SSE replay of the MCP search pipeline (no browser; read captured SSE from `%TEMP%\agent-platform-e2e\diag*`) |

Run any of them directly with `node`, e.g. `node scripts/e2e/e2e-browser.mjs`.
