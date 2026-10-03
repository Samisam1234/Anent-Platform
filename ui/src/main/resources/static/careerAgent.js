/**
 * AI Job Agent — Career Analysis.
 *
 * Presents a career decision report for one candidate against one job, built from the
 * deterministic Application Advisor (POST /api/v1/jobs/advisor) plus the job record
 * (GET /api/v1/jobs/{id}). Every number shown is a score the backend already computed
 * from the parsed profile and the listing; nothing is estimated here and no section is
 * filled in to look complete — unavailable data says so.
 *
 * It renders through the single global modal shell, so it can never stack behind or on
 * top of the Match Details dialog that launched it.
 *
 * Deliberately NOT shown: orchestration telemetry (AI call counts, tool call counts,
 * stopping agent, per-stage execution statuses). The agent pipeline still exists at
 * POST /api/v1/agent/orchestrate; it is implementation detail, not a career result, so
 * the user sees "Analysis completed" instead.
 *
 * It never displays raw AgentContext, resume text, tool arguments or internal
 * exceptions, and it never treats an analysis as an email sent or an application
 * submitted — only the explicit approval flow does that.
 */
(() => {
    'use strict';

    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';
    const ADVISOR_API = '/api/v1/jobs/advisor';
    const JOB_API = '/api/v1/jobs/';

    /**
     * User-facing fit bands. Labels describe the outcome; the underlying thresholds are
     * the backend's, unchanged here.
     */
    /**
     * Labelled progress bar for the headline score, using the same .fit-bar markup and
     * green/yellow/red tones as the factor rows in Match Details and Application
     * Readiness, so every dialog visualizes a score identically.
     */
    function scoreBarHtml(score, fit) {
        const pct = Number(score);
        if (!Number.isFinite(pct)) return '';
        const clamped = Math.max(0, Math.min(100, pct));
        const tone = fit && fit.cls === 'fit-high' ? 'is-good'
            : fit && fit.cls === 'fit-medium' ? 'is-caution'
            : fit && fit.cls === 'fit-low' ? 'is-gap' : '';
        return `<span class="fit-bar" role="img" aria-label="Career fit ${clamped} out of 100">
            <span class="fit-bar-fill ${tone}" style="width:${clamped}%"></span></span>`;
    }

    const FIT_LABELS = {
        STRONGLY_RECOMMENDED: { label: 'Strong fit', cls: 'fit-high' },
        RECOMMENDED: { label: 'Good fit', cls: 'fit-high' },
        APPLY_WITH_IMPROVEMENTS: { label: 'Borderline', cls: 'fit-medium' },
        LOW_PRIORITY: { label: 'Weak fit', cls: 'fit-low' },
        NOT_RECOMMENDED: { label: 'Not recommended', cls: 'fit-low' }
    };

    let inFlight = false;
    let activeCandidateId = null;
    let activeJobId = null;

    // ─── Entry point ────────────────────────────────────────────────────────
    function open(candidateId, candidateName, job) {
        if (inFlight || !job || !job.id) return;

        activeCandidateId = candidateId;
        activeJobId = job.id;

        window.modalShell.open({
            kicker: 'Career Analysis',
            title: job.title || 'Selected role',
            subtitle: subtitleFor(candidateName, job),
            body: loadingHtml(),
            footer: footerHtml(candidateId)
        });

        if (!candidateId) {
            window.modalShell.setBody(errorHtml(
                'Upload your resume first — career analysis compares your parsed profile against this listing.'));
            return;
        }

        run(candidateId, job.id, job);
    }

    function subtitleFor(candidateName, job) {
        const company = job.company ? ` at ${job.company}` : '';
        return candidateName
            ? `${candidateName} · ${job.title || 'role'}${company}`
            : `No profile · ${job.title || 'role'}${company}`;
    }

    function footerHtml(candidateId) {
        if (!candidateId) {
            return `<span class="modal-hint">Upload a <a href="resume.html">resume</a> to run career analysis.</span>`;
        }
        return `<span class="modal-hint">Calculated from your parsed resume and this listing.</span>
                <button type="button" class="btn-primary" data-career-analysis-retry="true">Run again</button>`;
    }

    function loadingHtml() {
        return `
            <div class="agent-run-loading">
                <span class="processing-spinner"></span>
                <span>Analysing your profile against this role…</span>
            </div>`;
    }

    function errorHtml(message) {
        return `
            <div class="agent-run-summary agent-summary-bad">
                <span class="agent-summary-status">Analysis unavailable</span>
                <span class="agent-summary-message">${esc(message)}</span>
            </div>`;
    }

    // ─── Data ───────────────────────────────────────────────────────────────
    async function run(candidateId, jobId, launchJob) {
        inFlight = true;
        try {
            const [advisor, job] = await Promise.all([
                fetchJson(ADVISOR_API, { candidateId: Number(candidateId), jobId: String(jobId) }),
                // The listing lookup only supplies header context (location, employment
                // type). The index holds a limited window of live jobs, so a miss must
                // not suppress the analysis the advisor already produced.
                fetchJson(JOB_API + encodeURIComponent(jobId)).catch(err => {
                    console.warn('Listing not retrievable for header context:', err);
                    return null;
                })
            ]);

            // Ignore a late response if the user has since opened another dialog.
            if (!window.modalShell.isOpen() || activeJobId !== jobId) return;
            window.modalShell.setBody(reportHtml(advisor, job, launchJob));
        } catch (err) {
            console.error('Career analysis error:', err);
            if (window.modalShell.isOpen() && activeJobId === jobId) {
                // Named, contextual wording rather than a bare exception message: the
                // server logs the real cause, the user gets something actionable.
                window.modalShell.setBody(errorHtml(window.apiError.describe(err,
                    'Could not load Career Analysis. The job listing data may be incomplete, '
                    + 'or the analysis service is temporarily unavailable. Please try again.')));
            }
        } finally {
            inFlight = false;
        }
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

    // ─── Report ─────────────────────────────────────────────────────────────
    /**
     * Builds the report from the advisor response. Each section renders only when the
     * backing data exists, and says "Not available" otherwise — no placeholder scores.
     */
    function reportHtml(advisor, job, launchJob) {
        const adv = advisor || {};
        const b = adv.scoreBreakdown || null;
        const jobData = Object.assign({}, launchJob || {}, job || {});

        const readiness = Number.isFinite(adv.applicationReadinessScore)
            ? adv.applicationReadinessScore : null;
        const fit = FIT_LABELS[adv.recommendation] || null;

        const matched = (b && b.matchedRequiredSkills) || [];
        const missing = (b && b.missingRequiredSkills) || [];
        const missingPreferred = (b && b.missingPreferredSkills) || [];
        const alsoMatched = (b && b.matchedPreferredSkills) || [];
        const strengths = narrativeLines(adv.strengths, matched, null);
        const risks = narrativeLines(adv.concerns, missing, missingPreferred);

        return `
            <div class="analysis-complete">Analysis completed using your resume and this job listing.</div>

            <section class="career-report">
                <header class="career-report-head">
                    <div class="career-report-role">
                        <h4 class="career-report-title">${esc(jobData.title || adv.jobTitle || 'Selected role')}</h4>
                        <div class="career-report-company">${esc(jobData.company || adv.company || 'Company not listed')}</div>
                        <div class="career-report-meta">
                            <span class="summary-chip">${esc(jobData.location || 'Location not listed')}</span>
                            <span class="summary-chip">${esc(formatEmploymentType(jobData.employmentType))}</span>
                            <span class="summary-chip">${esc(jobData.experienceRequirement || 'Experience: not specified')}</span>
                        </div>
                    </div>
                    <div class="career-report-score">
                        <span class="fit-label">Career fit</span>
                        <span class="fit-value">${readiness == null ? '—' : esc(readiness)}<small>/100</small></span>
                        ${scoreBarHtml(readiness, fit)}
                        ${fit ? `<span class="fit-badge ${fit.cls}">${esc(fit.label)}</span>` : ''}
                    </div>
                </header>

                ${section('Why', adv.recommendationExplanation
                    ? `<p class="career-report-text">${esc(adv.recommendationExplanation)}</p>`
                    : notAvailable())}

                ${section('Skill fit', skillFitHtml(b, matched, missing, missingPreferred, alsoMatched))}

                ${section('Experience fit', b ? experienceHtml(b) : notAvailable())}

                ${section('Education fit', educationHtml(b, jobData))}

                ${section('Location fit', locationHtml(b, jobData))}

                ${section('Career track', b ? trackHtml(b) : notAvailable())}

                ${section('Strengths', strengths.length
                    ? `<ul class="fit-list is-good">${strengths.map(s => `<li>${esc(s)}</li>`).join('')}</ul>`
                    : notAvailable('No further strengths beyond the matched skills above.'))}

                ${section('Risks and gaps', risks.length
                    ? `<ul class="fit-list is-bad">${risks.map(s => `<li>${esc(s)}</li>`).join('')}</ul>`
                    : notAvailable('No gaps were identified for this role.'))}

                ${section('Next steps', (adv.recommendedActionDetails && adv.recommendedActionDetails.length)
                    ? nextStepsHtml(adv.recommendedActionDetails)
                    : notAvailable('Nothing to close for this role — your profile covers what it asks for.'))}

                <p class="agent-note">
                    Fit is calculated from your parsed resume and this listing: required-skill coverage,
                    role, experience, education, location and career-track factors. Career fit is
                    60% resume readiness + 40% job match. No text here is generated by the AI model.
                </p>
            </section>`;
    }

    function section(title, bodyHtml) {
        return `
            <div class="career-report-section">
                <h5 class="career-report-section-title">${esc(title)}</h5>
                ${bodyHtml}
            </div>`;
    }

    function notAvailable(fallback) {
        return `<p class="career-report-na">${esc(fallback || 'Not available')}</p>`;
    }

    function skillFitHtml(b, matched, missing, missingPreferred, alsoMatched) {
        if (!b) return notAvailable();

        const chips = (list, cls) => list
            .map(s => `<span class="skill-tag ${cls}">${esc(s)}</span>`).join('');
        const parts = [];

        parts.push(`<p class="career-report-note">Required: ${esc(b.matchedRequiredCount)} matched, ${esc(b.missingRequiredCount)} missing. Preferred: ${esc(b.matchedPreferredCount)} matched, ${esc(b.missingPreferredCount)} missing.</p>`);

        if (matched.length) {
            parts.push(`<div class="analysis-sub">Matched required</div><div class="skills-tags-wrap">${chips(matched, 'required-skill match-hit')}</div>`);
        }
        if (missing.length) {
            parts.push(`<div class="analysis-sub">Missing required</div><div class="skills-tags-wrap">${chips(missing, 'required-skill match-miss')}</div>`);
        }
        if (alsoMatched.length) {
            parts.push(`<div class="analysis-sub">Preferred skills you also have</div><div class="skills-tags-wrap">${chips(alsoMatched, 'preferred-skill match-hit')}</div>`);
        }
        if (missingPreferred.length) {
            parts.push(`<div class="analysis-sub">Preferred skills you do not have</div><div class="skills-tags-wrap">${chips(missingPreferred, 'preferred-skill match-miss')}</div>`);
        }
        if (parts.length === 1) {
            parts.push(notAvailable('This listing states no required or preferred skills.'));
        }
        return parts.join('');
    }

    /**
     * Experience sentence. A shortfall is stated only when both sides carried structured
     * years; otherwise it says the comparison could not be made rather than guessing.
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
            } Experience factor ${esc(fit)}/100.</p>`;
        }
        if (required === 0) {
            return `<p class="career-report-text">This role is entry level, so no minimum years apply. Experience factor ${esc(fit)}/100.</p>`;
        }
        if (required != null && candidate != null) {
            const short = required - candidate;
            const verdict = short > 0
                ? `You are ${esc(short)} year${short === 1 ? '' : 's'} short of the stated requirement.`
                : 'Your stated experience meets or exceeds the requirement.';
            return `<p class="career-report-text">Your resume shows ${esc(candidate)} year${candidate === 1 ? '' : 's'}; this listing asks for ${esc(required)}. ${esc(verdict)} Experience factor ${esc(fit)}/100.</p>`;
        }
        return notAvailable();
    }

    /**
     * Education fit. The job model carries no education requirement, so the requirement
     * side is reported as not stated rather than invented.
     */
    function educationHtml(b, jobData) {
        if (!b) return notAvailable();
        return `<p class="career-report-text">Education factor ${esc(b.educationFitScore)}/100. `
            + `This listing does not state an education requirement${
                jobData && jobData.experienceRequirement ? '' : ''
            }, and your stored education entries are not part of this comparison.</p>`;
    }

    function locationHtml(b, jobData) {
        if (!b) return notAvailable();
        const where = jobData && jobData.location ? esc(jobData.location) : 'not listed';
        return `<p class="career-report-text">Listing location: ${where}. Location factor ${esc(b.locationFitScore)}/100.</p>`;
    }

    function trackHtml(b) {
        const candidate = humanize(b.candidateTrack);
        const jobTrack = humanize(b.jobTrack);
        if (!candidate || !jobTrack) {
            return notAvailable('Career track could not be determined on both sides.');
        }
        const badge = b.trackMismatch
            ? `<span class="fit-badge fit-low">Different tracks</span>`
            : `<span class="fit-badge fit-high">Compatible</span>`;
        return `<p class="career-report-text">Your profile reads as <strong>${esc(candidate)}</strong>; this role reads as <strong>${esc(jobTrack)}</strong>. ${badge} Track factor ${esc(b.trackFitScore)}/100.</p>`;
    }

    function nextStepsHtml(details) {
        return `<ol class="career-next-steps">${details.map(d => `
            <li>
                <span class="career-step-focus">${esc(stepTitle(d))}</span>
                ${d.description ? `<span class="career-step-desc">${esc(d.description)}</span>` : ''}
            </li>`).join('')}</ol>`;
    }

    /**
     * Turns an improvement priority into an instruction. The backend's machine-readable
     * type (REQUIRED_SKILL / PREFERRED_SKILL / EXPERIENCE) is mapped to wording here so
     * the internal label never reaches the screen.
     */
    function stepTitle(detail) {
        const focus = detail && detail.focus ? String(detail.focus) : 'Improvement';
        switch (detail && detail.type) {
            case 'REQUIRED_SKILL': return 'Learn ' + focus;
            case 'PREFERRED_SKILL': return 'Review ' + focus;
            case 'EXPERIENCE': return 'Build relevant experience';
            default: return focus;
        }
    }

    /**
     * Keeps only the narrative lines of the advisor's strengths/concerns. Skill names are
     * already shown as chips, so a line that merely names a skill already displayed (with
     * or without its evidence suffix) is dropped — an exact comparison against structured
     * data, not pattern matching, so nothing is shown twice.
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

    // ─── Delegated triggers ─────────────────────────────────────────────────
    document.addEventListener('click', (e) => {
        const retry = e.target.closest('[data-career-analysis-retry]');
        if (retry) {
            if (activeCandidateId && activeJobId && !inFlight) {
                window.modalShell.setBody(loadingHtml());
                run(activeCandidateId, activeJobId, null);
            }
            return;
        }

        const btn = e.target.closest('[data-career-agent]');
        if (!btn) return;
        const jobId = btn.getAttribute('data-job-id');
        if (!jobId) return;
        open(localStorage.getItem(LS_CANDIDATE_ID), localStorage.getItem(LS_CANDIDATE_NAME), {
            id: jobId,
            title: btn.getAttribute('data-job-title'),
            company: btn.getAttribute('data-company')
        });
    });

    window.careerAgent = {
        open: open,
        close: () => window.modalShell.close()
    };
})();
