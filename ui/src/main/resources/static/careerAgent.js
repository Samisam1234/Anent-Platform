/**
 * AI Job Agent — Career Agent workflow (orchestration execution status + results).
 *
 * Wires the controlled agent-orchestration API (POST /api/v1/agent/orchestrate)
 * into the matches page. A single user action (clicking "Run Career Agent" on a
 * job) issues exactly one orchestration request against the resolved candidate
 * profile (localStorage key agentplatform:candidateId) and the selected job id.
 *
 * Renders ONLY safe API fields: runStatus, success, message, agentExecutions,
 * aiCallsUsed, toolCallsUsed, stoppingAgentType. It never displays raw
 * AgentContext, resume text, tool arguments, or internal exceptions, and it
 * never interprets an APPLICATION_ADVISOR completion as an email sent or an
 * application submitted — only ApplicationEmailService + explicit approval do that.
 */
(() => {
    'use strict';

    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';
    const ORCHESTRATION_API = '/api/v1/agent/orchestrate';

    const AGENT_LABELS = {
        RESUME: 'Resume Analysis',
        JOB_DISCOVERY: 'Job Discovery',
        MATCHING: 'Job Matching',
        CAREER_ADVISOR: 'Career Advisor',
        APPLICATION_ADVISOR: 'Application Advisor'
    };

    let overlay = null;
    let requestInFlight = false;

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
                        <span class="agent-kicker">Controlled Agent Run</span>
                        <h3 class="agent-modal-title" id="agentModalTitle">Career Agent</h3>
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

        const modal = ensureModal();
        modal.querySelector('#agentModalSub').innerHTML = buildSubHtml(candidateName, job);
        modal.querySelector('#agentModalBody').innerHTML = '';
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
        runBtn.textContent = 'Run Career Agent';

        const missing = !candidateId;
        if (missing) {
            runBtn.disabled = true;
            runBtn.setAttribute('title', 'Upload a resume first to run the career agent.');
        }

        runBtn.addEventListener('click', () => run(candidateId, job.id, ensureModal()));
        footer.appendChild(runBtn);

        const hint = document.createElement('span');
        hint.className = 'modal-hint';
        if (missing) {
            hint.innerHTML = `Upload a <a href="resume.html">resume</a> first to run the career agent.`;
        } else {
            hint.textContent = 'Runs resume, job discovery, matching, career advisor and application advisor.';
        }
        footer.appendChild(hint);
    }

    // ─── Orchestration request (one user action → one request) ──────────────
    async function run(candidateId, jobId, modal) {
        if (requestInFlight) return;
        if (!candidateId) {
            renderMissingResult(modal);
            return;
        }

        requestInFlight = true;
        const body = modal.querySelector('#agentModalBody');
        const runBtn = modal.querySelector('.btn-run-agent');
        if (runBtn) runBtn.disabled = true;

        body.innerHTML = `
            <div class="agent-run-loading">
                <span class="processing-spinner"></span>
                <span>Running the career agent orchestration…</span>
            </div>`;

        const payload = { candidateId: Number(candidateId), jobId: String(jobId) };

        try {
            const response = await fetch(ORCHESTRATION_API, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const detail = await safeError(response);
                if (response.status === 404) {
                    // The stored candidate is gone — clear stale id and guide the user.
                    if (String(detail.title || '').indexOf('Candidate') !== -1
                        || String(detail.title || '').indexOf('Profile') !== -1) {
                        localStorage.removeItem(LS_CANDIDATE_ID);
                        localStorage.removeItem(LS_CANDIDATE_NAME);
                    }
                    body.innerHTML = buildErrorResult('Not found — the saved candidate profile or job no longer exists. Upload your resume again if needed.');
                } else {
                    body.innerHTML = buildErrorResult(
                        response.status >= 500
                            ? 'An unexpected error occurred. Please try again later.'
                            : (detail.detail || detail.title || `Request failed (${response.status}).`));
                }
                return;
            }

            const data = await response.json();
            body.innerHTML = buildResultsHtml(data);
        } catch (err) {
            console.error('Career agent orchestration error:', err);
            body.innerHTML = buildErrorResult('Could not reach the career agent. Check the connection and try again.');
        } finally {
            requestInFlight = false;
            if (runBtn) runBtn.disabled = false;
        }
    }

    // ─── Safe error mapping ─────────────────────────────────────────────────
    async function safeError(response) {
        try {
            return await response.json();
        } catch (_) {
            return {};
        }
    }

    // ─── Results rendering (safe fields only) ───────────────────────────────
    function buildResultsHtml(data) {
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
            return `
                <li class="agent-execution agent-exec-${tone}">
                    <span class="agent-exec-dot" aria-hidden="true"></span>
                    <div class="agent-exec-main">
                        <span class="agent-exec-name">${esc(AGENT_LABELS[agent.agentType] || printable(agent.agentType))}</span>
                        <span class="agent-exec-msg">${esc(agent.message || '')} ${errNote}</span>
                    </div>
                    <span class="agent-exec-status ${tone === 'good' ? 'agent-status-good' : (tone === 'skip' ? 'agent-status-skip' : 'agent-status-bad')}">${esc(state)}</span>
                </li>`;
        }).join('');

        const summaryLine = success
            ? 'The career agent completed its controlled run.'
            : 'The career agent finished with issues — review the details below.';

        return `
            <div class="agent-run-summary agent-summary-${statusCls}">
                <span class="agent-summary-status">${esc(statusLabel)}</span>
                <span class="agent-summary-message">${esc(data.message || summaryLine)}</span>
            </div>

            <div class="agent-usage-row">
                <div class="agent-usage-cell"><span class="agent-usage-num">${Number(data.aiCallsUsed) || 0}</span><span class="agent-usage-label">AI calls</span></div>
                <div class="agent-usage-cell"><span class="agent-usage-num">${Number(data.toolCallsUsed) || 0}</span><span class="agent-usage-label">Tool calls</span></div>
                <div class="agent-usage-cell"><span class="agent-usage-num">${esc(printable(data.stoppingAgentType) || '—')}</span><span class="agent-usage-label">Stopped at</span></div>
            </div>

            <div class="agent-executions-head">
                <h4 class="agent-section-title">Agent Executions</h4>
            </div>
            ${executions
                ? `<ul class="agent-executions-list">${executions}</ul>`
                : `<p class="modal-hint">No agent executions were reported for this run.</p>`}

            <p class="agent-note">The application advisor preparing material does not send email or submit applications. Sending in this platform only ever happens through the explicit, approval-gated application review flow.</p>
        `;
    }

    function buildErrorResult(message) {
        return `
            <div class="agent-run-summary agent-summary-bad">
                <span class="agent-summary-status">Error</span>
                <span class="agent-summary-message">${esc(message)}</span>
            </div>`;
    }

    function renderMissingResult(modal) {
        modal.querySelector('#agentModalBody').innerHTML = buildErrorResult(
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
