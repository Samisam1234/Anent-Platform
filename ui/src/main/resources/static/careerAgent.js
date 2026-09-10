/**
 * AI Job Agent — Career Agent workflow.
 *
 * One user action ("Run Career Agent" on a match) produces two things:
 *
 *  1. A CONTROLLED AGENT RUN (POST /api/v1/agent/orchestrate) — the existing
 *     RESUME → JOB_DISCOVERY → MATCHING → CAREER_ADVISOR → APPLICATION_ADVISOR
 *     pipeline. Only safe API fields are rendered (runStatus, success, message,
 *     agentExecutions, aiCallsUsed, toolCallsUsed, stoppingAgentType).
 *  2. A DATA-BACKED CAREER FIT REPORT built from the deterministic
 *     Application Advisor (POST /api/v1/jobs/advisor) plus the job record
 *     (GET /api/v1/jobs/{jobId}). Every number in the report is a deterministic
 *     score the backend already computes; nothing is estimated client-side.
 *
 * The two are fetched in parallel and rendered into separate slots, so a slow or
 * failed AI run never hides the deterministic analysis — and a missing field is
 * shown as "Not available" rather than guessed.
 *
 * It never displays raw AgentContext, resume text, tool arguments, or internal
 * exceptions, and it never interprets an APPLICATION_ADVISOR completion as an
 * email sent or an application submitted — only ApplicationEmailService plus
 * explicit user approval do that.
 */
(() => {
    'use strict';

    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';
    const ORCHESTRATION_API = '/api/v1/agent/orchestrate';
    const ADVISOR_API = '/api/v1/jobs/advisor';
    const JOB_API = '/api/v1/jobs/';

    const AGENT_LABELS = {
        RESUME: 'Resume Analysis',
        JOB_DISCOVERY: 'Job Discovery',
        MATCHING: 'Job Matching',
        CAREER_ADVISOR: 'Career Advisor',
        APPLICATION_ADVISOR: 'Application Advisor'
    };

    const AGENT_ORDER = ['RESUME', 'JOB_DISCOVERY', 'MATCHING', 'CAREER_ADVISOR', 'APPLICATION_ADVISOR'];

    // Plain-language labels for the deterministic recommendation bands returned by
    // ApplicationAdvisorService.mapScoreToRecommendation (thresholds unchanged here).
    const RECOMMENDATION_LABELS = {
        STRONGLY_RECOMMENDED: { label: 'High priority', cls: 'fit-high' },
        RECOMMENDED: { label: 'Apply', cls: 'fit-high' },
        APPLY_WITH_IMPROVEMENTS: { label: 'Apply after improvements', cls: 'fit-medium' },
        LOW_PRIORITY: { label: 'Low priority', cls: 'fit-low' },
        NOT_RECOMMENDED: { label: 'Not recommended', cls: 'fit-low' }
    };

    let overlay = null;
    let requestInFlight = false;
    // Kept in module state (not read back out of the DOM) so Retry re-runs the exact
    // same candidate + job the user launched. The previous implementation looked for a
    // [data-job-id] attribute that the modal never set, so Retry silently did nothing.
    let activeCandidateId = null;
    let activeJobId = null;
    let activeJob = null;

    // ─── Modal lifecycle ────────────────────────────────────────────────────
    function ensureModal() {
        if (overlay) return overlay;

        overlay = document.createElement('div');
        overlay.className = 'agent-overlay';
        overlay.hidden = true;
        overlay.innerHTML = `
            <div class="agent-modal" role="dialog" aria-modal="true" aria-labelledby="agentModalTitle">
                <div class="agent-modal-header">
                    <div class="agent-modal-header-text">
                        <span class="agent-kicker">Career Agent</span>
                        <h3 class="agent-modal-title" id="agentModalTitle">Career Analysis</h3>
                        <div class="agent-modal-sub" id="agentModalSub"></div>
                    </div>
                    <button type="button" class="modal-close" id="agentModalClose" aria-label="Close career agent results">&times;</button>
                </div>
                <div class="agent-modal-body" id="agentModalBody"></div>
                <div class="agent-modal-footer" id="agentModalFooter"></div>
            </div>
        `;

        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) closeModal();
        });
        overlay.querySelector('#agentModalClose').addEventListener('click', closeModal);
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && overlay && !overlay.hidden) closeModal();
        });

        document.body.appendChild(overlay);
        return overlay;
    }

    function open(candidateId, candidateName, job) {
        if (requestInFlight || !job || !job.id) return;

        activeCandidateId = candidateId;
        activeJobId = job.id;
        activeJob = job;

        const modal = ensureModal();
        modal.querySelector('#agentModalSub').innerHTML = buildSubHtml(candidateName, job);
        modal.querySelector('#agentModalBody').innerHTML = buildSkeletonHtml();
        renderFooter(candidateId, job);
        modal.hidden = false;
        document.body.classList.add('modal-open');
        run(candidateId, job.id, modal);
    }

    function closeModal() {
        if (!overlay || overlay.hidden) return;
        overlay.hidden = true;
        document.body.classList.remove('modal-open');
    }

    // ─── Display helpers ────────────────────────────────────────────────────
    function buildSubHtml(candidateName, job) {
        const nameChip = candidateName
            ? `<span class="summary-chip">Profile: ${esc(candidateName)}</span>`
            : `<span class="summary-chip">No profile</span>`;
        return `${nameChip}<span class="summary-chip">Role: ${esc(job.title || 'Untitled')} at ${esc(job.company || 'Unknown Company')}</span>`;
    }

    function renderFooter(candidateId, job) {
        const footer = ensureModal().querySelector('#agentModalFooter');
        footer.innerHTML = '';

        const runBtn = document.createElement('button');
        runBtn.type = 'button';
        runBtn.className = 'btn-primary btn-run-agent';
        runBtn.textContent = 'Run Again';

        if (!candidateId) {
            runBtn.disabled = true;
            runBtn.setAttribute('title', 'Upload a resume first to run the career agent.');
        }

        runBtn.addEventListener('click', () => run(candidateId, job.id, ensureModal()));
        footer.appendChild(runBtn);

        const hint = document.createElement('span');
        hint.className = 'modal-hint';
        if (!candidateId) {
            hint.innerHTML = `Upload a <a href="resume.html">resume</a> first to run the career agent.`;
        } else {
            hint.textContent = 'Career fit is calculated from your parsed profile and this listing — no AI text is invented.';
        }
        footer.appendChild(hint);
    }

    // ─── Skeleton + slots ───────────────────────────────────────────────────
    /**
     * The modal opens with two independent slots: the deterministic career-fit
     * report and the controlled agent run. Each is filled as its own request
     * settles, so a slow AI pipeline never delays the analysis.
     */
    function buildSkeletonHtml() {
        return `
            <div id="careerReportSlot">
                <div class="agent-run-loading">
                    <span class="processing-spinner"></span>
                    <span>Calculating career fit from your profile and this listing…</span>
                </div>
            </div>
            <div id="agentRunSlot"></div>`;
    }

    function slot(modal, id) {
        return modal.querySelector('#' + id);
    }

    // ─── Orchestration + deterministic analysis (one user action) ───────────
    function run(candidateId, jobId, modal) {
        if (requestInFlight) return;
        if (!candidateId) {
            renderMissingResult(modal);
            return;
        }

        requestInFlight = true;
        const runBtn = modal.querySelector('.btn-run-agent');
        if (runBtn) runBtn.disabled = true;

        modal.querySelector('#agentRunSlot').innerHTML = buildInFlightHtml();

        // The advisor response drives the report; orchestration only annotates it.
        // The job lookup is optional context (location / employment type): the
        // listing index only holds a limited window of live jobs, so a 404 there must
        // not suppress the analysis the advisor already produced.
        Promise.all([
            fetchJson(ADVISOR_API, { candidateId: Number(candidateId), jobId: String(jobId) }),
            fetchJson(JOB_API + encodeURIComponent(jobId)).catch(err => {
                console.warn('Job listing not retrievable for header context:', err);
                return null;
            })
        ])
            .then(([advisor, job]) => {
                const reportSlot = slot(modal, 'careerReportSlot');
                if (reportSlot) reportSlot.innerHTML = buildReportHtml(advisor, job);
            })
            .catch(err => {
                console.error('Career fit analysis error:', err);
                const reportSlot = slot(modal, 'careerReportSlot');
                if (reportSlot) {
                    reportSlot.innerHTML = buildErrorResult(
                        'Could not calculate career fit for this role. '
                        + (err && err.message ? esc(err.message) : 'Please try again.'));
                }
            })
            .then(() => {
                requestInFlight = false;
                if (runBtn) runBtn.disabled = false;
            });

        // Fire-and-forget: the agent run summary renders whenever it finishes.
        runOrchestration(candidateId, jobId, modal);
    }

    async function fetchJson(url, body) {
        const options = body
            ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
            : { method: 'GET', headers: { 'Accept': 'application/json' } };

        const response = await fetch(url, options);
        if (!response.ok) {
            const detail = await safeError(response);
            throw new Error(detail.detail || detail.title || `Request failed (${response.status}).`);
        }
        return response.json();
    }

    async function safeError(response) {
        try {
            return await response.json();
        } catch (_) {
            return {};
        }
    }

    async function runOrchestration(candidateId, jobId, modal) {
        try {
            const data = await fetch(ORCHESTRATION_API, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ candidateId: Number(candidateId), jobId: String(jobId) })
            });

            const runSlot = slot(modal, 'agentRunSlot');
            if (!runSlot) return;

            if (!data.ok) {
                const detail = await safeError(data);
                runSlot.innerHTML = buildRunSummaryHtml(null, detail, data.status);
                return;
            }
            runSlot.innerHTML = buildRunSummaryHtml(await data.json(), null, data.status);
        } catch (err) {
            console.error('Career agent orchestration error:', err);
            const runSlot = slot(modal, 'agentRunSlot');
            if (runSlot) {
                runSlot.innerHTML = buildRunSummaryHtml(null, null, 0);
            }
        }
    }

    // ─── In-flight rendering ────────────────────────────────────────────────
    /**
     * The orchestration endpoint is a single synchronous request: the server runs
     * RESUME → JOB_DISCOVERY → MATCHING → CAREER_ADVISOR → APPLICATION_ADVISOR in one
     * blocking call and reports every stage's final state when it responds. There is no
     * per-stage stream or polling, so claiming a per-stage "WAITING" status here would be
     * invented state. We show one honest in-progress notice and render the real per-stage
     * statuses from the response.
     */
    function buildInFlightHtml() {
        const stages = AGENT_ORDER
            .map(type => `<span class="summary-chip">${esc(AGENT_LABELS[type] || type)}</span>`)
            .join('');
        return `
            <div class="agent-run-block">
                <div class="agent-run-loading">
                    <span class="processing-spinner"></span>
                    <span>Running the controlled agent pipeline…</span>
                </div>
                <p class="modal-hint">
                    All stages run in a single request, so per-stage results appear together when the
                    run finishes. Stages: ${stages}
                </p>
            </div>`;
    }

    function formatDuration(ms) {
        if (ms < 1000) return `${ms}ms`;
        const sec = Math.round(ms / 1000);
        if (sec < 60) return `${sec}s`;
        const min = Math.floor(sec / 60);
        const rem = sec % 60;
        return rem > 0 ? `${min}m ${rem}s` : `${min}m`;
    }

    // ─── Deterministic career-fit report ────────────────────────────────────
    /**
     * Renders the career-fit report from the Application Advisor response only.
     * Each section is emitted when the backing data exists and explicitly marked
     * "Not available" when it does not — no section invents a value.
     */
    function buildReportHtml(advisor, job) {
        const adv = advisor || {};
        // Merge order: the card that launched this run supplies title/company, the
        // listing lookup overrides with the canonical record when it resolved.
        const jobData = Object.assign({}, activeJob || {}, job || {});
        const b = adv.scoreBreakdown || null;

        const readiness = Number.isFinite(adv.applicationReadinessScore)
            ? adv.applicationReadinessScore : null;
        const rec = RECOMMENDATION_LABELS[adv.recommendation] || null;

        // Skill names come from the structured breakdown, so the report never has to
        // pattern-match the advisor's prose lines to recover them.
        const matchedSkills = (b && b.matchedRequiredSkills) || [];
        const missingSkills = (b && b.missingRequiredSkills) || [];
        const missingPreferred = (b && b.missingPreferredSkills) || [];
        const extras = (b && b.matchedPreferredSkills) || [];
        const strengths = narrativeLines(adv.strengths, matchedSkills, null);
        const concerns = narrativeLines(adv.concerns, missingSkills, missingPreferred);

        return `
            <section class="career-report">
                <header class="career-report-head">
                    <div class="career-report-role">
                        <h4 class="career-report-title">${esc(jobData.title || adv.jobTitle || 'Selected role')}</h4>
                        <div class="career-report-company">${esc(jobData.company || adv.company || 'Company not listed')}</div>
                        <div class="career-report-meta">
                            <span class="summary-chip">${esc(jobData.location || 'Location not listed')}</span>
                            <span class="summary-chip">${esc(formatEmploymentType(jobData.employmentType))}</span>
                            ${adv.jobMatchScore != null ? `<span class="summary-chip">Job match ${esc(adv.jobMatchScore)}/100</span>` : ''}
                        </div>
                    </div>
                    <div class="career-report-score">
                        <span class="fit-label">Career fit</span>
                        <span class="fit-value">${readiness == null ? '—' : esc(readiness)}<small>/100</small></span>
                        ${rec ? `<span class="fit-badge ${rec.cls}">${esc(rec.label)}</span>` : ''}
                    </div>
                </header>

                ${reportSection('Why', adv.recommendationExplanation
                    ? `<p class="career-report-text">${esc(adv.recommendationExplanation)}</p>`
                    : notAvailable())}

                ${b ? reportSection('How this was scored', buildFitRows(b)) : ''}

                ${reportSection('Skills you already match', matchedSkills.length
                    ? `<div class="skills-tags-wrap">${matchedSkills.map(s => `<span class="skill-tag required-skill match-hit">${esc(s)}</span>`).join('')}</div>`
                    : notAvailable('No required skills from this listing were found in your profile.'))}

                ${reportSection('Skills still missing', (missingSkills.length || missingPreferred.length)
                    ? `<div class="skills-tags-wrap">${missingSkills.map(s => `<span class="skill-tag required-skill match-miss">${esc(s)}</span>`).join('')}${missingPreferred.map(s => `<span class="skill-tag preferred-skill match-miss" title="Preferred, not required">${esc(s)}</span>`).join('')}</div>`
                    : notAvailable('Nothing required by this listing is missing from your profile.'))}

                ${reportSection('Strengths', strengths.length
                    ? `<ul class="fit-list is-good">${strengths.map(s => `<li>${esc(s)}</li>`).join('')}</ul>`
                    : notAvailable('No further strengths were reported beyond the matched skills above.'))}

                ${reportSection('Risks and concerns', concerns.length
                    ? `<ul class="fit-list is-bad">${concerns.map(s => `<li>${esc(s)}</li>`).join('')}</ul>`
                    : notAvailable('No risks were reported for this application.'))}

                ${reportSection('Experience fit', b ? experienceHtml(b) : notAvailable())}

                ${reportSection('Career track', b ? trackHtml(b) : notAvailable())}

                ${extras.length ? reportSection('Additional skills you already have',
                    `<p class="career-report-text">Listed as preferred (not required) by this employer, and present in your resume:</p>
                     <ul class="fit-list is-good">${extras.map(s => `<li>${esc(s)}</li>`).join('')}</ul>`) : ''}

                ${reportSection('Next steps', (adv.recommendedActionDetails && adv.recommendedActionDetails.length)
                    ? buildNextSteps(adv.recommendedActionDetails)
                    : notAvailable('No improvement priorities were produced — there is nothing to close for this role.'))}

                <p class="agent-note">
                    Scores are deterministic: skill coverage, experience, location, education and career-track
                    factors are computed from your parsed profile and this listing. Career fit is
                    60% ATS readiness + 40% job match. No text here is generated by the AI model.
                </p>
            </section>`;
    }

    function reportSection(title, bodyHtml) {
        return `
            <div class="career-report-section">
                <h5 class="career-report-section-title">${esc(title)}</h5>
                ${bodyHtml}
            </div>`;
    }

    function notAvailable(fallback) {
        return `<p class="career-report-na">${esc(fallback || 'Not available')}</p>`;
    }

    /** Per-factor rows, each labelled with the score the backend actually produced. */
    function buildFitRows(b) {
        const rows = [
            ['Skill fit', b.skillFitScore],
            ['Role fit', b.roleFitScore],
            ['Experience fit', b.experienceFitScore],
            ['Education fit', b.educationFitScore],
            ['Location fit', b.locationFitScore],
            ['Career-track fit', b.trackFitScore]
        ];
        return `<div class="fit-rows">${rows.map(([label, value]) => {
            const pct = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
            const tone = pct >= 70 ? 'is-good' : (pct >= 40 ? 'is-mid' : 'is-low');
            return `
                <div class="fit-row">
                    <span class="fit-row-label">${esc(label)}</span>
                    <span class="fit-bar"><span class="fit-bar-fill ${tone}" style="width:${pct}%"></span></span>
                    <span class="fit-row-value">${esc(pct)}</span>
                </div>`;
        }).join('')}</div>
        <p class="career-report-text career-report-note">
            Required skills: ${esc(b.matchedRequiredCount)} matched, ${esc(b.missingRequiredCount)} missing.
            Preferred skills: ${esc(b.matchedPreferredCount)} matched, ${esc(b.missingPreferredCount)} missing.
        </p>`;
    }

    /**
     * Experience sentence. States a shortfall only when the backend marked the
     * comparison as knowable — otherwise it says the comparison could not be made.
     */
    function experienceHtml(b) {
        const required = b.requiredYears;
        const candidate = b.candidateYears;
        const fit = b.experienceFitScore;

        if (!b.experienceKnowable) {
            return `<p class="career-report-text">Not available — ${
                required != null
                    ? 'the listing asks for ' + esc(required) + ' year(s), but no structured years could be read from your resume, so no shortfall is claimed.'
                    : 'neither the listing nor your resume carried structured years to compare.'
            }</p>`;
        }
        if (required === 0) {
            return `<p class="career-report-text">This role is entry level, so no minimum years apply (experience fit ${esc(fit)}/100).</p>`;
        }
        if (required != null && candidate != null) {
            const short = required - candidate;
            const verdict = short > 0
                ? `You are ${esc(short)} year${short === 1 ? '' : 's'} short of the stated requirement.`
                : 'Your stated experience meets or exceeds the requirement.';
            return `<p class="career-report-text">Your resume shows ${esc(candidate)} year${candidate === 1 ? '' : 's'}; this listing asks for ${esc(required)}. ${esc(verdict)} Experience fit ${esc(fit)}/100.</p>`;
        }
        return notAvailable();
    }

    function trackHtml(b) {
        const candidate = humanize(b.candidateTrack);
        const jobTrack = humanize(b.jobTrack);
        if (!candidate || !jobTrack) {
            return notAvailable('Career track could not be determined on both sides.');
        }
        const alignment = b.trackMismatch
            ? `<span class="fit-badge fit-low">Different tracks</span>`
            : `<span class="fit-badge fit-high">Aligned</span>`;
        return `<p class="career-report-text">Your profile reads as <strong>${esc(candidate)}</strong>; this listing reads as <strong>${esc(jobTrack)}</strong>. ${alignment} Track fit ${esc(b.trackFitScore)}/100.</p>
            ${b.gapSeverity && b.gapSeverity !== 'NO_GAP'
                ? `<p class="career-report-note">Overall gap severity: ${esc(humanize(b.gapSeverity))}.</p>` : ''}`;
    }

    function buildNextSteps(details) {
        return `<ol class="career-next-steps">${details.map(d => `
            <li>
                <span class="career-step-focus">${esc(d.focus || 'Improvement')}</span>
                <span class="career-step-desc">${esc(d.description || d.reason || '')}</span>
                ${d.reason && d.description && d.reason !== d.description
                    ? `<span class="career-step-reason">Why: ${esc(d.reason)}</span>` : ''}
            </li>`).join('')}</ol>`;
    }

    /**
     * Keeps only the narrative lines of the advisor's strengths/concerns. The skill
     * names themselves are rendered as chips from the structured breakdown, so a line
     * that merely names a skill already shown (with or without its evidence suffix) is
     * dropped — that is an exact comparison against structured data, not pattern
     * matching, and it keeps a skill from appearing in two sections.
     */
    function narrativeLines(lines, requiredSkills, preferredSkills) {
        const shown = new Set((requiredSkills || []).map(String));
        (preferredSkills || []).forEach(s => shown.add(String(s)));

        return (lines || [])
            .map(line => String(line || '').trim())
            .filter(text => {
                if (!text) return false;
                if (text.indexOf('Preferred match: ') === 0) return false;
                if (text.indexOf('Missing required skill: ') === 0) return false;
                if (text.indexOf('Missing preferred skill: ') === 0) return false;
                const bare = text.replace(/\s*\(evidence:[^)]*\)\s*$/, '');
                return !shown.has(bare);
            });
    }

    function humanize(value) {
        if (!value) return '';
        return String(value).toLowerCase().split('_')
            .filter(Boolean)
            .map(w => w.charAt(0).toUpperCase() + w.slice(1))
            .join(' ');
    }

    function formatEmploymentType(type) {
        if (!type) return 'Employment type not listed';
        switch (String(type).toUpperCase()) {
            case 'FULL_TIME': return 'Full-time';
            case 'PART_TIME': return 'Part-time';
            case 'INTERNSHIP': return 'Internship';
            case 'CONTRACT': return 'Contract';
            default: return String(type);
        }
    }

    // ─── Controlled agent run summary ───────────────────────────────────────
    function buildRunSummaryHtml(data, problem, status) {
        if (!data) {
            const message = (problem && (problem.detail || problem.title))
                || (status ? `The agent run returned ${status}.` : 'The agent run could not be reached.')
                + ' The career fit analysis above is unaffected — it is calculated deterministically.';
            return `
                <div class="agent-run-block">
                    <div class="agent-run-summary agent-summary-bad">
                        <span class="agent-summary-status">Agent run unavailable</span>
                        <span class="agent-summary-message">${esc(message)}</span>
                    </div>
                </div>`;
        }

        const runStatus = data.runStatus || 'UNKNOWN';
        const statusCls = statusClass(runStatus);
        const statusLabel = printable(runStatus);
        const success = !!data.success;

        const executions = (data.agentExecutions || []).map(agent => {
            const state = printable(agent.status || 'PENDING');
            const tone = agent.success ? 'good'
                : (agent.status === 'SKIPPED' ? 'skip' : 'bad');
            const errNote = agent.errorCode && agent.errorCode !== 'NONE'
                ? `<span class="agent-error-code">${esc(agent.errorCode)}</span>`
                : '';
            const duration = agent.durationMs > 0 ? ` (${formatDuration(agent.durationMs)})` : '';
            return `
                <li class="agent-execution agent-exec-${tone}">
                    <span class="agent-exec-dot" aria-hidden="true"></span>
                    <div class="agent-exec-main">
                        <span class="agent-exec-name">${esc(AGENT_LABELS[agent.agentType] || printable(agent.agentType))}</span>
                        <span class="agent-exec-msg">${esc(agent.message || '')}${duration} ${errNote}</span>
                    </div>
                    <span class="agent-exec-status ${tone === 'good' ? 'agent-status-good' : (tone === 'skip' ? 'agent-status-skip' : 'agent-status-bad')}">${esc(state)}</span>
                </li>`;
        }).join('');

        // Check for any timeout/failed agents for special messaging
        const hasTimeout = (data.agentExecutions || []).some(a => a.status === 'TIMEOUT');
        const hasFailure = (data.agentExecutions || []).some(a => a.status === 'FAILED');
        let statusMessage = data.message
            || (success
                ? 'The career agent completed its controlled run.'
                : 'The career agent finished with issues — review the details below.');
        if (hasTimeout) {
            statusMessage = 'One or more AI reasoning steps timed out. The deterministic career fit above is unaffected. '
                + 'You may retry or increase the Ollama timeout setting.';
        } else if (hasFailure && !success) {
            statusMessage = 'Some agents did not complete successfully. The deterministic career fit above is unaffected. '
                + 'Check the details below and retry if needed.';
        }

        return `
            <div class="agent-run-block">
                <h4 class="agent-section-title">Controlled Agent Run</h4>
                <div class="agent-run-summary agent-summary-${statusCls}">
                    <span class="agent-summary-status">${esc(statusLabel)}</span>
                    <span class="agent-summary-message">${esc(statusMessage)}</span>
                </div>

                <div class="agent-usage-row">
                    <div class="agent-usage-cell"><span class="agent-usage-num">${Number(data.aiCallsUsed) || 0}</span><span class="agent-usage-label">AI calls</span></div>
                    <div class="agent-usage-cell"><span class="agent-usage-num">${Number(data.toolCallsUsed) || 0}</span><span class="agent-usage-label">Tool calls</span></div>
                    <div class="agent-usage-cell"><span class="agent-usage-num">${esc(printable(data.stoppingAgentType) || '—')}</span><span class="agent-usage-label">Stopped at</span></div>
                </div>

                ${executions
                    ? `<ul class="agent-executions-list">${executions}</ul>`
                    : `<p class="modal-hint">No agent executions were reported for this run.</p>`}

                <p class="agent-note">The application advisor preparing material does not send email or submit applications. Sending in this platform only ever happens through the explicit, approval-gated application review flow.</p>
            </div>`;
    }

    function buildErrorResult(message) {
        return `
            <div class="agent-run-summary agent-summary-bad">
                <span class="agent-summary-status">Error</span>
                <span class="agent-summary-message">${esc(message)}</span>
            </div>`;
    }

    function renderMissingResult(modal) {
        modal.querySelector('#careerReportSlot').innerHTML = buildErrorResult(
            'A saved candidate profile is required. Upload your resume first to run the career agent.');
    }

    // ─── Formatting utilities ───────────────────────────────────────────────
    function statusClass(status) {
        switch (String(status || '').toUpperCase()) {
            case 'COMPLETED': return 'good';
            case 'PARTIAL': return 'partial';
            case 'FAILED': return 'bad';
            default: return 'partial';
        }
    }

    function printable(value) {
        if (value === undefined || value === null) return '';
        return String(value);
    }

    /**
     * HTML-escapes untrusted text (job titles and company names come from an external
     * job source). The previous version of this helper mapped each character back to
     * itself, which escaped nothing and let listing text inject markup into the modal.
     */
    function esc(text) {
        if (text === 0) return '0';
        if (text === undefined || text === null) return '';
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // ─── Delegated click handling for card run buttons ──────────────────────
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-career-agent]');
        if (!btn) return;
        const jobId = btn.getAttribute('data-job-id');
        if (!jobId) return;
        const candidateId = localStorage.getItem(LS_CANDIDATE_ID);
        const candidateName = localStorage.getItem(LS_CANDIDATE_NAME);
        open(candidateId, candidateName, {
            id: jobId,
            title: btn.getAttribute('data-job-title'),
            company: btn.getAttribute('data-company')
        });
    });

    // ─── Public API (optional, for tests / other pages) ─────────────────────
    window.careerAgent = {
        open: (candidateId, candidateName, job) => open(candidateId, candidateName, job),
        close: closeModal
    };
})();
