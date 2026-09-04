/**
 * AI Job Agent — shared Job Details modal + source-aware job action logic.
 *
 * Used by jobs.html (Job Search) and matches.html (Resume → Job Matching).
 *
 * Mock-source listings come from the development MockJobSource and their
 * sourceUrl points at a placeholder domain (mockjobs.local) that is NOT a real
 * website — they must never be opened externally. For those listings the card
 * action becomes an internal "View Details" modal instead. Live sources with a
 * valid http(s) URL keep a safe "View Original Listing" link; live sources
 * without a URL show an internal "Job Details" action.
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

    let modalOverlay = null;
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
    function ensureModal() {
        if (modalOverlay) return modalOverlay;

        modalOverlay = document.createElement('div');
        modalOverlay.className = 'modal-overlay';
        modalOverlay.hidden = true;
        modalOverlay.innerHTML = `
            <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="jobDetailsModalTitle">
                <div class="modal-header">
                    <div class="modal-header-text">
                        <span class="job-source-badge mock" id="jobDetailsModalSource" hidden></span>
                        <h3 class="modal-title" id="jobDetailsModalTitle">Job Details</h3>
                        <div class="modal-company" id="jobDetailsModalCompany"></div>
                    </div>
                    <button type="button" class="modal-close" id="jobDetailsModalClose" aria-label="Close job details">&times;</button>
                </div>
                <div class="modal-body" id="jobDetailsModalBody"></div>
                <div class="modal-footer" id="jobDetailsModalFooter"></div>
            </div>
        `;

        modalOverlay.addEventListener('click', (e) => {
            if (e.target === modalOverlay) closeModal();
        });
        modalOverlay.querySelector('#jobDetailsModalClose').addEventListener('click', closeModal);
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && !modalOverlay.hidden) closeModal();
        });

        document.body.appendChild(modalOverlay);
        return modalOverlay;
    }

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

        const modal = ensureModal();
        const sourceBadge = modal.querySelector('#jobDetailsModalSource');
        sourceBadge.className = isMock(job) ? 'job-source-badge mock' : 'job-source-badge live';
        sourceBadge.textContent = job.source || 'SOURCE';
        sourceBadge.hidden = false;

        modal.querySelector('#jobDetailsModalTitle').textContent = job.title || 'Untitled Role';
        modal.querySelector('#jobDetailsModalCompany').textContent = job.company || 'Unknown Company';
        modal.querySelector('#jobDetailsModalBody').innerHTML = buildBodyHtml(job);
        modal.querySelector('#jobDetailsModalFooter').innerHTML = '';
        renderFooter(job);

        modal.hidden = false;
        document.body.classList.add('modal-open');
        modal.querySelector('#jobDetailsModalClose').focus();
    }

    function closeModal() {
        if (!modalOverlay || modalOverlay.hidden) return;
        modalOverlay.hidden = true;
        document.body.classList.remove('modal-open');
        currentJob = null;
    }

    // ─── Modal content ──────────────────────────────────────────────────────
    function buildBodyHtml(job) {
        const reqSkillTags = (job.requiredSkills || [])
            .map(s => `<span class="skill-tag required-skill">${esc(s)}</span>`).join('');
        const prefSkillTags = (job.preferredSkills || [])
            .map(s => `<span class="skill-tag preferred-skill">${esc(s)}</span>`).join('');

        const mockNotice = isMock(job)
            ? `
            <div class="modal-mock-notice">
                <strong>Development Mock Job</strong>
                <span>External application link is not available because this listing comes from the development MockJobSource.</span>
            </div>`
            : '';

        return `
            ${mockNotice}

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
                    <span>Source: ${esc(job.source || 'UNKNOWN')}${isMock(job) ? ' (development mock data)' : ''}</span>
                </div>
            </div>

            <div id="jobDetailsModalMatch" hidden></div>
        `;
    }

    function renderFooter(job) {
        const footer = modalOverlay.querySelector('#jobDetailsModalFooter');

        if (!isMock(job) && hasValidUrl(job)) {
            const link = document.createElement('a');
            link.className = 'btn-view-job';
            link.href = job.sourceUrl;
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            link.textContent = 'View Original Listing';
            footer.appendChild(link);
        }

        const checkBtn = document.createElement('button');
        checkBtn.type = 'button';
        checkBtn.className = 'btn-primary btn-check-match';
        checkBtn.textContent = 'Check Match';

        const candidateId = localStorage.getItem(LS_CANDIDATE_ID);
        const candidateName = localStorage.getItem(LS_CANDIDATE_NAME);
        if (candidateId) {
            checkBtn.addEventListener('click', () => runCheckMatch(job, Number(candidateId), checkBtn));
        } else {
            checkBtn.addEventListener('click', () => {
                const matchArea = fetchMatchArea();
                matchArea.hidden = false;
                matchArea.innerHTML = '';
                const hint = document.createElement('span');
                hint.className = 'modal-hint';
                hint.innerHTML = `Upload a <a href="resume.html">resume</a> first to check your match strength against this job.`;
                matchArea.appendChild(hint);
            });
            checkBtn.classList.add('disabled');
            checkBtn.setAttribute('title', 'Upload a resume first to check your match strength.');
        }
        footer.appendChild(checkBtn);

        const nameChip = candidateName
            ? `<span class="summary-chip">Profile: ${esc(candidateName)}</span>`
            : `<span class="summary-chip">No profile uploaded</span>`;
        const meta = document.createElement('span');
        meta.className = 'modal-hint';
        meta.innerHTML = nameChip;
        footer.appendChild(meta);
    }

    // ─── Check Match (deterministic scoring for this single job) ────────────
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
            matchArea.innerHTML = `<div class="modal-match-error">${esc(err.message || 'Failed to check match.')}</div>`;
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
        register: (job) => {
            if (job && job.id) jobRegistry.set(job.id, job);
        }
    };
})();