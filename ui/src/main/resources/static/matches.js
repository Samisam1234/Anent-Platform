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
            showToast(window.apiError.describe(err, 'Failed to match jobs. Please check backend connection.'), 'error');
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
                    <span>Career Analysis</span>
                </button>

                ${candidateId ? `
                <button type="button" class="btn-tailor resume-tailor-btn" data-candidate-id="${esc(candidateId)}" data-job-id="${esc(job.id || '')}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>
                    <span>Tailor Resume</span>
                </button>` : ''}

                ${m.matchScore >= 60 ? `
                <button class="btn-apply application-prepare-btn" data-candidate-id="${candidateId}" data-job-id="${job.id || ''}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}" data-location="${esc(job.location || '')}">
                    <span>Prepare Application</span>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                </button>
                ` : ''}

                ${candidateId ? `
                <button type="button" class="btn-advisor application-advisor-btn" data-candidate-id="${esc(candidateId)}" data-job-id="${esc(job.id || '')}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}" data-location="${esc(job.location || '')}">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 11l3 3L22 4"></path><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path></svg>
                    <span>Application Readiness</span>
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

        const parts = [details];

        // Destination resolution lives in jobLink.js: the employer's own application page
        // when the source genuinely supplied one, otherwise the originating listing,
        // otherwise a disabled control that explains why. sourceUrl is never presented
        // as "the application".
        if (window.jobLink) {
            parts.push(window.jobLink.actionHtml(job, { withIcon: true }));
        }

        return parts.join('');
    }

    function hasValidUrl(url) {
        return typeof url === 'string' && /^https?:\/\//i.test(url.trim());
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
    document.addEventListener('click', async (e) => {
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

        // Styled confirmation via the shared modal shell — never a native browser dialog.
        const proceed = await window.confirmDialog.ask({
            title: 'Check application readiness',
            subtitle: [jobTitle, company].filter(Boolean).join(' · '),
            message: 'Compare your parsed resume against this role\u2019s requirements?',
            detail: 'You will get a readiness score, matched and missing requirements, and recommended next steps.',
            warning: 'Nothing is submitted to the employer.',
            confirmLabel: 'Run readiness check',
            cancelLabel: 'Cancel'
        });
        if (!proceed) {
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
            showToast('Application readiness calculated.', 'success');
            openAdvisorReview(data, {
                candidateId: candidateId, jobId: jobId,
                jobTitle: jobTitle, company: company, location: location
            });
        })
        .catch(err => {
            console.error('Error running application advisor:', err);
            showToast(window.apiError.describe(err, 'Failed to run application advisor.'), 'error');
        });
    });

    // ─── Prepare Application button ──────────────────────────────────────────
    document.addEventListener('click', async (e) => {
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

        // Styled confirmation via the shared modal shell — never a native browser dialog.
        const proceed = await window.confirmDialog.ask({
            title: 'Prepare application',
            subtitle: [jobTitle, company].filter(Boolean).join(' · '),
            message: 'Build a review-ready application package for this role?',
            detail: 'This creates a resume summary, cover letter and suggested answers, and saves the package to your Applications page.',
            warning: 'Nothing is submitted to the employer.',
            confirmLabel: 'Prepare application',
            cancelLabel: 'Cancel'
        });
        if (!proceed) {
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
            showToast(window.apiError.describe(err, 'Failed to prepare application.'), 'error');
        });
    });

    // ─── Prepared application review ────────────────────────────────────────
    /**
     * Shows the prepared package in the single global dialog. Renders whatever the API
     * returned; the application id is only used for the Applications page deep link, so
     * a missing id must not blank the content.
     */
    function openPreparedReview(data) {
        const app = data || {};
        const body = `
            <div class="prep-summary">
                <span class="summary-chip">Company: ${esc(app.company || '—')}</span>
                <span class="summary-chip">Skill coverage: ${app.matchScore != null ? esc(app.matchScore) + '/100' : 'not calculated'}</span>
                ${app.recommendation ? `<span class="summary-chip">Advisor: ${esc(humanizeEnum(app.recommendation))}</span>` : ''}
                <span class="summary-chip">Prepared for your review — not submitted</span>
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Skills you match</h4>
                ${listOrEmpty(app.matchingSkills, 'No matching skills listed.')}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Skills to address</h4>
                ${listOrEmpty(app.missingSkills, 'No missing skills to address.')}
            </div>

            <div class="modal-job-section">
                <div class="prep-section-head">
                    <h4 class="modal-section-title">Tailored professional summary</h4>
                    <button type="button" class="copy-btn" data-copy-inline="prepReviewSummary">Copy</button>
                </div>
                <p class="modal-description" id="prepReviewSummary">${esc(app.tailoredProfessionalSummary || '—')}</p>
            </div>

            <div class="modal-job-section">
                <div class="prep-section-head">
                    <h4 class="modal-section-title">Cover letter</h4>
                    <button type="button" class="copy-btn" data-copy-inline="prepReviewCoverLetter">Copy</button>
                </div>
                <p class="modal-description prep-pre" id="prepReviewCoverLetter">${esc(app.coverLetter || '—')}</p>
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Suggested application answers</h4>
                ${splitListOrEmpty(app.suggestedAnswers, 'No suggested answers available.')}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Your strengths</h4>
                ${listOrEmpty(app.candidateStrengths, 'No candidate strengths listed.')}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Resume highlights</h4>
                ${listOrEmpty(app.resumeHighlights, 'No resume highlights available.')}
            </div>`;

        const footer = `
            <span class="modal-hint">Nothing has been submitted. Approving the package in Applications is required before any email can go out.</span>
            ${app.applicationId ? `<a class="btn-secondary" href="applications.html?application=${encodeURIComponent(app.applicationId)}&edit=1">Edit in Applications</a>` : ''}`;

        window.modalShell.open({
            kicker: 'Prepared Application',
            title: app.jobTitle || 'Job',
            subtitle: app.company || '',
            body: body,
            footer: footer
        });
    }

    /** Renders a list as bullets (optionally with tone icons), or a muted line when it is empty. */
    function listOrEmpty(values, emptyText, listClass) {
        const items = (values || []).filter(v => v !== null && v !== undefined && String(v).trim() !== '');
        if (!items.length) return `<p class="modal-empty-line">${esc(emptyText)}</p>`;
        const cls = listClass ? `match-sc-list ${listClass}` : 'match-sc-list';
        return `<ul class="${cls}">${items.map(v => `<li>${esc(v)}</li>`).join('')}</ul>`;
    }

    /** Splits the delimited suggested-answers string (same convention as applications.js splitList). */
    function splitListOrEmpty(text, emptyText) {
        const items = String(text || '').split(/[,\u2014-]\s*|\s+\|\|\s+/).map(s => s.trim()).filter(Boolean);
        return listOrEmpty(items, emptyText);
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
        const copyBtn = e.target.closest('.copy-btn');
        if (copyBtn) {
            copyElementText(copyBtn.dataset.copyTarget || copyBtn.dataset.copyInline, copyBtn);
        }
    });

    // ─── ATS resume tailoring (§ workflow: Tailor Resume) ───────────────────
    /**
     * Calls the existing deterministic tailoring service. It only reorders and
     * emphasises content the parsed profile already contains; missing requirements come
     * back in a separate list and are shown as gaps, never as qualifications.
     *
     * The backend now returns { analysis, draft }. This handler unwraps that shape
     * (with a compat guard for stale caches) and renders both the analysis and the
     * tailored-resume draft in the single global modal. Download buttons for PDF and
     * DOCX are added to the footer once the JSON payload resolves.
     */
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('.resume-tailor-btn');
        if (!btn) return;

        const candidateId = Number(btn.dataset.candidateId);
        const jobId = btn.dataset.jobId;
        const jobTitle = btn.dataset.jobTitle || '';
        const company = btn.dataset.company || '';
        if (!candidateId || !jobId) {
            showToast('Missing required information to tailor your resume.', 'error');
            return;
        }

        const requestBody = { candidateId: candidateId, jobId: jobId };

        window.modalShell.open({
            kicker: 'ATS Resume Tailoring',
            title: jobTitle || 'Selected role',
            subtitle: company || '',
            body: `<div class="agent-run-loading"><span class="processing-spinner"></span><span>Analysing your resume against this role…</span></div>`,
            footer: ''
        });

        fetch('/api/v1/resume/tailor', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.detail || `HTTP ${response.status}`); })
                    .catch(parseErr => { throw parseErr; });
            }
            return response.json();
        })
        .then(data => {
            // Response shape: { analysis: ResumeTailoringAnalysis, draft: TailoredResumeDraft }
            // Compat: a stale cached page might still receive the old top-level analysis.
            const analysis = data?.analysis ?? data;
            const draft = data?.draft ?? null;

            if (!analysis) {
                throw new Error('Tailoring response did not contain analysis data.');
            }

            // Render the combined preview (analysis + draft)
            window.modalShell.setBody(buildTailoringPreviewHtml(analysis, draft));

            // Add download actions to the footer
            const footerHtml = buildTailoringFooterHtml(requestBody);
            window.modalShell.setFooter(footerHtml);
        })
        .catch(err => {
            console.error('Resume tailoring error:', err);
            window.modalShell.setBody(
                `<div class="agent-run-summary agent-summary-bad">
                    <span class="agent-summary-status">Tailoring unavailable</span>
                    <span class="agent-summary-message">${esc(window.apiError.describe(err, 'Could not tailor your resume for this role.'))}</span>
                </div>`);
            window.modalShell.setFooter('');
        });
    });

    /**
     * Human-readable labels for the backend's RecommendationType values.
     *
     * The tailoring API returns structured TailoringRecommendation records
     * ({type, focus, reason, evidenceSources}). These are rendered as labelled cards —
     * the record is never serialized into the DOM, so no braces, quotes or JSON
     * property names can reach the user.
     */
    const RECOMMENDATION_TYPES = {
        HIGHLIGHT_SKILL: { label: 'Highlight this skill', tone: 'is-good' },
        HIGHLIGHT_PROJECT: { label: 'Highlight this project', tone: 'is-good' },
        HIGHLIGHT_EXPERIENCE: { label: 'Highlight this experience', tone: 'is-good' },
        HIGHLIGHT_INTERNSHIP: { label: 'Highlight this internship', tone: 'is-good' },
        SECTION_PRIORITY: { label: 'Section order', tone: 'is-caution' },
        MISSING_REQUIREMENT: { label: 'Missing requirement', tone: 'is-gap' }
    };

    /**
     * Renders one TailoringRecommendation as a styled card: a human label, the subject,
     * the plain-language reason and, where the backend supplied them, the resume
     * sections that evidence it.
     *
     * An unrecognized payload degrades to a generic label — it is never dumped as JSON.
     */
    function recommendationItemHtml(rec) {
        if (rec === null || rec === undefined) return '';

        // A plain string is already human-readable, so render it as-is.
        if (typeof rec !== 'object') {
            const text = String(rec).trim();
            return text ? `<li class="tailoring-item">${esc(text)}</li>` : '';
        }

        const meta = RECOMMENDATION_TYPES[rec.type]
            || { label: rec.type ? humanizeEnum(rec.type) : 'Suggestion', tone: '' };
        const focus = rec.focus ? String(rec.focus).trim() : '';
        const reason = rec.reason ? String(rec.reason).trim() : '';
        const evidence = (rec.evidenceSources || [])
            .map(sec => humanizeEnum(sec))
            .filter(Boolean);

        // Nothing recognisable at all: show the label only, never the raw object.
        if (!focus && !reason && !evidence.length) {
            return `<li class="tailoring-item ${meta.tone}"><span class="tailoring-item-label">${esc(meta.label)}</span></li>`;
        }

        return `
            <li class="tailoring-item ${meta.tone}">
                <span class="tailoring-item-label">${esc(meta.label)}</span>
                ${focus ? `<span class="tailoring-item-focus">${esc(focus)}</span>` : ''}
                ${reason ? `<span class="tailoring-item-reason">${esc(reason)}</span>` : ''}
                ${evidence.length ? `<span class="tailoring-item-evidence">Evidence in your resume: ${esc(evidence.join(', '))}</span>` : ''}
            </li>`;
    }

    /** Renders one HighlightedSkill (canonicalSkill + the sections it was seen in). */
    function highlightedSkillHtml(skill) {
        if (!skill || typeof skill !== 'object') return '';
        const name = skill.canonicalSkill ? String(skill.canonicalSkill).trim() : '';
        if (!name) return '';
        const evidence = (skill.evidenceSources || [])
            .map(sec => humanizeEnum(sec))
            .filter(Boolean);
        return `<li class="tailoring-item is-good">
            <span class="tailoring-item-label">Skill to lead with</span>
            <span class="tailoring-item-focus">${esc(name)}</span>
            ${evidence.length ? `<span class="tailoring-item-evidence">Evidence in your resume: ${esc(evidence.join(', '))}</span>` : ''}
        </li>`;
    }

    function buildTailoringHtml(t) {
        const a = t || {};
        const ats = a.atsReadiness || {};
        const chips = (list, cls) => (list || [])
            .map(s => `<span class="skill-tag ${cls}">${esc(s)}</span>`).join('');
        const matched = chips(a.matchedRequiredSkills, 'required-skill match-hit')
            + chips(a.matchedPreferredSkills, 'preferred-skill match-hit');
        const missing = chips(a.missingRequiredSkills, 'required-skill match-miss')
            + chips(a.missingPreferredSkills, 'preferred-skill match-miss');

        const recommendations = (a.tailoringRecommendations || [])
            .map(recommendationItemHtml).filter(Boolean).join('');
        const missingReqs = (a.missingRequirements || [])
            .map(recommendationItemHtml).filter(Boolean).join('');
        const order = (a.recommendedSectionOrder || [])
            .map(sec => sec ? `<li>${esc(humanizeEnum(sec))}</li>` : '').join('');
        const highlighted = (a.highlightedSkills || [])
            .map(h => highlightedSkillHtml(h)).filter(Boolean).join('');

        return `
            <div class="prep-summary">
                <span class="summary-chip">ATS readiness: ${Number.isFinite(ats.score) ? esc(ats.score) + '/100' : 'not calculated'}</span>
                ${ats.label ? `<span class="summary-chip">${esc(humanizeEnum(ats.label))}</span>` : ''}
                <span class="summary-chip">Nothing is invented — only your own content is reordered</span>
            </div>

            ${ats.explanation ? `<div class="modal-job-section"><h4 class="modal-section-title">ATS alignment</h4><p class="modal-description">${esc(ats.explanation)}</p></div>` : ''}

            <div class="modal-job-section">
                <h4 class="modal-section-title">Skills to lead with</h4>
                ${matched ? `<div class="skills-tags-wrap">${matched}</div>` : `<p class="modal-empty-line">No overlapping skills were found for this role.</p>`}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Missing requirements (not added to your resume)</h4>
                ${missing ? `<div class="skills-tags-wrap">${missing}</div>` : `<p class="modal-empty-line">Nothing required by this role is missing.</p>`}
            </div>

            ${highlighted ? `<div class="modal-job-section"><h4 class="modal-section-title">Genuine evidence to emphasise</h4><ul class="match-sc-list">${highlighted}</ul></div>` : ''}

            ${recommendations ? `<div class="modal-job-section"><h4 class="modal-section-title">Suggested changes</h4><ul class="match-sc-list">${recommendations}</ul></div>` : ''}

            ${missingReqs ? `<div class="modal-job-section"><h4 class="modal-section-title">Gaps to address separately</h4><ul class="match-sc-list">${missingReqs}</ul></div>` : ''}

            ${order ? `<div class="modal-job-section"><h4 class="modal-section-title">Recommended section order</h4><ol class="match-sc-list">${order}</ol></div>` : ''}

            <p class="agent-note">Tailoring reorders and rewords the content already in your resume. It never adds skills, employers, projects, certifications or education you do not have.</p>`;
    }

    /**
     * Builds the combined tailoring preview HTML from the analysis and the draft.
     * The draft contains the tailored resume content (professional summary, ordered skills,
     * highlighted projects/experience/internships, section order, warnings).
     *
     * @param {object} analysis  ResumeTailoringAnalysis (matched/missing skills, highlighted skills, recommendations, atsReadiness, etc.)
     * @param {object|null} draft  TailoredResumeDraft or null if backend returned old shape
     */
    function buildTailoringPreviewHtml(analysis, draft) {
        const a = analysis || {};
        const d = draft || {};

        // Reuse the existing analysis rendering for the top part
        const ats = a.atsReadiness || {};
        const chips = (list, cls) => (list || [])
            .map(s => `<span class="skill-tag ${cls}">${esc(s)}</span>`).join('');
        const matched = chips(a.matchedRequiredSkills, 'required-skill match-hit')
            + chips(a.matchedPreferredSkills, 'preferred-skill match-hit');
        const missing = chips(a.missingRequiredSkills, 'required-skill match-miss')
            + chips(a.missingPreferredSkills, 'preferred-skill match-miss');

        const recommendations = (a.tailoringRecommendations || [])
            .map(recommendationItemHtml).filter(Boolean).join('');
        const missingReqs = (a.missingRequirements || [])
            .map(recommendationItemHtml).filter(Boolean).join('');
        const order = (a.recommendedSectionOrder || [])
            .map(sec => sec ? `<li>${esc(humanizeEnum(sec))}</li>` : '').join('');
        const highlighted = (a.highlightedSkills || [])
            .map(h => highlightedSkillHtml(h)).filter(Boolean).join('');

        // --- Draft sections (new) ---
        const professionalSummary = d.professionalSummary ? String(d.professionalSummary).trim() : '';
        const orderedSkills = (d.orderedSkills || []).filter(s => s && String(s).trim());
        const highlightedProjects = (d.highlightedProjects || []).filter(p => p && String(p).trim());
        const highlightedExperience = (d.highlightedExperience || []).filter(e => e && String(e).trim());
        const highlightedInternships = (d.highlightedInternships || []).filter(i => i && String(i).trim());
        const sectionOrder = (d.sectionOrder || []).filter(s => s);
        const warnings = (d.warnings || []).filter(w => w && String(w).trim());

        // Build draft section HTML in the order specified by the draft
        const sectionRenderers = {
            SUMMARY: () => professionalSummary
                ? `<div class="modal-job-section"><h4 class="modal-section-title">Professional Summary</h4><p class="modal-description">${esc(professionalSummary)}</p></div>`
                : '',
            SKILLS: () => orderedSkills.length
                ? `<div class="modal-job-section"><h4 class="modal-section-title">Skills</h4><div class="skills-tags-wrap">${orderedSkills.map(s => `<span class="skill-tag skill-draft">${esc(s)}</span>`).join('')}</div>`
                : '',
            PROJECTS: () => highlightedProjects.length
                ? `<div class="modal-job-section"><h4 class="modal-section-title">Projects</h4><ul class="match-sc-list">${highlightedProjects.map(p => `<li>${esc(p)}</li>`).join('')}</ul></div>`
                : '',
            EXPERIENCE: () => highlightedExperience.length
                ? `<div class="modal-job-section"><h4 class="modal-section-title">Experience</h4><ul class="match-sc-list">${highlightedExperience.map(e => `<li>${esc(e)}</li>`).join('')}</ul></div>`
                : '',
            INTERNSHIPS: () => highlightedInternships.length
                ? `<div class="modal-job-section"><h4 class="modal-section-title">Internships</h4><ul class="match-sc-list">${highlightedInternships.map(i => `<li>${esc(i)}</li>`).join('')}</ul></div>`
                : '',
            CERTIFICATIONS: () => '', // Not in draft; comes from profile if needed
            EDUCATION: () => ''       // Not in draft; comes from profile if needed
        };

        const draftSectionsHtml = sectionOrder
            .map(sec => sectionRenderers[sec]?.() ?? '')
            .filter(Boolean)
            .join('');

        const warningsHtml = warnings.length
            ? `<div class="modal-job-section"><h4 class="modal-section-title">Notes</h4><ul class="match-sc-list is-caution">${warnings.map(w => `<li>${esc(w)}</li>`).join('')}</ul></div>`
            : '';

        return `
            <div class="prep-summary">
                <span class="summary-chip">ATS readiness: ${Number.isFinite(ats.score) ? esc(ats.score) + '/100' : 'not calculated'}</span>
                ${ats.label ? `<span class="summary-chip">${esc(humanizeEnum(ats.label))}</span>` : ''}
                <span class="summary-chip">Nothing is invented — only your own content is reordered</span>
            </div>

            ${ats.explanation ? `<div class="modal-job-section"><h4 class="modal-section-title">ATS alignment</h4><p class="modal-description">${esc(ats.explanation)}</p></div>` : ''}

            <div class="modal-job-section">
                <h4 class="modal-section-title">Skills to lead with</h4>
                ${matched ? `<div class="skills-tags-wrap">${matched}</div>` : `<p class="modal-empty-line">No overlapping skills were found for this role.</p>`}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Missing requirements (not added to your resume)</h4>
                ${missing ? `<div class="skills-tags-wrap">${missing}</div>` : `<p class="modal-empty-line">Nothing required by this role is missing.</p>`}
            </div>

            ${highlighted ? `<div class="modal-job-section"><h4 class="modal-section-title">Genuine evidence to emphasise</h4><ul class="match-sc-list">${highlighted}</ul></div>` : ''}

            ${recommendations ? `<div class="modal-job-section"><h4 class="modal-section-title">Suggested changes</h4><ul class="match-sc-list">${recommendations}</ul></div>` : ''}

            ${missingReqs ? `<div class="modal-job-section"><h4 class="modal-section-title">Gaps to address separately</h4><ul class="match-sc-list">${missingReqs}</ul></div>` : ''}

            ${order ? `<div class="modal-job-section"><h4 class="modal-section-title">Recommended section order</h4><ol class="match-sc-list">${order}</ol></div>` : ''}

            ${draftSectionsHtml}

            ${warningsHtml}

            <p class="agent-note">Tailoring reorders and rewords the content already in your resume. It never adds skills, employers, projects, certifications or education you do not have.</p>`;
    }

    /**
     * Builds the footer HTML with PDF and DOCX download buttons.
     * Each button POSTs to the respective binary endpoint with the same request body.
     *
     * @param {object} requestBody  { candidateId, jobId }
     */
    function buildTailoringFooterHtml(requestBody) {
        return `
            <span class="modal-hint">Download the tailored resume as a real document (PDF or DOCX). Nothing is submitted to the employer.</span>
            <button type="button" class="btn-secondary tailor-download-btn" data-format="pdf" data-candidate-id="${esc(requestBody.candidateId)}" data-job-id="${esc(requestBody.jobId)}">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"></path></svg>
                <span>Download PDF</span>
            </button>
            <button type="button" class="btn-secondary tailor-download-btn" data-format="docx" data-candidate-id="${esc(requestBody.candidateId)}" data-job-id="${esc(requestBody.jobId)}">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"></path></svg>
                <span>Download DOCX</span>
            </button>`;
    }

    // ─── Download handlers for PDF/DOCX ─────────────────────────────────────
    document.addEventListener('click', async (e) => {
        const btn = e.target.closest('.tailor-download-btn');
        if (!btn) return;

        const format = btn.dataset.format; // 'pdf' or 'docx'
        const candidateId = Number(btn.dataset.candidateId);
        const jobId = btn.dataset.jobId;

        if (!candidateId || !jobId) {
            showToast('Missing required information for download.', 'error');
            return;
        }

        // Disable both buttons during generation
        const overlay = document.querySelector('.ms-overlay');
        if (!overlay) return;
        const buttons = overlay.querySelectorAll('.tailor-download-btn');
        buttons.forEach(b => {
            b.disabled = true;
            b.classList.add('loading');
        });

        const endpoint = `/api/v1/resume/tailor/${format}`;
        const acceptType = format === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
        const fallbackFilename = `tailored-resume.${format}`;

        try {
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': acceptType
                },
                body: JSON.stringify({ candidateId: candidateId, jobId: jobId })
            });

            if (!response.ok) {
                const errText = await response.text();
                let errorTitle = `Download failed (${response.status})`;
                try {
                    const errJson = JSON.parse(errText);
                    errorTitle = errJson.detail || errJson.title || errorTitle;
                } catch (_) { /* keep fallback */ }
                throw new Error(errorTitle);
            }

            const blob = await response.blob();
            if (!blob || blob.size === 0) {
                throw new Error('Received empty document from server.');
            }

            // Extract filename from Content-Disposition header if present
            let filename = fallbackFilename;
            const cd = response.headers.get('Content-Disposition');
            if (cd) {
                const match = cd.match(/filename\*?=(?:UTF-8''|")?([^";]+)/i);
                if (match && match[1]) {
                    filename = decodeURIComponent(match[1]).replace(/^"+|"+$/g, '');
                }
            }

            // Trigger download via object URL
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            a.style.display = 'none';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            // Revoke after a short delay to ensure the download starts
            setTimeout(() => URL.revokeObjectURL(url), 10000);

            showToast(`${format.toUpperCase()} downloaded successfully.`, 'success');
        } catch (err) {
            console.error(`${format.toUpperCase()} download error:`, err);
            showToast(window.apiError.describe(err, `Could not download the tailored resume as ${format.toUpperCase()}.`), 'error');
        } finally {
            buttons.forEach(b => {
                b.disabled = false;
                b.classList.remove('loading');
            });
        }
    });

    // ─── Application readiness review ───────────────────────────────────────
    function openAdvisorReview(data, ctx) {
        const adv = data || {};
        const context = ctx || {};
        const b = adv.scoreBreakdown || null;

        const rows = b ? [
            ['Resume (ATS) fit', b.atsReadinessScore],
            ['Job match', adv.jobMatchScore],
            ['Required-skill coverage', b.skillFitScore],
            ['Role fit', b.roleFitScore],
            ['Experience', b.experienceFitScore],
            ['Education', b.educationFitScore],
            ['Location', b.locationFitScore],
            ['Career-track fit', b.trackFitScore]
        ] : [];

        const matched = (b && b.matchedRequiredSkills) || [];
        const missing = (b && b.missingRequiredSkills) || [];
        const missingPreferred = (b && b.missingPreferredSkills) || [];
        const chips = (list, cls) => list.map(s => `<span class="skill-tag ${cls}">${esc(s)}</span>`).join('');

        const gaps = (adv.concerns || [])
            .map(String)
            .filter(text => text.indexOf('Missing required skill: ') !== 0
                && text.indexOf('Missing preferred skill: ') !== 0);

        const actions = (adv.recommendedActionDetails || []).map(d => `
            <li>
                <span class="career-step-focus">${esc(stepTitle(d))}</span>
                ${d.description ? `<span class="career-step-desc">${esc(d.description)}</span>` : ''}
            </li>`).join('');

        const body = `
            <div class="advisor-hero">
                <div class="advisor-hero-score">
                    <span class="fit-label">Application readiness</span>
                    <span class="fit-value">${numOrDash(adv.applicationReadinessScore)}<small>/100</small></span>
                </div>
                <div class="advisor-hero-rec">
                    <span class="fit-label">Recommendation</span>
                    <span class="fit-badge ${recommendationTone(adv.recommendation)}">${esc(recommendationLabel(adv.recommendation))}</span>
                </div>
            </div>

            ${adv.recommendationExplanation ? `
            <div class="modal-job-section">
                <h4 class="modal-section-title">Why</h4>
                <p class="modal-description">${esc(adv.recommendationExplanation)}</p>
            </div>` : ''}

            ${rows.length ? `
            <div class="modal-job-section">
                <h4 class="modal-section-title">Readiness breakdown</h4>
                <div class="fit-rows advisor-breakdown">${rows.map(([label, value]) => {
                    const pct = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
                    const tone = pct >= 70 ? 'is-good' : (pct >= 40 ? 'is-mid' : 'is-low');
                    return `
                        <div class="fit-row">
                            <span class="fit-row-label">${esc(label)}</span>
                            <span class="fit-bar"><span class="fit-bar-fill ${tone}" style="width:${pct}%"></span></span>
                            <span class="fit-row-value">${esc(pct)}</span>
                        </div>`;
                }).join('')}</div>
                <p class="career-report-note">Required skills: ${numOrDash(b.matchedRequiredCount)} matched, ${numOrDash(b.missingRequiredCount)} missing.
                Preferred skills: ${numOrDash(b.matchedPreferredCount)} matched, ${numOrDash(b.missingPreferredCount)} missing.</p>
            </div>` : ''}

            <div class="modal-job-section">
                <h4 class="modal-section-title">Skills you match</h4>
                ${matched.length ? `<div class="skills-tags-wrap">${chips(matched, 'required-skill match-hit')}</div>`
                    : `<p class="modal-empty-line">No required skills from this listing were found in your profile.</p>`}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Skills to address</h4>
                ${(missing.length || missingPreferred.length)
                    ? `<div class="skills-tags-wrap">${chips(missing, 'required-skill match-miss')}${chips(missingPreferred, 'preferred-skill match-miss')}</div>`
                    : `<p class="modal-empty-line">Nothing required by this listing is missing.</p>`}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Strengths</h4>
                ${listOrEmpty(adv.strengths, 'No strengths were reported.', 'fit-list is-good')}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Important gaps</h4>
                ${listOrEmpty(gaps, 'No gaps were reported.', 'fit-list is-bad')}
            </div>

            <div class="modal-job-section">
                <h4 class="modal-section-title">Recommended actions</h4>
                ${actions ? `<ol class="career-next-steps">${actions}</ol>`
                    : `<p class="modal-empty-line">No actions were produced.</p>`}
            </div>

            <p class="agent-note">Advice only. Nothing is submitted, approved or emailed by running this check.</p>`;

        window.modalShell.open({
            kicker: 'Application Readiness',
            title: adv.jobTitle || context.jobTitle || 'Selected role',
            subtitle: adv.company || context.company || '',
            body: body,
            footer: advisorFooterHtml(context)
        });
    }

    /**
     * Advisor footer. This is what makes the Applications portal reachable: running a
     * readiness check saves nothing, so the dialog says so plainly and offers the one
     * action that actually persists a prepared application. Without it, a user could
     * run readiness on every match and still find "No prepared applications yet".
     */
    function advisorFooterHtml(context) {
        const parts = [`<span class="modal-hint">This check is advice only — nothing is saved or submitted.</span>`];
        if (context.candidateId && context.jobId) {
            parts.push(`<button type="button" class="btn-apply application-prepare-btn"
                data-candidate-id="${esc(context.candidateId)}"
                data-job-id="${esc(context.jobId)}"
                data-job-title="${esc(context.jobTitle || '')}"
                data-company="${esc(context.company || '')}"
                data-location="${esc(context.location || '')}">
                <span>Prepare Application</span></button>`);
        }
        return parts.join('');
    }

    /**
     * Turns an improvement priority into an instruction. The backend's machine-readable
     * type is mapped to wording here, so labels such as REQUIRED_SKILL never reach the
     * screen and the raw "X is required by the target job…" sentence is not repeated.
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

    /** Renders a score as-is, including a legitimate 0 (only null/undefined becomes a dash). */
    function numOrDash(value) {
        return Number.isFinite(value) ? value : '—';
    }

    /** Priority wording for the recommendation band; thresholds belong to the backend. */
    function recommendationLabel(value) {
        const RECOMMENDATION_LABELS = {
            STRONGLY_RECOMMENDED: 'High priority',
            RECOMMENDED: 'High priority',
            APPLY_WITH_IMPROVEMENTS: 'Medium priority',
            LOW_PRIORITY: 'Low priority',
            NOT_RECOMMENDED: 'Not recommended'
        };
        return RECOMMENDATION_LABELS[value] || '—';
    }

    function recommendationTone(value) {
        switch (value) {
            case 'STRONGLY_RECOMMENDED':
            case 'RECOMMENDED': return 'fit-high';
            case 'APPLY_WITH_IMPROVEMENTS': return 'fit-medium';
            default: return 'fit-low';
        }
    }

    // ─── Match Details modal ────────────────────────────────────────────────
    // Matches are keyed by job id so a card button only needs a data-job-id attribute.
    // The dialog itself comes from the single global modal shell — this page never
    // creates its own overlay, so it cannot stack behind another analysis.
    const matchRegistry = new Map();

    function openMatchDetails(jobId) {
        const m = matchRegistry.get(String(jobId));
        if (!m) {
            showToast('Match details are no longer available for this listing. Run matching again.', 'error');
            return;
        }
        const job = m.job || {};
        window.modalShell.open({
            kicker: 'Match Details',
            title: job.title || 'Untitled Role',
            subtitle: job.company || 'Unknown Company',
            body: buildMatchDetailsHtml(m),
            footer: buildMatchDetailsFooter(m)
        });
    }

    /** Sub-scores as 0-100 factors. The match model stores them as 0.0-1.0 doubles. */
    function factorRows(m) {
        const rows = [
            ['Skill fit', m.skillScore],
            ['Role fit', m.roleScore],
            ['Experience fit', m.experienceScore],
            ['Education fit', m.educationScore],
            ['Location fit', m.locationScore],
            ['Career-track fit', m.trackScore]
        ];
        const known = rows.filter(([, v]) => Number.isFinite(v));
        if (!known.length) {
            return `<p class="modal-empty-line">Match factors are not available for this listing.</p>`;
        }
        return `<div class="fit-rows">${known.map(([label, value]) => {
            const pct = Math.max(0, Math.min(100, Math.round(value * 100)));
            const tone = pct >= 70 ? 'is-good' : (pct >= 40 ? 'is-mid' : 'is-low');
            return `
                <div class="fit-row">
                    <span class="fit-row-label">${esc(label)}</span>
                    <span class="fit-bar"><span class="fit-bar-fill ${tone}" style="width:${pct}%"></span></span>
                    <span class="fit-row-value">${esc(pct)}</span>
                </div>`;
        }).join('')}</div>`;
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

        const matched = chips(m.matchedSkills, 'required-skill', true);
        const missing = chips(m.missingSkills, 'required-skill', false);
        const prefHit = chips(m.matchedPreferredSkills, 'preferred-skill', true);
        const prefMiss = chips(m.missingPreferredSkills, 'preferred-skill', false);
        const preferred = prefHit || prefMiss ? prefHit + prefMiss : '';

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

            ${rows('Skills you already match', matched ? `<div class="skills-tags-wrap">${matched}</div>` : '',
                'No required skills from this listing were found in your profile.')}

            ${rows('Required skills you are missing', missing ? `<div class="skills-tags-wrap">${missing}</div>` : '',
                'Nothing required by this listing is missing from your profile.')}

            ${rows('Preferred skills', preferred ? `<div class="skills-tags-wrap">${preferred}</div>` : '',
                'This listing states no preferred skills.')}

            <div class="modal-job-section">
                <h4 class="modal-section-title">Match factors</h4>
                ${factorRows(m)}
            </div>

            ${rows('Your strengths', strengths ? `<ul class="match-sc-list">${strengths}</ul>` : '',
                'No strengths were reported for this match.')}

            ${rows('Gaps and concerns', concerns ? `<ul class="match-sc-list">${concerns}</ul>` : '',
                'No gaps were reported for this match.')}

            ${rows('Recommended next actions', nextActionsHtml(m), '')}

            <div class="job-meta-row modal-source-row">
                <div class="job-meta-item" title="Listing Source"><span>Source: ${esc(job.source || 'Unknown')}</span></div>
            </div>`;
    }

    /**
     * Next actions derived only from what the listing actually asks for. Each line names
     * a real missing skill or a real step in this workflow — no generic advice.
     */
    function nextActionsHtml(m) {
        const actions = [];
        (m.missingSkills || []).slice(0, 5).forEach(s => actions.push('Learn ' + s));
        (m.missingPreferredSkills || []).slice(0, 3).forEach(s => actions.push('Review ' + s + ' (preferred, not required)'));
        actions.push('Tailor your resume to emphasise the skills you already match');
        if ((Number(m.matchScore) || 0) >= 60) {
            actions.push('Prepare this application for review');
        }
        return `<ul class="match-sc-list">${actions.map(a => `<li>${esc(a)}</li>`).join('')}</ul>`;
    }

    /**
     * Dialog actions reuse the same delegated handlers as the cards, so there is exactly
     * one implementation of each action.
     */
    function buildMatchDetailsFooter(m) {
        const job = m.job || {};
        const jobId = esc(job.id || '');
        const score = Number(m.matchScore) || 0;
        const parts = [];

        if (candidateId) {
            parts.push(`<button type="button" class="btn-career-agent" data-career-agent="true" data-job-id="${jobId}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}">
                <span>Career Analysis</span></button>`);
            parts.push(`<button type="button" class="btn-tailor resume-tailor-btn" data-candidate-id="${esc(candidateId)}" data-job-id="${jobId}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}">
                <span>Tailor Resume</span></button>`);
            parts.push(`<button type="button" class="btn-advisor application-advisor-btn" data-candidate-id="${esc(candidateId)}" data-job-id="${jobId}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}" data-location="${esc(job.location || '')}">
                <span>Application Readiness</span></button>`);
            if (score >= 60) {
                parts.push(`<button type="button" class="btn-apply application-prepare-btn" data-candidate-id="${esc(candidateId)}" data-job-id="${jobId}" data-job-title="${esc(job.title || '')}" data-company="${esc(job.company || '')}" data-location="${esc(job.location || '')}">
                    <span>Prepare Application</span></button>`);
            }
        }

        if (window.jobLink) {
            parts.push(window.jobLink.actionHtml(job, { withNote: false }));
        }

        const hint = `<span class="modal-hint">${candidateId
            ? 'This platform never submits an application for you.'
            : `Upload a <a href="resume.html">resume</a> to run analysis and prepare an application.`}</span>`;

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