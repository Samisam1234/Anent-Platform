AI Career Agent Platform

An evidence-first AI career platform built with Java and Spring Boot that takes a candidate from resume intelligence to job discovery, deterministic matching, career-gap analysis, ATS guidance, application preparation, and user-controlled application actions.

Overview

The AI Career Agent Platform is a multi-module Java/Spring Boot application designed to make career assistance more structured, explainable, and safer than a free-form AI chatbot.

The core design principle is deterministic-first + bounded AI assistance:

Resume facts are extracted and represented as structured candidate data.

Job records are normalized and deduplicated before downstream processing.

Matching, career-gap analysis, readiness calculations, safety gates, and approval decisions are driven by application logic.

LLMs are used for constrained reasoning, explanations, and controlled text generation—not as the authority for candidate facts or match scores.

The platform does not autonomously mass-apply to jobs or send emails without explicit user approval.

The project evolved from a resume/job-search application into a multi-module, multi-agent platform with configurable AI providers, tool orchestration, application lifecycle handling, Openings-MCP job discovery, browser workflows, and provider failover/cooldown behavior.

Product Workflow

Resume Upload
    ↓
Resume Parsing (PDF/DOCX)
    ↓
Structured Candidate Profile
    ↓
Career Track + Skills Evidence
    ↓
Job Discovery (public/live adapters + Openings-MCP + mock test source)
    ↓
Normalization + Deduplication + Filters
    ↓
Deterministic Job Matching
    ↓
Career Gap Analysis
    ↓
Improvement Priorities
    ↓
ATS Readiness + Resume Tailoring
    ↓
Application Preparation
    ↓
Review / Edit
    ↓
Explicit User Approval
    ├──→ Approved Email Action
    └──→ Employer/ATS Handoff

The platform is intentionally user-controlled at the point where external action would occur.

Key Capabilities

Resume Intelligence

PDF and DOCX resume upload.

Apache PDFBox and Apache POI based document extraction.

Structured candidate profile generation and persistence.

Deterministic candidate-profile construction and correction logic.

Resume evidence is kept separate from unsupported model-generated claims.

Software, hardware/ECE, VLSI/FPGA, embedded, and AI/ML career tracks can be represented when supported by resume evidence.

Job Discovery

JobSource abstraction hides external job systems behind stable application contracts.

MockJobSource remains available for tests and development.

Public/live source adapters are supported when configured.

Openings-MCP is integrated as a project-associated job-discovery source.

Job records are normalized and deduplicated before downstream use.

Search can derive job-oriented keywords from candidate profile evidence.

Search flow is centered on practical filters such as location, experience, and employment type.

Source provenance is preserved rather than fabricating job facts or URLs.

Deterministic Matching

The matching pipeline combines candidate evidence and job requirements using application logic for dimensions such as:

skills

role alignment

experience

career track

location

education

The resulting score and explanation are deterministic. LLM output does not replace the scoring engine.

Career-Gap Analysis

The project includes deterministic gap analysis covering required skills, preferred skills, experience shortfalls, and track mismatch.

Recorded scoring rules:

Evidence

Points

Missing required skill

+3

Missing preferred skill

+1

Knowable experience shortfall

+2

Career-track mismatch

+2

Recorded severity bands:

Score

Severity

0

NONE

1–3

LOW

4–6

MEDIUM

7–9

HIGH

10+

CRITICAL

Experience is scored only when the relevant experience expressions can be parsed under the supported explicit formats; unknown values are not guessed.

Improvement Planning

ImprovementPriority and CareerImprovementPlan convert detected gaps into ordered, actionable improvement items.

The plan can include an AI-generated explanation, but deterministic priorities remain authoritative and unsupported credentials/skills are not added.

ATS Readiness and Safe Tailoring

The project implements deterministic ATS-oriented analysis and safe tailoring models including:

AtsReadinessAnalysis

ResumeTailoringAnalysis

TailoredResumeDraft

DraftOrigin

The recorded ATS readiness formula is:

ATS readiness = round(

60 × required-skill coverage

+20 × preferred-skill coverage

+10 × project evidence

+10 × experience evidence
)

This is an internal readiness measure, not a guarantee of passing an employer's ATS.

Tailoring does not mutate the source resume or insert unsupported facts. Missing requirements become warnings rather than fabricated experience.

Application Preparation and User Control

The application workflow separates preparation from external action.

Typical states include:

DRAFT_ONLY
    ↓
REVIEW_REQUIRED
    ↓
User review/edit
    ↓
Explicit approval
    ↓
Validated external action / handoff

Important safety rules:

No automatic mass application.

No automatic email sending.

approved=false blocks email sending.

Approval to prepare an application is not proof that an employer submission occurred.

Employer/ATS URLs must be genuine; URLs must never be invented.

Mock jobs must not behave like real employer destinations.

Multi-Agent Orchestration

The platform contains a fixed five-agent logical workflow:

Resume

Job Discovery

Matching

Career Advisor

Application Advisor

The project uses typed contracts such as AgentType, AgentStatus, AgentRequest, AgentResult, AgentContext, CareerAgent, and OrchestrationResult.

Recorded orchestration constraints include:

Maximum 5 agents per orchestration.

Resume and Job Discovery are blocking agents.

Matching, Career Advisor, and Application Advisor are optional later stages.

Maximum 3 AI calls per orchestration context.

Maximum 2 tool calls per agent.

Maximum 4 tool calls per orchestration.

Run-local memory stores up to 5 AgentResult values.

Run-local messages are limited to 512 characters.

Failures resolve to deterministic fallback behavior or classified errors rather than fabricated success.

Tool Orchestration

Tool access is policy-controlled rather than globally open.

Named tool infrastructure includes:

ToolMethod

ToolRegistry

DefaultToolExecutor

AgentToolPolicy

AgentToolRequest

AgentToolResult

AgentToolOrchestrator

EmailTools

ImageTools

WhatsAppTools

The recorded policy allows image-generation requests for the Resume agent while restricting other agent tool access; email sending is separately denied to agents and guarded by user approval.

AI / LLM Architecture

The project was deliberately moved away from an Ollama-centric model creation path toward a provider-neutral architecture.

Provider Abstraction

LlmProvider
    ↓
LlmProviderRouter
    ├── OllamaProvider
    ├── GeminiProvider
    ├── GroqProvider
    ├── OpenRouterProvider
    ├── CerebrasProvider
    └── Cloudflare AI Gateway provider

Named provider/configuration types include:

LlmProvider, LlmProviderRouter, LlmProperties, OllamaProvider, GeminiProvider, GroqProvider, GroqProperties, OpenRouterProvider, OpenRouterProperties, OpenRouterConfig, CerebrasProvider, CerebrasProperties, CerebrasConfig, CloudflareAiGatewayProvider, CloudflareAiGatewayProperties, CloudflareAiGatewayConfig, CloudflareCompatibleChatModel, and AiErrorClassifier.

Provider Priority History

The recorded Phase 13 architecture assigned:

Priority

Provider

10

Ollama

20

Gemini

30

Groq

40

OpenRouter

50

Cerebras

60

Cloudflare

Automatic provider selection uses configured-default-first behavior, provider priority, failover, and provider cooldown state.

The provider-specific request normalization is isolated behind provider adapters, including a Cloudflare-specific compatibility wrapper.

Cloud API Usage

The current development direction uses cloud providers through API keys. The Ollama provider remains in the architecture for compatibility/history, but local chat models were removed after local benchmarking. Real credentials are supplied through environment variables rather than being stored in application data.

No API key values are included in this repository documentation.

Deterministic AI Boundary

A central architectural rule is:

+-----------------------+
|     DETERMINISTIC     |
+-----------------------+
| Candidate facts       |
| Job facts             |
| Safety gates          |
| Job matching          |
| Gap scoring           |
| Approval state        |
| Readiness             |
| Source data           |
| Application status    |
+-----------------------+
            ↓
+-----------------------+
| BOUNDED LLM ASSISTANCE|
+-----------------------+
| Explanations          |
| Constrained text      |
+-----------------------+
            ↓
+-----------------------+
| Validation / Fallback |
+-----------------------+

This prevents model output from silently becoming authoritative business data.

Maven Module Architecture

agent-platform (root)
├── agent-core
│   └── Shared domain contracts, agent/tool abstractions,
│       LLM provider contracts, router, and core configuration
├── logging
│   └── Logging / observability support
├── memory-service
│   └── Conversation/run memory and persistence
├── tool-service
│   └── Tool registry, execution, and integrations
├── orchestrator
│   └── Job search, matching, career analysis, tailoring,
│       application preparation, agents, and orchestration
├── ui
│   └── Spring Boot web layer, REST-facing controllers/config,
│       H2/PostgreSQL runtime wiring, and static frontend
└── rag-service
    └── Retained placeholder module; no new RAG/embedding
        functionality is assumed without a scope decision

Recorded dependency shape:

ui
 ↓
orchestrator
 ├── agent-core
 ├── memory-service
 ├── tool-service
 └── logging

rag-service ──→ memory-service

Layered Architecture

Presentation

HTML, CSS, and Vanilla JavaScript pages, forms, dialogs, modals, status indicators, and user interaction.

HTTP / API

Spring MVC controllers, DTOs, validation, response shaping, and safe RFC 7807 ProblemDetail errors.

Application / Services

Resume parsing, profile construction, job search, matching, career analysis, tailoring, application preparation, email, reasoning, and orchestration.

Domain

Candidate profiles, jobs, application states, match models, gap models, tailoring models, agent contracts, run status, and execution records.

Infrastructure

Spring Data JPA, Hibernate, H2, optional PostgreSQL/pgvector, document parsers, job-source adapters, MCP integrations, tool integrations, and LLM provider adapters.

Verification

JUnit/Mockito unit tests, controller/contract tests, orchestration tests, integration tests, regression tests, browser checks, Maven reactor runs, manual API smoke checks, and Git/diff validation.

Java Engineering Patterns Used

The project is deliberately conventional Java/Spring engineering rather than an LLM-only prototype.

Spring MVC REST controllers with thin entry points.

Application services for business orchestration.

Typed domain models/entities with JPA/Hibernate repositories.

Stable interfaces and contracts such as JobSource, LlmProvider, CareerAgent, and ToolMethod.

Enums and workflow state machines such as AgentType, AgentStatus, RunStatus, ApplicationStatus, ApplicationDraftStatus, and DraftOrigin.

Records / typed DTO-style request and result structures.

Strategy / adapter / router patterns for external providers.

Configuration-driven Spring beans using @Configuration, @Bean, @ConfigurationProperties, and @ConditionalOnProperty.

Java concurrency primitives in provider cooldown state, including ConcurrentHashMap, AtomicInteger, volatile, and injected Clock.

Error classification through AiErrorClassifier and global exception handling.

Browser fetch, DOM updates, safe text rendering, in-flight guards, duplicate-click protection, and guarded reruns.

Named Java Type Inventory

The project history contains an explicit catalog of 67 named production Java types at an earlier documented snapshot. That is not a certified current repository-wide class count; the current checkout contains additional supporting types and tests.

Representative named types preserved in the engineering record:

Resume / Profile

ResumeUploadController, ResumeParserService, ResumeProfileService, CandidateProfile, DeterministicCandidateProfileBuilder

Job Discovery

JobSource, MockJobSource, PublicApiJobSource, OpeningsMcpJobSourceProvider, JobDeduplicationService, JobSearchService, JobNotFoundException, Job, PublicApiJobConfig, OpeningsMcpJobProperties

Matching / Career Tracks

SkillMatchingEngine, SkillTaxonomy, CareerTrackEngine, JobMatchController, JobDetailsController

Career Gap / Improvement

CareerGapAnalysis, ExperienceGap, ImprovementPriority, CareerImprovementPlan, CareerImprovementPlanService

ATS / Tailoring

AtsReadinessAnalysis, ResumeTailoringAnalysis, TailoredResumeDraft, DraftOrigin, ResumeTailoringController

Application / Email

ApplicationEmailDraft, ApplicationDraftStatus, ApplicationPreparationService, ApplicationEmailService, ApplicationEmailController, ApplicationAdvisorService, ApplicationAdvisorResponse, ApplicationStatus, ApplicationStorageService, JobApplicationPreparationService

Agent Contracts / Orchestration

AgentType, AgentStatus, AgentRequest, AgentResult, AgentContext, CareerAgent, OrchestrationResult, CareerAgentOrchestrator, AgentReasoningService, AgentReasoningResult, Explanation, AgentChatService, AiStatusService, AgentToolPolicy, AgentToolRequest, AgentToolResult, AgentToolOrchestrator, RunStatus, OrchestrationRun, AgentExecution, OrchestrationController, OrchestrationRequestDto, GlobalExceptionHandler

Tools

ToolMethod, ToolRegistry, DefaultToolExecutor, EmailTools, ImageTools, WhatsAppTools

LLM / Provider Types

LlmProvider, LlmProviderRouter, LlmProperties, OllamaChatModelFactory, AiErrorClassifier, OllamaProvider, GeminiProvider, OllamaConfig, GeminiConfig, GroqProperties, GroqProvider, OpenRouterConfig, OpenRouterProperties, OpenRouterProvider, CerebrasConfig, CerebrasProperties, CerebrasProvider, CloudflareAiGatewayConfig, CloudflareAiGatewayProperties, CloudflareAiGatewayProvider, CloudflareCompatibleChatModel

The five logical agents are also recorded as Resume, Job Discovery, Matching, Career Advisor, and Application Advisor. The exact concrete Java class name for every logical agent is not reproduced here where the project-history record did not preserve it completely.

REST API Surface

The project history records these important API paths:

Endpoint

Purpose

POST /api/v1/resume/upload

Upload PDF/DOCX resume, parse it, build/persist candidate profile, and return candidate/profile data

/api/v1/jobs/search

Job discovery through JobSearchService and configured source adapters

/api/v1/jobs/match

Candidate/job matching workflow used in later verification

POST /api/v1/agent/orchestrate

Execute the bounded five-agent career workflow

POST /api/v1/applications/email/send

Validated email action guarded by explicit approval

GET /api/v1/ai/status

AI/provider configuration and runtime status

POST /api/v1/agent/chat

Custom AI/chat request path with classified failures

Some exact controller HTTP methods/URI details were not preserved in the chat-derived history; the current source annotations are the authoritative API specification.

Frontend

The frontend intentionally uses HTML + CSS + Vanilla JavaScript.

Recorded pages/scripts include:

index.html
resume.html / resume.js
jobs.html / jobs.js
jobDetails.js
matches.html / matches.js
careerAgent.js
applications.html + application scripts
custom.html / custom.js
style.css
application.yml

Important frontend engineering details include:

browser fetch() API calls

DOM updates instead of a component framework

modal/overlay state management

validation and loading/error/empty states

safe text rendering

in-flight guards

duplicate-click protection

guarded reruns

Escape/overlay close behavior where implemented

explicit application-review and approval surfaces

The project deliberately avoids React, Next.js, TypeScript, Tailwind, and unrelated frontend rewrites.

MCP Integration — Project-Associated Local Openings-MCP

The platform integrates with a project-associated Openings-MCP server that was run locally during verification. This should not be described as an unrelated third-party MCP dependency.

Recorded server/runtime details:

Executable location used during verification: E:\tools\openings-mcp

Local HTTP endpoint: http://127.0.0.1:9000/ / http://localhost:9000/

Base endpoint is /, not /mcp.

Recorded server version: 0.16.2

Recorded server commit: 3726ec09

tools/list exposed 35 tools.

tools/call was directly verified.

google_search_jobs was directly verified.

The tool inventory included Google, Amazon, Apple, and Meta job-search/detail tools.

The server binary/source was not committed into the repository.

The application-side OpeningsMcpJobSourceProvider:

calls MCP through streamable HTTP

uses JSON-RPC tools/call

accepts JSON/SSE responses

parses structured MCP data

invokes multiple relevant tools in parallel

isolates individual tool failures

validates returned job URLs

preserves provenance

uses official employer/application URLs where appropriate

does not fabricate URLs or job information

The provider also supports identifier variations such as id / job_id and company / company_name, search-array responses and flat detail structuredContent, and optional detail apply_url enrichment.

A bounded process-local JobIdCache provides cache-first exact-ID reuse for final normalized search results.

First-party host allowlisting was recorded for:

Amazon: www.amazon.jobs, account.amazon.jobs

Apple: jobs.apple.com

Google: www.google.com, careers.google.com

Meta: www.metacareers.com

Aggregator URLs such as LinkedIn remain source provenance rather than being treated as approved employer application destinations.

Safety and Reliability Model

Evidence-First Rule

Never fabricate:

candidate skills

employment history

employers

dates

achievements

job requirements

job identifiers

source URLs

application URLs

successful application outcomes

Approval Gate

Preparation and external execution are intentionally separate.

Provider Failure Behavior

An unavailable provider should result in deterministic fallback where designed or a safe classified error—not a fake success.

Tool Budgets

Tool calls are bounded per agent and per orchestration.

AI Budgets

AI calls are bounded per orchestration context.

Mock-Source Protection

Mock records are test/development data and must not be presented as genuine employer opportunities or routed to fake employer destinations.

Historical Roadmap — 13 Phases

This section records the documented evolution of the project. Where the preserved project history does not retain an exact original subphase title, the README intentionally does not invent one.

Phase 1 — Foundation ✅

Recorded scope: Spring Boot/Maven multi-module foundation, module boundaries, core project setup, and baseline services.

The exact initial subphase-by-subphase inventory is not fully preserved in the final project-history record, so no unsupported 1.x titles are invented here.

Phase 2 — Resume Intelligence ✅

Recorded scope:

PDF/DOCX parsing with Apache PDFBox and Apache POI

structured CandidateProfile construction and persistence

resume upload API

frontend upload flow

structured profile rendering

deterministic profile construction/correction work

The exact final 2.x subphase-by-subphase inventory is not fully preserved in the final record.

Phase 3 — Job Intelligence ✅

Recorded scope:

JobSource abstraction

MockJobSource

public/live source adapters

normalization and deduplication

location/experience/employment-type filtering

software/hardware/ECE/VLSI/embedded job support

later evidence-based search-query propagation

The exact final 3.x subphase-by-subphase inventory is not fully preserved in the final record.

Phase 4 — Career Analysis / ATS / Safe Tailoring ✅

Subphase

Recorded scope

Verification record

4.1

Career Gap Analysis: deterministic skill/experience/track gaps

28 focused tests; full suite 403

4.2

Improvement Priority: deterministic prioritization structure

15 tests; full suite 418

4.3

Career Improvement Plan: deterministic plan + optional AI explanation/fallback

14 tests; full suite 432

4.4

ATS Tailoring Analysis: readiness/section models and deterministic formula

24 tests; full suite 456

4.5

Safe Tailored Resume Draft: evidence-safe draft generation and warnings

full suite 475

Phase 5 — Application Preparation & User-Controlled Sending ✅

Subphase

Recorded scope

Verification record

5.1

Application Email Preparation; REVIEW_REQUIRED / DRAFT_ONLY

29 focused tests; full suite 504

5.2

User-Controlled Email Sending; explicit approved=true gate

17 tests + controller coverage; full suite 531

Phase 6 — Multi-Agent Orchestration ✅

Subphase

Recorded scope

Verification record

6.1

Agent contracts, five logical agents, fixed sequence, MAX_AGENTS=5

43 tests; full suite 576

6.2

Controlled AI reasoning via AgentReasoningService

26 tests; full suite 602

6.3

Controlled tool orchestration and budgets

32 tests; full suite 634

6.4

Run tracking with RunStatus, OrchestrationRun, AgentExecution

25 tests; full suite 659

6.5

REST orchestration API + safe errors

19 tests; full suite 678

6.6

Run-local memory: bounded AgentContext

20 tests; full suite 698

6.7

End-to-end five-agent workflow

21/21 cases; full suite 719

6.8

Frontend Career Agent workflow and guarded rerun

full suite 722; later browser checks

6.9

Job-source/search reliability; mock/live-source behavior and graceful failures

later project record

6.10–6.12

Later project history records these milestones as completed, but the exact original titles and test deltas are not fully preserved

do not invent unsupported detail

Phase 7 — Application Advisor ✅

The project history records the following Phase 7 evolution:

Subphase

Recorded scope

Evidence status

7.1

Readiness score in application-preparation response

implemented in advisor path

7.2

Learning improvements / deterministic gap-driven preparation data

implemented in advisor path

7.3

ApplicationAdvisorController

implementation recorded

7.4

Pre-implementation inspection / review before later advisor work

inspection completed; no unsupported standalone coding claim made

7.5

Multi-factor recommendation using ATS readiness + job match and deterministic recommendation mapping

direct implementation evidence

7.6

Optional AI reasoning enrichment via AgentReasoningService

implementation evidence

7.7

REST API endpoint + frontend integration

implemented/integrated; exact standalone acceptance artifact not fully preserved

The preserved Phase 7 advisor implementation calculates deterministic ATS readiness, obtains a deterministic job-match score, forms a composite recommendation, and builds strengths, concerns, and recommended actions. Optional AI reasoning is enrichment only and does not replace deterministic authority.

Phase 8 — Safety / Autonomy + Stabilization ✅

Subphase

Recorded scope

Verification record

8.1

AgentSafetyPolicy.java

implemented

8.2

AutonomyLevel.java

implemented

8.3

Integrate safety checks into application flow

implemented

8.4

Stabilization / verification gate, including Hibernate persistence fix

commit ce61c38; full reactor verification passed

The stabilization fix addressed PersistentConversationStore.trimMessages() so orphan deletes are flushed while the parent remains managed. A regression test and rollback tag phase-8-backend-green were recorded.

Phase 9 — Observability ✅ / Frozen

9.1 — Enhance execution tracking with structured log output.

9.2 — Add execution timeline to the agent orchestrator.

The later record also includes correlation/observability improvements, run identifiers, and PII-safe logging utilities such as LoggingContext and PiiSanitizer.

Freeze checkpoint: de68e10.

The final record does not preserve a larger exact 9.x inventory beyond the documented items above.

Phase 10 — Evaluation ✅ / Frozen

10.1 — Create EvaluationService.java.

10.2 — Integrate evaluation metrics into the orchestrator pipeline.

Evaluation scope included metrics collection, tool success rate, score distribution, and measurable system behavior.

Freeze checkpoint: 3eb73c7.

Phase 11 — MCP / Openings-MCP ✅ / Frozen

11.1 — Openings-MCP implementation and integration layer.

Freeze checkpoint: 0d141d6 (preceded by fc3176c and 40fa456).

Local server verification: Openings-MCP 0.16.2, commit 3726ec09, endpoint 127.0.0.1:9000, 35 tools through tools/list, direct tools/call, and google_search_jobs verification.

Application-side work preserved job IDs, provenance, genuine URL handling, and exact-ID cache lookup.

The project-associated MCP server is run locally as a separate executable; it is not presented as a server binary committed into this repository.

Phase 12 — Frontend Completion / Application Flow ✅ (Later Completion Work)

The earlier September handoff stopped at 12.2, but the later final project history documents subsequent completion work for 12.3–12.10. The README follows that later final record.

Subphase

Recorded scope

Recorded evidence

12.1

Resume/profile evidence reliability and deterministic profile fixes

checkpoint 553eb76; browser A–G slice recorded

12.2

Frontend/application-advisor preparation foundation

checkpoint f47562b; browser A–G warm run reported

12.3

Job Search Flow & Live Job Sources; profile-derived keywords; location/experience/employment type UX; live/public-source flow

commit a6eaf5c; later browser record reported 14/14 checkpoints with ARBEITNOW + REMOTIVE

12.4

Match details and source URL polish; modal controls; responsive behavior; source/application URL semantics

commits da933ac, e9bdf19; recorded browser verification

12.5

Career Analysis + Application Readiness frontend flow and UI polish

later B1–B8 browser workflow evidence

12.6

ATS Resume Tailoring presentation and generated-document flow

later project record marks complete/verified

12.7

Application Package; preparation/review/approval flow

persistence coverage; SMTP explicitly not treated as real mailbox delivery when unconfigured

12.8

Employer Application / Apply Kit; truthful employer URLs and assisted handoff boundary

automatic form filling/submission intentionally out of scope

12.9

Application Tracking; seven application states, idempotent approval behavior, timeline UI

commits f21b09c, 45afa38, 28d49aa, 95da0e9, c3c570a

12.10

MCP-backed final E2E and employer handoff

commit 1ba94cc; 35-tool MCP verification; recorded Playwright acceptance of match → prepare → approve → Apply Kit → acknowledgement → handoff

The Phase 12.10 record states that the system records the employer handoff destination and timestamp but does not claim that the employer received or accepted a submission.

Phase 13 — LLM Provider Modernization ✅ / Frozen

Subphase

Recorded scope

Evidence / checkpoint

13.1

Provider abstraction: LlmProvider, OllamaProvider, GeminiProvider, LlmProviderRouter

commit 41509d0

13.2

Groq provider + properties via OpenAI-compatible integration

commit ff91c74; model correction 40eb27f

13.2.1

Groq model correction

openai/gpt-oss-20b; commit 40eb27f

13.3

Provider-neutral active path with LlmProperties.defaultProvider and router-based chat model selection

commit d7c29be

13.4

Live Groq smoke test

GROQ_SMOKE_TEST_OK; commit 5ae5a98

13.5

OpenRouter provider/configuration and token cap

commits 3e72bd6, 262d7bf; OPENROUTER_SMOKE_TEST_OK recorded

13.6

Cerebras provider/configuration; model later switched to gpt-oss-120b

billing/payment-required limitation recorded for live account state

13.7

Cloudflare AI Gateway provider plus provider-local compatibility handling

commit 6540fde; CLOUDFLARE_SMOKE_TEST_OK; Gemini/Cerebras compatibility follow-up recorded in ff9cb70

13.8

Automatic provider failover

commit 2137597; 12 failover tests recorded

13.9

In-memory provider cooldown / circuit breaker

commit e690b4b; 12 cooldown tests; full 1181-test snapshot passed

13.10

Verification-only phase; no source change/commit

clean startup, HTTP/chat checks, browser smoke, deterministic/local job-search checks in recorded run

Phase 13 moved the active architecture from an Ollama-centric path to a provider-neutral router with cloud providers, failover, cooldown, and provider-specific compatibility handling.

Testing and Verification

Testing was built incrementally alongside implementation.

Recorded full-suite milestones include:

403 → 418 → 432 → 456 → 475 → 504 → 531 → 576 → 602 → 634 → 659 → 678 → 698 → 719 → 722 → 1169 → 1181

The recorded Phase 13.9/13.10 full-suite snapshot reports:

1181 tests
0 failures
0 errors
BUILD SUCCESS

Testing techniques used across the project include:

JUnit

Mockito

Spring test contexts

controller/contract tests

deterministic engine tests

orchestration tests

integration tests

regression tests

Testcontainers for PostgreSQL/pgvector integration

browser/E2E checks using Playwright/Shiplight

manual API smoke checks

git diff --check

PostgreSQL / pgvector Verification Note

The project later added a targeted PostgreSQL regression test for conversation trimming after the e92635c production fix. The test was committed as:

ee91cd9 test: add pgvector trim regression coverage

A previous PostgreSQL Testcontainers run successfully executed the original PgVectorConversationStoreTest suite with 9 tests passing. A later attempt to execute the newly expanded test class was blocked before the test methods ran because the temporary pgvector/pgvector container failed its readiness check. Therefore the new targeted regression assertion is committed but is not claimed as passed.

Git and Change Control

The project uses explicit verification gates before commits:

Inspect change
    ↓
Run focused tests
    ↓
Run full Maven reactor
    ↓
Check git diff
    ↓
Run git diff --check
    ↓
Inspect staged files
    ↓
Commit
    ↓
Verify git status
    ↓
Push only when explicitly approved

Important recorded checkpoints include:

553eb76 — Phase 12.1 resume/profile reliability

f47562b — Phase 12.2 frontend/application-preparation foundation

ce61c38 — persistent conversation-store orphan-delete fix

41509d0 — LLM provider abstraction

ff91c74 — Groq provider integration

40eb27f — Groq model correction

d7c29be — provider-neutral active path

5ae5a98 — live Groq verification

3e72bd6 — OpenRouter provider

262d7bf — OpenRouter token-limit fix

6540fde — Cloudflare AI Gateway integration

ff9cb70 — Gemini/Cerebras compatibility fix

2137597 — automatic LLM provider failover

e690b4b — provider cooldown / circuit breaker

e92635c — PostgreSQL conversation-trimming flush-order fix

ee91cd9 — PostgreSQL trim regression test

The latest ee91cd9 commit was subsequently pushed to:

arena/01a08566-anent-platform

The local working tree was recorded as still containing unrelated untracked diff_full.txt, maven_output.txt, and test-*.cjs files; these were intentionally not staged by the final test commit.

Development Environment

Recorded development tools include:

Windows / PowerShell

Java 21

Maven

IntelliJ IDEA

Eclipse

Docker Desktop

PostgreSQL / pgvector

OpenCode

Arena AI

Ponytail plugin

Playwright / Shiplight browser automation

Configuration and Secrets

API credentials are supplied through environment variables.

Example configuration concept:

$env:GROQ_API_KEY="..."
$env:GROQ_ENABLED="true"
$env:LLM_DEFAULT_PROVIDER="groq"

Never commit real credentials.

The project history explicitly distinguishes API configuration from application data and records that provider credentials should remain environment-specific.

Running Locally

The documented default runtime uses H2 for lightweight local/test execution, while PostgreSQL is available as an optional configured profile.

Basic Maven workflow:

cd "E:\AI Agent\agent-platform"
mvn test

Start the relevant Spring Boot UI/application module using the repository's current Maven configuration and environment variables.

Docker Note

Docker Desktop is not an LLM requirement. It is used when the local environment or a test explicitly needs Docker-managed services such as PostgreSQL/pgvector or Testcontainers.

The cloud LLM providers communicate through their APIs independently of Docker.

Project Scope and Honest Limitations

This repository should be presented as a portfolio-ready, local-first prototype / engineering project, not as a fully hardened production SaaS platform.

Known boundaries from the project record include:

authentication/multi-tenant ownership hardening was not the focus of the current scope

deployment-scale security and infrastructure hardening remain separate concerns

the rag-service module is retained as a placeholder; RAG/vector retrieval was not accepted as a required core capability

some API method/path details are not fully preserved in the chat-derived history; current source annotations are authoritative

exact repository-wide Java class count is not asserted by this README

live job-source enablement depends on runtime configuration; public/live capability should not be represented as a guarantee that every source is always active

application approval/handoff is not equivalent to proof of successful employer submission

the latest added PostgreSQL regression test is committed but has not been claimed as successfully executed because the Testcontainers pgvector container timed out during startup

Why This Project Is Technically Interesting

This project is not simply an LLM wrapper. It combines:

Spring Boot backend engineering

multi-module Maven architecture

JPA/Hibernate persistence

PDFBox/POI document processing

deterministic scoring engines

multi-agent orchestration

policy-controlled tool execution

bounded AI execution

multiple cloud LLM provider adapters

automatic provider failover

provider cooldown / circuit-breaker behavior

public/live job-source abstraction

Openings-MCP integration

browser automation and E2E verification

application safety/approval states

safe error classification

Git-based verification discipline

The central engineering idea is the separation between authoritative deterministic career data and bounded probabilistic language-model assistance.

Interview Topics Covered by This Project

A technical discussion of this project can cover:

Why deterministic matching is preferable to asking an LLM for a match score.

How JobSource allows multiple job providers without coupling search to one vendor.

How LlmProviderRouter handles provider selection, failover, and cooldown.

How application approval acts as a safety boundary.

How tool budgets prevent uncontrolled agent behavior.

How fallback behavior works when an LLM is unavailable.

How JPA/Hibernate persistence is organized in a multi-module Spring Boot system.

How browser E2E testing complements backend unit and integration tests.

Why mock job data must remain distinguishable from real/public sources.

Why the platform keeps evidence and provenance instead of allowing generated text to overwrite source facts.

How a locally run project-associated MCP server can sit behind the same JobSource abstraction as other job sources.

Related Project: NN_VLSI_Simulator

A separate Java project discussed alongside this platform is NN_VLSI_Simulator, a software-side neural-network/VLSI simulator built around hardware-inspired Java units.

Recorded components include:

MemoryUnit, ControlUnit, ActivationUnit, AdderUnit, MultiplierUnit, Layer, and NeuralNetwork.

That project explores neural-network execution using hardware-oriented abstractions and forms a separate software-to-VLSI learning track from this AI Career Agent Platform.

Portfolio Positioning

One-line Description

AI Career Agent Platform — a Java/Spring Boot evidence-first career automation platform with deterministic job matching, bounded multi-agent AI, multiple LLM providers, Openings-MCP job discovery, ATS guidance, and user-controlled application workflows.

Resume-Style Project Description

AI Career Agent Platform | Java 21, Spring Boot, Maven, JPA/Hibernate, H2/PostgreSQL, LangChain4j, Vanilla JS

Built a multi-module AI career platform that parses PDF/DOCX resumes into structured candidate profiles, discovers and normalizes jobs through pluggable sources and Openings-MCP, performs deterministic skill/experience/track matching and career-gap analysis, generates ATS-oriented tailoring guidance, orchestrates five bounded agents, and prepares user-reviewed applications. Implemented a provider-neutral LLM router with Gemini, Groq, OpenRouter, Cerebras, and Cloudflare integrations, automatic failover/cooldown behavior, policy-limited tool execution, safe error classification, explicit approval gates, and JUnit/Mockito/integration/browser verification.

Documentation

For the complete engineering record—including phase history, Java inventory, architecture, testing history, browser observations, MCP evidence, Git checkpoints, provider modernization, verification boundaries, and related NN/VLSI work—see the AI Career Agent Platform Final Master Project Dossier maintained alongside this repository documentation.

Status

Portfolio preparation phase: documented development work is complete for the recorded project scope; repository presentation, screenshots, architecture visuals, README review, and resume packaging are the remaining portfolio tasks.

License

No license is asserted by this README. Add an explicit license to the repository only when the project owner chooses one.
