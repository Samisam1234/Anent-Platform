/**
 * AI Job Agent — Milestone 4: Resume → Job Matching Frontend Logic
 * Uses the candidate profile captured from the Resume page (stored candidate id)
 * and the deterministic /api/v1/jobs/match engine to rank jobs for the candidate.
 */

(() => {
    'use strict';

    // ─── Profile storage keys (shared with resume.js) ─────────────────────────
    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';

    // ─── DOM References ───────────────────────────────────────────────────────
    const profileBadgeStatus = document.getElementById('profileStatusText');
    const profileBadge = document.getElementById('profileBadge');

    const candidatePanel = document.getElementById('candidatePanel');
    const candidatePanelTitle = document.getElementById('candidatePanelTitle');
    const candidatePanelDesc = document.getElementById('candidatePanelDesc');
    const uploadCtaBtn = document.getElementById('uploadCtaBtn');

    const matchControls = document.getElementById('matchControls');
    const matchesSearchForm = document.getElementById('matchesSearchForm');
    const locationInput = document.getElementById('matchesLocationInput');
    const trackSelect = document.getElementById('matchesTrackSelect');
    const minScoreSelect = document.getElementById('matchesMinScoreSelect');
    const limitSelect = document.getElementById('matchesLimitSelect');
    const findMatchesBtn = document.getElementById('findMatchesBtn');
    const resetBtn = document.getElementById('matchesResetBtn');
    const emptyResetBtn = document.getElementById('matchesEmptyResetBtn');

    const sourceBanner = document.getElementById('matchesSourceBanner');
    const bannerTitle = document.getElementById('matchesBannerTitle');
    const bannerMessage = document.getElementById('matchesBannerMessage');

    const resultsCount = document.getElementById('matchesResultsCount');
    const resultsCountLabel = document.getElementById('matchesResultsCountLabel');
    const activeSummary = document.getElementById('matchesActiveSummary');
    const loading = document.getElementById('matchesLoading');
    const empty = document.getElementById('matchesEmpty');
    const emptyDesc = document.getElementById('matchesEmptyDesc');
    const cardsGrid = document.getElementById('matchesCardsGrid');
    const toastContainer = document.getElementById('toastContainer');

    const API_ENDPOINT = '/api/v1/jobs/match';
const ADVISOR_API_ENDPOINT = '/api/v1/jobs/advisor';

    let candidateId = localStorage.getItem(LS_CANDIDATE_ID) || null;
    let candidateName = localStorage.getItem(LS_CANDIDATE_NAME) || null;
    let isMatching = false;
    // Selected job carried via matches.html?jobId=<id> from Job Details → Check Match.
    // Query param is the single source of truth; candidate state stays in localStorage.
    let selectedJobId = null;

    function getSelectedJobIdFromUrl() {
        try {
            const params = new URLSearchParams(window.location.search || '');
            const raw = params.get('jobId');
            if (raw === null || raw === undefined) return null;
            const trimmed = String(raw).trim();
            return trimmed ? trimmed : null;
        } catch (_) {
            return null;
        }
    }

    // ─── Init / Profile gate ──────────────────────────────────────────────────
    function init() {
        candidateId = localStorage.getItem(LS_CANDIDATE_ID) || null;
        candidateName = localStorage.getItem(LS_CANDIDATE_NAME) || null;
        selectedJobId = getSelectedJobIdFromUrl();

        if (candidateId) {
            profileBadge.classList.add('status-live');
            profileBadge.querySelector('.status-dot').classList.add('status-dot-online');
            profileBadgeStatus.textContent = candidateName ? `Matching as ${candidateName}` : 'Profile ready';

            candidatePanel.hidden = true;
            uploadCtaBtn.hidden = true;
            matchControls.hidden = false;

            runMatch();
        } else {
            profileBadgeStatus.textContent = 'No profile';
            matchControls.hidden = true;
            loading.hidden = true;
            empty.hidden = true;
            cardsGrid.innerHTML = '';
            sourceBanner.hidden = true;
        }

        // Event wiring (safe regardless of state)
        findMatchesBtn.addEventListener('click', () => runMatch());
        resetBtn.addEventListener('click', () => resetFilters(true));
        if (emptyResetBtn) emptyResetBtn.addEventListener('click', () => resetFilters(true));

        locationInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                runMatch();
            }
        });
    }

    function resetFilters(autoRun = true) {
        locationInput.value = '';
        trackSelect.value = 'ALL';
        minScoreSelect.value = '';
        limitSelect.value = '20';
        activeSummary.innerHTML = '';
        // Reset returns to the normal multi-job view: drop the deep-linked job
        // without touching candidate state.
        selectedJobId = null;
        try {
            const url = new URL(window.location.href);
            if (url.searchParams.has('jobId')) {
                url.searchParams.delete('jobId');
                window.history.replaceState({}, '', url.pathname + (url.search ? url.search : ''));
            }
        } catch (_) { /* keep filters reset even if URL cleanup fails */ }
        if (autoRun) runMatch();
    }

    // ─── Matching ─────────────────────────────────────────────────────────────
    async function runMatch() {
        if (isMatching || !candidateId) return;
        isMatching = true;
        findMatchesBtn.disabled = true;
        findMatchesBtn.classList.add('loading');
        loading.hidden = false;
        empty.hidden = true;
        cardsGrid.innerHTML = '';

        const track = trackSelect.value;
        const minScore = minScoreSelect.value;

        // No free-text keywords: the backend derives relevance keywords from the stored
        // profile's parsed skills, and every engine below already scores against it.
        const payload = {
            candidateProfileId: Number(candidateId),
            location: locationInput.value.trim() || null,
            limit: parseInt(limitSelect.value, 10) || 20,
            minScore: minScore ? Number(minScore) : null,
            careerTrack: track && track !== 'ALL' ? track : null
        };

        try {
            const response = await fetch(API_ENDPOINT, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                let errorTitle = `Matching request failed (${response.status})`;
                try {
                    const errJson = await response.json();
                    errorTitle = errJson.detail || errJson.title || errorTitle;
                } catch (_) { /* keep status fallback */ }

                if (response.status === 404) {
                    // Stored profile no longer exists — reset the local reference.
                    localStorage.removeItem(LS_CANDIDATE_ID);
                    localStorage.removeItem(LS_CANDIDATE_NAME);
                    candidateId = null;
                    profileBadge.classList.remove('status-live');
                    profileBadgeStatus.textContent = 'No profile';
                    candidatePanel.hidden = false;
                    uploadCtaBtn.hidden = false;
                    matchControls.hidden = true;
                    candidatePanelTitle.textContent = 'Profile not found';
                    candidatePanelDesc.textContent = errorTitle;
                    showToast(errorTitle, 'error');
                    return;
                }
                throw new Error(errorTitle);
            }

            const data = await response.json();
            renderMatches(data, payload);
        } catch (err) {
            console.error('Job matching error:', err);
            showToast(err.message || 'Failed to match jobs. Please check backend connection.', 'error');
            loading.hidden = true;
            cardsGrid.innerHTML = '';
            emptyDesc.textContent = 'Matching failed — please try again or check the backend connection.';
            empty.hidden = false;
        } finally {
            isMatching = false;
            findMatchesBtn.disabled = false;
            findMatchesBtn.classList.remove('loading');
            loading.hidden = true;
        }
    }

    // ─── Rendering ────────────────────────────────────────────────────────────
    function renderMatches(data, payload) {
        const matches = data.matches || [];
        const isLive = data.live === true;
        const sourceName = data.source || 'Unknown source';

        // Source banner
        sourceBanner.hidden = false;
        if (isLive) {
            sourceBanner.className = 'source-info-banner source-live';
            bannerTitle.textContent = `Live Job Source Active (${sourceName})`;
        } else {
            sourceBanner.className = 'source-info-banner';
            bannerTitle.textContent = `Live Source Returned No Listings (${sourceName})`;
        }
        bannerMessage.textContent = data.message || 'Matched against the job catalog.';

        // Counts
        resultsCount.textContent = data.totalJobs || 0;
        resultsCountLabel.textContent = (data.totalJobs === 1) ? 'job match' : 'job matches';

        // Active query summary
        activeSummary.innerHTML = '';
        if (candidateName) activeSummary.appendChild(createChip('Profile: ' + candidateName));
        if (payload.location) activeSummary.appendChild(createChip('Location: ' + payload.location));
        if (payload.minScore) activeSummary.appendChild(createChip('Min score: ' + payload.minScore));
        if (payload.careerTrack) activeSummary.appendChild(createChip('Track: ' + payload.careerTrack));

        cardsGrid.innerHTML = '';
        if (matches.length === 0) {
            emptyDesc.textContent = data.totalJobs === 0
                ? 'No jobs were found for the search parameters. Try removing the location filter.'
                : 'No roles cleared the current filters. Try lowering the minimum score or removing the track filter.';
            empty.hidden = false;
            return;
        }

        empty.hidden = true;
        matchRegistry.clear();
        matches.forEach(match => {
            if (match && match.job && match.job.id != null) {
                matchRegistry.set(String(match.job.id), match);
            }
            cardsGrid.appendChild(createMatchCard(match));
        });
        highlightSelectedJob(matches);
    }

    // ─── Selected job (Check Match deep link) ─────────────────────────────────
    // Highlights + focuses the card carried via ?jobId=. A missing/invalid jobId
    // never breaks the page: normal multi-job results still render.
    function highlightSelectedJob(matches) {
        if (!selectedJobId) return;
        if (!Array.isArray(matches) || matches.length === 0) {
            showToast('Selected job is not in the current matches. Showing all matches instead.', 'info');
            return;
        }
        const found = matches.find(m => m && m.job && String(m.job.id) === String(selectedJobId));
        if (!found) {
            showToast('Selected job is not in the current matches. Showing all matches instead.', 'info');
            return;
        }
        const target = Array.from(cardsGrid.children).find(
            el => el && el.getAttribute && el.getAttribute('data-id') === String(selectedJobId)) || null;
        activeSummary.appendChild(createChip('Selected job: ' + String(selectedJobId)));
        if (!target) return;
        target.classList.add('match-card-selected');
        target.setAttribute('tabindex', '-1');
        try {
            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        } catch (_) { /* non-fatal */ }
        try {
            target.focus({ preventScroll: true });
        } catch (_) { /* non-fatal */ }
    }

    function createChip(text) {
        const chip = document.createElement('span');
        chip.className = 'summary-chip';
        chip.textContent = text;
        return chip;
    }

    function skillTag(skill, type, hit) {
        const cls = hit ? 'match-hit' : 'match-miss';
        return `<span class="skill-tag ${type} ${cls}" title="${hit ? 'Matched' : 'Missing'}">${esc(skill)}</span>`;
    }

    function createMatchCard(m) {
        const job = m.job || {};
        const card = document.createElement('article');
        card.className = 'match-card';
        card.setAttribute('data-id', job.id || '');

        const reqHit = (m.matchedSkills || []).map(s => skillTag(s, 'required-skill', true)).join('');
        const reqMiss = (m.missingSkills || []).map(s => skillTag(s, 'required-skill', false)).join('');
        const prefHit = (m.matchedPreferredSkills || []).map(s => skillTag(s, 'preferred-skill', true)).join('');
        const prefMiss = (m.missingPreferredSkills || []).map(s => skillTag(s, 'preferred-skill', false)).join('');

        const strengths = (m.strengths || []).map(s =>
            `<li class="match-strength"><span class="match-strength-icon">✓</span>${esc(s)}</li>`).join('');
        const concerns = (m.concerns || []).map(c =>
            `<li class="match-concern"><span class="match-concern-icon">!</span>${esc(c)}</li>`).join('');

        const rawRec = m.recommendation || 'POSSIBLE_MATCH';
        const rec = REC_MAP[rawRec] || { cls: 'rec-possible', label: rawRec.replace(/_/g, ' ').toLowerCase() };

        const trackLabel = (m.careerTrack || 'UNKNOWN')
            .replace(/_/g, ' ')
            .toLowerCase();

        card.innerHTML = `
            <div class="match-score-strip">
                <div class="match-score-fill" style="width:${Number(m.matchScore) || 0}%"></div>
            </div>

            <div class="match-card-head">
                <div class="match-card-title-group">
                    <h3 class="job-title">${esc(job.title || 'Untitled Role')}</h3>
                    <div class="job-company">${esc(job.company || 'Unknown Company')}</div>
                </div>
                <div class="match-score-block">
                    <span class="match-score-num">${Number(m.matchScore) || 0}</span>
                    <span class="match-rec-badge ${rec.cls}">${esc(rec.label)}</span>
                </div>
            </div>

            <div class="job-meta-row">
                <div class="job-meta-item" title="Location">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>
                    <span>${esc(job.location || 'Not Specified')}</span>
                </div>
                <div class="job-meta-item" title="Experience Requirement">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
                    <span>${esc(job.experienceRequirement || 'Not Specified')}</span>
                </div>
                <div class="job-meta-item" title="Employment Type">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                    <span>${esc(job.employmentType || 'Full-time')}</span>
                </div>
                <div class="job-meta-item" title="Career Track">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"></path></svg>
                    <span>${esc(trackLabel)}</span>
                </div>
            </div>

            <div class="match-explanation">${esc(m.explanation || '')}</div>

            <div class="job-skills-section">
                ${reqHit ? `<div class="skills-sub-group">
                    <span class="skills-label">✓ Required Matched:</span>
                    <div class="skills-tags-wrap">${reqHit}</div>
                </div>` : ''}
                ${reqMiss ? `<div class="skills-sub-group">
                    <span class="skills-label skills-label-miss">Missing Required:</span>
                    <div class="skills-tags-wrap">${reqMiss}</div>
                </div>` : ''}
                ${prefHit ? `<div class="skills-sub-group">
                    <span class="skills-label">Preferred Matched:</span>
                    <div class="skills-tags-wrap">${prefHit}</div>
                </div>` : ''}
                ${prefMiss ? `<div class="skills-sub-group">
                    <span class="skills-label skills-label-miss">Missing Preferred:</span>
                    <div class="skills-tags-wrap">${prefMiss}</div>
                </div>` : ''}
            </div>

            <div class="match-sc-rows">
                ${strengths ? `<div class="match-strengths-col">
                    <h4 class="match-sc-title match-sc-title-good">Strengths</h4>
                    <ul class="match-sc-list">${strengths}</ul>
                </div>` : ''}
                ${concerns ? `<div class="match-concerns-col">
                    <h4 class="match-sc-title match-sc-title-bad">Concerns</h4>
                    <ul class="match-sc-list">${concerns}</ul>
                </div>` : ''}
            </div>

            <div class="job-card-footer">
                ${matchJobFooterHtml(job)}

                <button type="button" class="btn-career-agent" data-career-agent="true" data-job-id="${esc(job.id || '')}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}"
                    ${candidateId ? '' : 'disabled'}>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"></path></svg>
                    <span>Run Career Agent</span>
                </button>

                ${m.matchScore >= 60 ? `
                <button class="btn-apply application-prepare-btn" data-candidate-id="${candidateId}" data-job-id="${job.id || ''}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}" data-location="${esc(job.location || '')}">
                    <span>Prepare Application</span>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                </button>
                ` : ''}

                ${candidateId ? `
                <button type="button" class="btn-advisor application-advisor-btn" data-candidate-id="${candidateId}" data-job-id="${job.id || ''}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}" data-location="${esc(job.location || '')}">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"></path></svg>
                    <span>Run Application Advisor</span>
                </button>
                ` : ''}
        `;

        if (window.jobDetails) window.jobDetails.register(job);

        return card;
    }

    /**
     * Source-aware card actions (shared rules with the jobs page).
     *
     * "Match Details" is always available: it opens the internal modal with the
     * scored comparison for this listing. The apply action is offered only when the
     * listing carries a real external URL, and it is labelled as what it actually is
     * — a link to the employer's own application page, opened in a new tab. This
     * platform never submits an application on the user's behalf.
     */
    function matchJobFooterHtml(job) {
        const details = `<button type="button" class="btn-view-job btn-view-details" data-open-match-details="true" data-job-id="${esc(job.id || '')}">
            <span>Match Details</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>
        </button>`;

        const isExternal = window.jobDetails && !window.jobDetails.isMock(job) && window.jobDetails.hasValidUrl(job);
        if (!isExternal) {
            return details;
        }
        return details + `<a href="${esc(job.sourceUrl)}" target="_blank" rel="noopener noreferrer"
                class="btn-view-job btn-apply-external"
                title="Opens the employer's own application page in a new tab">
            <span>Open Official Application</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>
        </a>`;
    }

    const REC_MAP = {
        EXCELLENT_MATCH: { cls: 'rec-excellent', label: 'Excellent Match' },
        STRONG_MATCH: { cls: 'rec-strong', label: 'Strong Match' },
        POSSIBLE_MATCH: { cls: 'rec-possible', label: 'Possible Match' },
        WEAK_MATCH: { cls: 'rec-weak', label: 'Weak Match' },
        POOR_MATCH: { cls: 'rec-poor', label: 'Poor Match' }
    };

    // ─── Utilities ────────────────────────────────────────────────────────────
    function esc(text) {
        if (text === 0) return '0';
        if (!text) return '';
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    /**
     * Writes text into #id. A missing element is a no-op instead of a TypeError —
     * the review modals are optional chrome, and one absent node must never abort
     * the rest of the render (the modal has to open either way).
     */
    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    }

    /**
     * Writes <li> rows into the <ul> #id, or a single placeholder row when empty.
     */
    function setList(id, items, render, emptyHtml) {
        const ul = document.getElementById(id);
        if (!ul) return;
        ul.innerHTML = (items && items.length)
            ? items.map(render).join('')
            : emptyHtml;
    }

    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast ${type === 'error' ? 'toast-error' : ''}`;
        const icon = type === 'error'
            ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'
            : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
        toast.innerHTML = `${icon}<span>${esc(message)}</span>`;
        toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(30px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    // ─── Application Advisor button ────────────────────────────────────────────
    // One delegated listener per action. Both branches must guard on their own
    // selector: the prepare-application code used to sit in this listener's
    // fall-through and dereferenced the advisor button, so every click that was not
    // on the advisor button threw "Cannot read properties of null (reading 'dataset')"
    // and the prepare request never fired at all.
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('.application-advisor-btn');
        if (!btn) return;

        const candidateId = Number(btn.dataset.candidateId);
        const jobId = btn.dataset.jobId;
        const jobTitle = btn.dataset.jobTitle;
        const company = btn.dataset.company;
        const location = btn.dataset.location;

        if (!candidateId || !jobId) {
            showToast('Missing required information to run application advisor.', 'error');
            return;
        }

        // Show confirmation
        if (!confirm(`Run Application Advisor for "${jobTitle}" at ${company}?\n\nThis will evaluate your application readiness against the job using your profile and the job requirements.`)) {
            return;
        }

        fetch('/api/v1/jobs/advisor', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                candidateId: candidateId,
                jobId: jobId
            })
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.detail || `HTTP ${response.status}`); });
            }
            return response.json();
        })
        .then(data => {
            showToast('Application Advisor completed.', 'success');
            openAdvisorReview(data, jobTitle, company);
        })
        .catch(err => {
            console.error('Error running application advisor:', err);
            showToast(err.message || 'Failed to run application advisor.', 'error');
        });
    });

    // ─── Prepare Application button ──────────────────────────────────────────
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('.application-prepare-btn');
        if (!btn) return;

        const candidateId = Number(btn.dataset.candidateId);
        const jobId = btn.dataset.jobId;
        const jobTitle = btn.dataset.jobTitle;
        const company = btn.dataset.company;
        const location = btn.dataset.location;

        if (!candidateId || !jobId) {
            showToast('Missing required information to prepare application.', 'error');
            return;
        }

        // Show confirmation
        if (!confirm(`Prepare application for "${jobTitle}" at ${company}?\n\nThis will prepare a review-ready application package. It uses deterministic content for development jobs and falls back safely if AI is unavailable.`)) {
            return;
        }

        fetch('/api/v1/applications/prepare', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                candidateId: candidateId,
                jobId: jobId,
                jobTitle: jobTitle,
                company: company
            })
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.detail || `HTTP ${response.status}`); });
            }
            return response.json();
        })
        .then(data => {
            showToast('Application prepared successfully.', 'success');
            openPreparedReview(data);
        })
        .catch(err => {
            console.error('Error preparing application:', err);
            showToast(err.message || 'Failed to prepare application.', 'error');
        });
    });

    // ─── Prepared application review modal ─────────────────────────────────
    const prepReviewOverlay = document.getElementById('prepReviewOverlay');

    function openPreparedReview(data) {
        if (!prepReviewOverlay) return;
        // Render whatever the API returned. Gating on applicationId used to blank the
        // whole modal whenever the id was absent, even though every content field was
        // present; the id is only used for the Applications page deep link.
        const app = data || {};

        setText('prepReviewSubtitle', app.jobTitle || 'Job');
        setText('prepReviewJobTitle', app.jobTitle || '—');
        setText('prepReviewCompany', app.company || '—');
        setText('prepReviewMatchScore', app.matchScore != null ? `${app.matchScore}/100` : 'not calculated');
        setText('prepReviewSummary', app.tailoredProfessionalSummary || '—');
        setText('prepReviewCoverLetter', app.coverLetter || '—');

        setList('prepReviewMatching', app.matchingSkills,
            s => `<li>${esc(s)}</li>`, '<li>No matching skills listed.</li>');
        setList('prepReviewMissing', app.missingSkills,
            s => `<li>${esc(s)}</li>`, '<li>No missing skills to address.</li>');
        setList('prepReviewHighlights', app.resumeHighlights,
            s => `<li>${esc(s)}</li>`, '<li>No resume highlights available.</li>');

        prepReviewOverlay.hidden = false;
        document.body.classList.add('modal-open');
    }

    function closePreparedReview() {
        if (prepReviewOverlay) {
            prepReviewOverlay.hidden = true;
            document.body.classList.remove('modal-open');
        }
    }

    function copyElementText(sourceId, btn) {
        const el = document.getElementById(sourceId);
        if (!el) return;
        const text = (el.innerText || '').trim();
        if (!text) return;
        const done = () => {
            if (btn) {
                btn.textContent = 'Copied ✓';
                setTimeout(() => { btn.textContent = 'Copy'; }, 1600);
            }
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(done).catch(() => fallbackCopy(text, done));
        } else {
            fallbackCopy(text, done);
        }
    }

    function fallbackCopy(text, done) {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); } catch (e) { /* ignore */ }
        document.body.removeChild(ta);
        done();
    }

    document.addEventListener('click', (e) => {
        // Advisor Review Modal close/done
        if (e.target.closest('#advisorReviewClose') || e.target.closest('#advisorReviewDone')) {
            closeAdvisorReview();
            return;
        }

        // Prepared Application review modal
        if (e.target.closest('#prepReviewClose') || e.target.closest('#prepReviewDone')) {
            closePreparedReview();
            return;
        }
        if (e.target === prepReviewOverlay) {
            closePreparedReview();
            return;
        }
        const copyBtn = e.target.closest('.copy-btn');
        if (copyBtn) {
            copyElementText(copyBtn.dataset.copyTarget, copyBtn);
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (prepReviewOverlay && !prepReviewOverlay.hidden) {
                closePreparedReview();
            }
            if (advisorReviewOverlay && !advisorReviewOverlay.hidden) {
                closeAdvisorReview();
            }
        }
    });

    // ─── Application Advisor Review Modal ─────────────────────────────────
    const advisorReviewOverlay = document.getElementById('advisorReviewOverlay');

    function openAdvisorReview(data, fallbackJobTitle, fallbackCompany) {
        if (!advisorReviewOverlay) return;
        const adv = data || {};

        setText('advisorReviewSubtitle', `Score: ${numOrDash(adv.applicationReadinessScore)}/100`);
        // The API echoes jobTitle/company; the card the user clicked carries the same
        // values, so a partial response still renders the right role.
        setText('advisorReviewJobTitle', adv.jobTitle || fallbackJobTitle || '—');
        setText('advisorReviewCompany', adv.company || fallbackCompany || '—');

        setText('advisorReviewRecommendation', recommendationLabel(adv.recommendation));
        setText('advisorReviewReadiness', `${numOrDash(adv.applicationReadinessScore)}/100`);
        setText('advisorReviewJobMatch', `${numOrDash(adv.jobMatchScore)}/100`);

        renderAdvisorWhy(adv);
        renderAdvisorBreakdown(adv.scoreBreakdown);

        setList('advisorReviewStrengths', adv.strengths,
            s => `<li>${esc(s)}</li>`, '<li>No strengths listed.</li>');
        setList('advisorReviewConcerns', adv.concerns,
            c => `<li>${esc(c)}</li>`, '<li>No concerns listed.</li>');
        setList('advisorReviewActions', adv.recommendedActions,
            a => `<li>${esc(a)}</li>`, '<li>No recommended actions.</li>');
        setList('advisorReviewDetails', adv.recommendedActionDetails,
            d => `<li><strong>${esc(d.focus)}</strong> (${esc(d.type)}): ${esc(d.description)}</li>`,
            '<li>No action details available.</li>');

        advisorReviewOverlay.hidden = false;
        document.body.classList.add('modal-open');
    }

    /**
     * "Why this recommendation": the backend supplies a deterministic sentence built
     * from the same numbers shown above. When an older response has none, the section
     * is hidden rather than filled with a guess.
     */
    function renderAdvisorWhy(adv) {
        const section = document.getElementById('advisorReviewWhySection');
        if (!section) return;
        const why = adv && adv.recommendationExplanation ? String(adv.recommendationExplanation).trim() : '';
        section.hidden = !why;
        setText('advisorReviewWhy', why);
    }

    /**
     * Per-factor score breakdown. Each row is a deterministic factor score the backend
     * already computes; the section is hidden entirely when the response predates it,
     * so no factor is ever shown as a fabricated number.
     */
    function renderAdvisorBreakdown(b) {
        const section = document.getElementById('advisorReviewBreakdownSection');
        const rowsEl = document.getElementById('advisorReviewBreakdown');
        const noteEl = document.getElementById('advisorReviewBreakdownNote');
        if (!section || !rowsEl) return;

        if (!b) {
            section.hidden = true;
            return;
        }

        const rows = [
            ['Skill fit', b.skillFitScore],
            ['Role fit', b.roleFitScore],
            ['Experience fit', b.experienceFitScore],
            ['Education fit', b.educationFitScore],
            ['Location fit', b.locationFitScore],
            ['Career-track fit', b.trackFitScore],
            ['ATS readiness', b.atsReadinessScore]
        ];

        rowsEl.innerHTML = rows.map(([label, value]) => {
            const pct = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
            const tone = pct >= 70 ? 'is-good' : (pct >= 40 ? 'is-mid' : 'is-low');
            return `
                <div class="advisor-breakdown-row">
                    <span class="advisor-breakdown-label">${esc(label)}</span>
                    <span class="advisor-breakdown-bar"><span class="advisor-breakdown-fill ${tone}" style="width:${pct}%"></span></span>
                    <span class="advisor-breakdown-value">${esc(pct)}/100</span>
                </div>`;
        }).join('');

        const notes = [];
        notes.push(`Required skills: ${numOrDash(b.matchedRequiredCount)} matched, ${numOrDash(b.missingRequiredCount)} missing.`);
        notes.push(`Preferred skills: ${numOrDash(b.matchedPreferredCount)} matched, ${numOrDash(b.missingPreferredCount)} missing.`);
        if (b.experienceKnowable === false) {
            notes.push('Experience: years could not be compared from structured data, so no shortfall is claimed.');
        } else if (b.requiredYears != null && b.candidateYears != null) {
            notes.push(`Experience: ${esc(b.candidateYears)} year(s) on the resume vs ${esc(b.requiredYears)} year(s) required.`);
        }
        if (noteEl) noteEl.innerHTML = notes.map(n => `<span class="advisor-breakdown-note-item">${n}</span>`).join('');

        section.hidden = false;
    }

    /** Renders a score as-is, including a legitimate 0 (only null/undefined becomes a dash). */
    function numOrDash(value) {
        return Number.isFinite(value) ? value : '—';
    }

    /** Sentence-case label for a recommendation band, so the UI never shows a bare enum name. */
    function recommendationLabel(value) {
        const RECOMMENDATION_LABELS = {
            STRONGLY_RECOMMENDED: 'Strongly recommended',
            RECOMMENDED: 'Recommended',
            APPLY_WITH_IMPROVEMENTS: 'Apply after improvements',
            LOW_PRIORITY: 'Low priority',
            NOT_RECOMMENDED: 'Not recommended'
        };
        return RECOMMENDATION_LABELS[value] || '—';
    }

    function closeAdvisorReview() {
        if (advisorReviewOverlay) {
            advisorReviewOverlay.hidden = true;
            document.body.classList.remove('modal-open');
        }
    }

    // ─── Match Details modal ────────────────────────────────────────────────
    // Matches are kept keyed by job id so the card button only needs a data-job-id
    // attribute; the modal then renders the exact scored match the user clicked.
    const matchRegistry = new Map();
    let matchDetailsOverlay = null;

    function ensureMatchDetailsModal() {
        if (matchDetailsOverlay) return matchDetailsOverlay;

        matchDetailsOverlay = document.createElement('div');
        matchDetailsOverlay.className = 'modal-overlay';
        matchDetailsOverlay.hidden = true;
        matchDetailsOverlay.innerHTML = `
            <div class="modal-card" role="dialog" aria-modal="true" aria-labelledby="matchDetailsTitle">
                <div class="modal-header">
                    <div class="modal-header-text">
                        <h3 class="modal-title" id="matchDetailsTitle">Match Details</h3>
                        <div class="modal-company" id="matchDetailsCompany"></div>
                    </div>
                    <button type="button" class="modal-close" id="matchDetailsClose" aria-label="Close match details">&times;</button>
                </div>
                <div class="modal-body" id="matchDetailsBody"></div>
                <div class="modal-footer" id="matchDetailsFooter"></div>
            </div>`;

        matchDetailsOverlay.addEventListener('click', (e) => {
            if (e.target === matchDetailsOverlay) closeMatchDetails();
        });
        matchDetailsOverlay.querySelector('#matchDetailsClose').addEventListener('click', closeMatchDetails);
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && matchDetailsOverlay && !matchDetailsOverlay.hidden) closeMatchDetails();
        });

        document.body.appendChild(matchDetailsOverlay);
        return matchDetailsOverlay;
    }

    function openMatchDetails(jobId) {
        const m = matchRegistry.get(String(jobId));
        if (!m) {
            showToast('Match details are no longer available for this listing. Run matching again.', 'error');
            return;
        }
        const job = m.job || {};
        const modal = ensureMatchDetailsModal();
        modal.querySelector('#matchDetailsTitle').textContent = job.title || 'Untitled Role';
        modal.querySelector('#matchDetailsCompany').textContent = job.company || 'Unknown Company';
        modal.querySelector('#matchDetailsBody').innerHTML = buildMatchDetailsHtml(m);
        modal.querySelector('#matchDetailsFooter').innerHTML = buildMatchDetailsFooter(m);
        modal.hidden = false;
        document.body.classList.add('modal-open');
        modal.querySelector('#matchDetailsClose').focus();
    }

    function closeMatchDetails() {
        if (!matchDetailsOverlay || matchDetailsOverlay.hidden) return;
        matchDetailsOverlay.hidden = true;
        document.body.classList.remove('modal-open');
    }

    function buildMatchDetailsHtml(m) {
        const job = m.job || {};
        const rec = REC_MAP[m.recommendation] || { cls: 'rec-possible', label: humanizeEnum(m.recommendation) };
        const score = Number(m.matchScore) || 0;

        const chips = (list, cls, hit) => (list || [])
            .map(s => `<span class="skill-tag ${cls} ${hit ? 'match-hit' : 'match-miss'}">${esc(s)}</span>`)
            .join('');
        const rows = (title, html, emptyText) => html
            ? `<div class="modal-job-section"><h4 class="modal-section-title">${esc(title)}</h4>${html}</div>`
            : `<div class="modal-job-section"><h4 class="modal-section-title">${esc(title)}</h4><p class="modal-empty-line">${esc(emptyText)}</p></div>`;

        const strengths = (m.strengths || [])
            .map(s => `<li class="match-strength"><span class="match-strength-icon">✓</span>${esc(s)}</li>`).join('');
        const concerns = (m.concerns || [])
            .map(c => `<li class="match-concern"><span class="match-concern-icon">!</span>${esc(c)}</li>`).join('');

        const required = chips(m.matchedSkills, 'required-skill', true) + chips(m.missingSkills, 'required-skill', false);
        const preferred = chips(m.matchedPreferredSkills, 'preferred-skill', true) + chips(m.missingPreferredSkills, 'preferred-skill', false);

        return `
            <div class="match-details-score">
                <span class="match-details-score-num">${esc(score)}</span>
                <span class="match-details-score-max">/100 match score</span>
                <span class="match-rec-badge ${rec.cls}">${esc(rec.label)}</span>
            </div>

            <div class="job-meta-row">
                <div class="job-meta-item" title="Location"><span>${esc(job.location || 'Location not listed')}</span></div>
                <div class="job-meta-item" title="Employment Type"><span>${esc(formatEmploymentType(job.employmentType))}</span></div>
                <div class="job-meta-item" title="Experience Requirement"><span>${esc(job.experienceRequirement || 'Not specified')}</span></div>
                <div class="job-meta-item" title="Career Track"><span>${esc(humanizeEnum(m.careerTrack))}</span></div>
            </div>

            ${rows('Why this job matched', m.explanation ? `<p class="modal-description">${esc(m.explanation)}</p>` : '',
                'No explanation was produced for this match.')}

            ${rows('Required skills', required ? `<div class="skills-tags-wrap">${required}</div>` : '',
                'This listing states no required skills.')}

            ${rows('Preferred skills', preferred ? `<div class="skills-tags-wrap">${preferred}</div>` : '',
                'This listing states no preferred skills.')}

            ${rows('Your strengths', strengths ? `<ul class="match-sc-list">${strengths}</ul>` : '',
                'No strengths were reported for this match.')}

            ${rows('Gaps to close', concerns ? `<ul class="match-sc-list">${concerns}</ul>` : '',
                'No gaps were reported for this match.')}

            <div class="job-meta-row modal-source-row">
                <div class="job-meta-item" title="Listing Source"><span>Source: ${esc(job.source || 'Unknown')}</span></div>
            </div>`;
    }

    /**
     * Modal actions reuse the same delegated handlers as the cards (career agent,
     * advisor, prepare), so there is exactly one implementation of each action.
     * The apply action is only offered when the listing carries a real external URL,
     * and it is labelled as a link to the employer's own site.
     */
    function buildMatchDetailsFooter(m) {
        const job = m.job || {};
        const jobId = esc(job.id || '');
        const score = Number(m.matchScore) || 0;
        const parts = [];

        if (candidateId) {
            parts.push(`<button type="button" class="btn-career-agent" data-career-agent="true" data-job-id="${jobId}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}">
                <span>Career Analysis</span></button>`);
            parts.push(`<button type="button" class="btn-advisor application-advisor-btn" data-candidate-id="${esc(candidateId)}" data-job-id="${jobId}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}" data-location="${esc(job.location || '')}">
                <span>Application Advisor</span></button>`);
            if (score >= 60) {
                parts.push(`<button type="button" class="btn-apply application-prepare-btn" data-candidate-id="${esc(candidateId)}" data-job-id="${jobId}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}" data-location="${esc(job.location || '')}">
                    <span>Prepare Application</span></button>`);
            }
        }

        const isExternal = window.jobDetails && !window.jobDetails.isMock(job) && window.jobDetails.hasValidUrl(job);
        if (isExternal) {
            parts.push(`<a href="${esc(job.sourceUrl)}" target="_blank" rel="noopener noreferrer" class="btn-view-job btn-apply-external"
                title="Opens the employer's own application page in a new tab">
                <span>Open Official Application</span></a>`);
        }

        const hint = `<span class="modal-hint">${candidateId
            ? 'This platform never submits an application for you — the official application opens on the employer\'s own site.'
            : `Upload a <a href="resume.html">resume</a> to run career analysis and prepare an application.`}</span>`;

        return parts.join('') + hint;
    }

    function formatEmploymentType(type) {
        if (!type) return 'Not specified';
        switch (String(type).toUpperCase()) {
            case 'FULL_TIME': return 'Full-time';
            case 'PART_TIME': return 'Part-time';
            case 'INTERNSHIP': return 'Internship';
            case 'CONTRACT': return 'Contract';
            default: return String(type);
        }
    }

    /** Turns an enum-ish value into readable words ("POSSIBLE_MATCH" → "Possible match"). */
    function humanizeEnum(value) {
        if (!value) return 'Unknown';
        const words = String(value).toLowerCase().split('_').filter(Boolean);
        if (!words.length) return 'Unknown';
        return words.map((w, i) => i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w).join(' ');
    }

    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-open-match-details]');
        if (!btn) return;
        openMatchDetails(btn.getAttribute('data-job-id'));
    });

    // ─── Boot ─────────────────────────────────────────────────────────────────
    init();
})();