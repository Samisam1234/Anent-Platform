/**
 * AI Job Agent — shared Job Details modal + source-aware job action logic.
 *
 * Used by jobs.html (Job Search) and matches.html (Resume → Job Matching).
 *
 * The normal flow serves live listings only (MockJobSource is not registered as a
 * bean unless job-sources.mock.enabled=true). A listing with a valid http(s)
 * sourceUrl gets a safe "View Original Listing" link that opens the real external
 * posting; a listing without one gets an internal "Job Details" action. The
 * isMock() guard stays as a defensive check so a development listing can never be
 * opened externally, but no mock wording is ever shown to the user.
 */

(() => {
    'use strict';

    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';
    const MATCH_API = '/api/v1/jobs/match';

    const REC_MAP = {
        EXCELLENT_MATCH: { cls: 'rec-excellent', label: 'Excellent Match' },
        STRONG_MATCH: { cls: 'rec-strong', label: 'Strong Match' },
        POSSIBLE_MATCH: { cls: 'rec-possible', label: 'Possible Match' },
        WEAK_MATCH: { cls: 'rec-weak', label: 'Weak Match' },
        POOR_MATCH: { cls: 'rec-poor', label: 'Poor Match' }
    };

    // Jobs handed to the modal are stored here keyed by id so card buttons only
    // need a data-job-id attribute (no fragile embedded JSON).
    const jobRegistry = new Map();

    let currentJob = null;
    let matchInFlight = false;

    // ─── Source-aware action metadata ───────────────────────────────────────
    function isMock(job) {
        return !!job && job.source === 'MOCK_SOURCE';
    }

    function hasValidUrl(job) {
        const url = job && job.sourceUrl ? String(job.sourceUrl).trim() : '';
        return /^https?:\/\//i.test(url);
    }

    function actionLabel(job) {
        if (isMock(job)) return 'View Details';
        return hasValidUrl(job) ? 'View Original Listing' : 'Job Details';
    }

    // ─── Modal lifecycle ────────────────────────────────────────────────────
    // Uses the single global modal shell: this module never creates its own overlay,
    // so a Job Details dialog cannot stack behind or on top of another analysis.
    function open(job) {
        if (!job) return;
        // Card payloads are display caches; the canonical record comes from the API.
        if (job.id) {
            fetch(`/api/v1/jobs/${encodeURIComponent(job.id)}`)
                .then(response => response.ok ? response.json() : job)
                .then(render)
                .catch(() => render(job));
            return;
        }
        render(job);
    }

    function render(job) {
        if (!job) return;
        currentJob = job;

        window.modalShell.open({
            kicker: job.source || 'Listing',
            title: job.title || 'Untitled Role',
            subtitle: job.company || 'Unknown Company',
            body: buildBodyHtml(job),
            footer: buildFooterHtml(job)
        });
    }

    function closeModal() {
        window.modalShell.close();
    }

    // ─── Modal content ──────────────────────────────────────────────────────
    function buildBodyHtml(job) {
        const reqSkillTags = (job.requiredSkills || [])
            .map(s => `<span class="skill-tag required-skill">${esc(s)}</span>`).join('');
        const prefSkillTags = (job.preferredSkills || [])
            .map(s => `<span class="skill-tag preferred-skill">${esc(s)}</span>`).join('');

        return `
            <div class="job-meta-row">
                <div class="job-meta-item" title="Location">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>
                    <span>${esc(job.location || 'Location Not Specified')}</span>
                </div>
                <div class="job-meta-item" title="Experience Requirement">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
                    <span>${esc(job.experienceRequirement || 'Not Specified')}</span>
                </div>
                <div class="job-meta-item" title="Employment Type">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                    <span>${esc(formatEmploymentType(job.employmentType))}</span>
                </div>
                <div class="job-meta-item" title="Date Posted">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>
                    <span>${esc(formatDate(job.postingDate))}</span>
                </div>
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Description</h4>
                <p class="modal-description">${esc(job.description || 'No description available.')}</p>
            </div>

            ${reqSkillTags ? `
                <div class="modal-job-section">
                    <h4 class="modal-section-title">Required Skills</h4>
                    <div class="skills-tags-wrap">${reqSkillTags}</div>
                </div>` : ''}

            ${prefSkillTags ? `
                <div class="modal-job-section">
                    <h4 class="modal-section-title">Preferred Skills</h4>
                    <div class="skills-tags-wrap">${prefSkillTags}</div>
                </div>` : ''}

            <div class="job-meta-row modal-source-row">
                <div class="job-meta-item" title="Listing Source">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>
                    <span>Source: ${esc(job.source || 'UNKNOWN')}</span>
                </div>
            </div>

            <div id="jobDetailsModalMatch" hidden></div>
        `;
    }

    /**
     * Footer actions. The external link is the original listing on its source, and the
     * employer application is offered only when the source actually supplied one.
     */
    function buildFooterHtml(job) {
        const candidateId = localStorage.getItem(LS_CANDIDATE_ID);
        const candidateName = localStorage.getItem(LS_CANDIDATE_NAME);
        const parts = [];

        // Resolved centrally by jobLink.js. This previously called the local
        // hasValidUrl(job) helper with a URL string instead of a job, so the employer
        // branch could never be taken and the button silently degraded to the listing.
        if (window.jobLink) {
            parts.push(window.jobLink.actionHtml(job, { withNote: false }));
        }

        parts.push(candidateId
            ? `<button type="button" class="btn-primary" data-open-matches-for="${esc(job.id || '')}">Check Match</button>`
            : `<button type="button" class="btn-primary" disabled title="Upload a resume first to check your match strength.">Check Match</button>`);

        const chip = candidateName
            ? `<span class="summary-chip">Profile: ${esc(candidateName)}</span>`
            : `<span class="summary-chip">No profile uploaded</span>`;
        parts.push(`<span class="modal-hint">${chip}</span>`);
        return parts.join('');
    }

    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-open-matches-for]');
        if (!btn) return;
        const jobId = btn.getAttribute('data-open-matches-for');
        if (!jobId) return;
        window.location.href = buildMatchesUrl(jobId);
    });

    // ─── Check Match → Matches page (Phase 6.10) ────────────────────────────
    // The dedicated Matches portal owns scoring. Check Match navigates to
    // matches.html?jobId=<id> so the selected job survives navigation and is
    // highlighted there. Candidate context stays in localStorage; nothing here
    // clears it.
    function buildMatchesUrl(jobId) {
        return 'matches.html?jobId=' + encodeURIComponent(String(jobId));
    }

    function navigateToMatches(job) {
        const jobId = job && job.id ? String(job.id).trim() : '';
        if (!jobId) {
            const matchArea = fetchMatchArea();
            if (matchArea) {
                matchArea.hidden = false;
                matchArea.innerHTML = '<div class="modal-match-error">This listing has no job id, so it cannot be opened in Matches.</div>';
            }
            return;
        }
        window.location.href = buildMatchesUrl(jobId);
    }

    // ─── Check Match (deterministic scoring for this single job) ────────────
    // Legacy inline scorer, kept for reference. The Check Match button now
    // navigates to the Matches page instead of rendering here.
    async function runCheckMatch(job, candidateId, btn) {
        if (matchInFlight) return;
        matchInFlight = true;
        btn.disabled = true;
        btn.textContent = 'Scoring…';

        const matchArea = fetchMatchArea();
        matchArea.hidden = false;
        matchArea.innerHTML = `
            <div class="modal-match-loading">
                <span class="processing-spinner"></span>
                <span>Scoring this job against the saved profile…</span>
            </div>`;

        const payload = {
            candidateProfileId: candidateId,
            jobs: [job],
            limit: 1
        };

        try {
            const response = await fetch(MATCH_API, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                let errTitle = `Match request failed (${response.status})`;
                try {
                    const errJson = await response.json();
                    errTitle = errJson.detail || errJson.title || errTitle;
                } catch (_) { /* keep status fallback */ }

                if (response.status === 404) {
                    localStorage.removeItem(LS_CANDIDATE_ID);
                    localStorage.removeItem(LS_CANDIDATE_NAME);
                    throw new Error('Saved profile not found. Please upload your resume again.');
                }
                throw new Error(errTitle);
            }

            const data = await response.json();
            const match = data.matches && data.matches[0];
            if (!match) {
                matchArea.innerHTML = `<div class="modal-hint">No match score could be computed for this job.</div>`;
                return;
            }
            matchArea.innerHTML = buildMatchHtml(match);
        } catch (err) {
            console.error('Check Match error:', err);
            matchArea.innerHTML = `<div class="modal-match-error">${esc(window.apiError.describe(err, 'Failed to check match.'))}</div>`;
        } finally {
            matchInFlight = false;
            btn.disabled = false;
            btn.textContent = 'Check Match';
        }
    }

    function fetchMatchArea() {
        return document.getElementById('jobDetailsModalMatch');
    }

    function buildMatchHtml(m) {
        const rec = REC_MAP[m.recommendation] || {
            cls: 'rec-possible',
            label: (m.recommendation || 'POSSIBLE_MATCH').replace(/_/g, ' ').toLowerCase()
        };

        const score = Number(m.matchScore) || 0;
        const reqHit = (m.matchedSkills || []).map(s => `<span class="skill-tag required-skill match-hit">${esc(s)}</span>`).join('');
        const reqMiss = (m.missingSkills || []).map(s => `<span class="skill-tag required-skill match-miss">${esc(s)}</span>`).join('');
        const prefHit = (m.matchedPreferredSkills || []).map(s => `<span class="skill-tag preferred-skill match-hit">${esc(s)}</span>`).join('');
        const prefMiss = (m.missingPreferredSkills || []).map(s => `<span class="skill-tag preferred-skill match-miss">${esc(s)}</span>`).join('');

        const strengths = (m.strengths || []).map(s =>
            `<li class="match-strength"><span class="match-strength-icon">✓</span>${esc(s)}</li>`).join('');
        const concerns = (m.concerns || []).map(c =>
            `<li class="match-concern"><span class="match-concern-icon">!</span>${esc(c)}</li>`).join('');

        const trackLabel = (m.careerTrack || 'UNKNOWN').replace(/_/g, ' ').toLowerCase();

        return `
            <div class="modal-match-result">
                <div class="modal-match-head">
                    <span class="modal-match-score">
                        <strong>${score}</strong><span>/100</span>
                    </span>
                    <span class="match-rec-badge ${rec.cls}">${esc(rec.label)}</span>
                    <span class="summary-chip">${esc(trackLabel)}</span>
                </div>
                <p class="modal-match-explanation">${esc(m.explanation || '')}</p>

                ${reqHit ? `
                    <h4 class="modal-section-title modal-title-good">✓ Required Matched</h4>
                    <div class="skills-tags-wrap">${reqHit}</div>` : ''}
                ${reqMiss ? `
                    <h4 class="modal-section-title modal-title-bad">Missing Required</h4>
                    <div class="skills-tags-wrap">${reqMiss}</div>` : ''}
                ${prefHit ? `
                    <h4 class="modal-section-title modal-title-good">Preferred Matched</h4>
                    <div class="skills-tags-wrap">${prefHit}</div>` : ''}
                ${prefMiss ? `
                    <h4 class="modal-section-title modal-title-bad">Missing Preferred</h4>
                    <div class="skills-tags-wrap">${prefMiss}</div>` : ''}

                ${strengths ? `
                    <h4 class="modal-section-title modal-title-good">Strengths</h4>
                    <ul class="modal-match-list">${strengths}</ul>` : ''}
                ${concerns ? `
                    <h4 class="modal-section-title modal-title-bad">Concerns</h4>
                    <ul class="modal-match-list">${concerns}</ul>` : ''}
            </div>
        `;
    }

    // ─── Utilities ──────────────────────────────────────────────────────────
    function formatEmploymentType(type) {
        if (!type) return 'Full-time';
        switch (type.toUpperCase()) {
            case 'FULL_TIME': return 'Full-time';
            case 'INTERNSHIP': return 'Internship';
            case 'CONTRACT': return 'Contract';
            case 'PART_TIME': return 'Part-time';
            default: return type;
        }
    }

    function formatDate(dateStr) {
        if (!dateStr) return 'Recently';
        try {
            const date = new Date(dateStr);
            if (isNaN(date.getTime())) return dateStr;
            return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
        } catch (_) {
            return dateStr;
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

    // ─── Delegated click handling for card buttons ──────────────────────────
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-open-job-details]');
        if (!btn) return;
        const job = jobRegistry.get(btn.getAttribute('data-job-id'));
        if (job) open(job);
    });

    // ─── Public API (consumed by jobs.js / matches.js) ──────────────────────
    window.jobDetails = {
        open: open,
        actionLabel: actionLabel,
        isMock: isMock,
        hasValidUrl: hasValidUrl,
        buildMatchesUrl: buildMatchesUrl,
        register: (job) => {
            if (job && job.id) jobRegistry.set(job.id, job);
        }
    };
})();