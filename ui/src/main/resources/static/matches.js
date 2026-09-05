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
    const keywordsInput = document.getElementById('matchesKeywordsInput');
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

        [keywordsInput, locationInput].forEach(input => {
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    runMatch();
                }
            });
        });
    }

    function resetFilters(autoRun = true) {
        keywordsInput.value = '';
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

        const rawKeywords = keywordsInput.value.trim();
        const keywords = rawKeywords ? rawKeywords.split(/[,+]/).map(s => s.trim()).filter(Boolean) : null;
        const track = trackSelect.value;
        const minScore = minScoreSelect.value;

        const payload = {
            candidateProfileId: Number(candidateId),
            keywords: keywords,
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
        const sourceName = data.source || 'MOCK_SOURCE';

        // Source banner
        sourceBanner.hidden = false;
        if (isLive) {
            sourceBanner.className = 'source-info-banner source-live';
            bannerTitle.textContent = `Live Job Source Active (${sourceName})`;
        } else {
            sourceBanner.className = 'source-info-banner';
            bannerTitle.textContent = `Development Mock Source Active (${sourceName})`;
        }
        bannerMessage.textContent = data.message || 'Matched against the job catalog.';

        // Counts
        resultsCount.textContent = data.totalJobs || 0;
        resultsCountLabel.textContent = (data.totalJobs === 1) ? 'job match' : 'job matches';

        // Active query summary
        activeSummary.innerHTML = '';
        if (candidateName) activeSummary.appendChild(createChip('Profile: ' + candidateName));
        if (payload.keywords && payload.keywords.length) {
            activeSummary.appendChild(createChip('Keywords: ' + payload.keywords.join(', ')));
        }
        if (payload.location) activeSummary.appendChild(createChip('Location: ' + payload.location));
        if (payload.minScore) activeSummary.appendChild(createChip('Min score: ' + payload.minScore));
        if (payload.careerTrack) activeSummary.appendChild(createChip('Track: ' + payload.careerTrack));

        cardsGrid.innerHTML = '';
        if (matches.length === 0) {
            emptyDesc.textContent = data.totalJobs === 0
                ? 'No jobs were found for the search parameters. Try broadening keywords or removing the location filter.'
                : 'No roles cleared the current filters. Try lowering the minimum score or removing the track filter.';
            empty.hidden = false;
            return;
        }

        empty.hidden = true;
        matches.forEach(match => cardsGrid.appendChild(createMatchCard(match)));
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
        `;

        if (window.jobDetails) window.jobDetails.register(job);

        return card;
    }

    /**
     * Source-aware card action (shared rules with jobs page).
     * MOCK_SOURCE listings never navigate to mockjobs.local — they open the
     * internal Job Details modal instead.
     */
    function matchJobFooterHtml(job) {
        const isExternal = window.jobDetails && !window.jobDetails.isMock(job) && window.jobDetails.hasValidUrl(job);
        if (isExternal) {
            return `<a href="${esc(job.sourceUrl)}" target="_blank" rel="noopener noreferrer" class="btn-view-job">
                <span>View Original Listing</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>
            </a>`;
        }
        const label = window.jobDetails ? window.jobDetails.actionLabel(job) : 'View Details';
        return `<button type="button" class="btn-view-job btn-view-details" data-open-job-details="true" data-job-id="${esc(job.id || '')}">
            <span>${esc(label)}</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>
        </button>`;
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
        const app = data && data.applicationId != null ? data : {};
        document.getElementById('prepReviewSubtitle').textContent = app.jobTitle || 'Job';
        document.getElementById('prepReviewJobTitle').textContent = app.jobTitle || '—';
        document.getElementById('prepReviewCompany').textContent = app.company || '—';
        document.getElementById('prepReviewMatchScore').textContent = app.matchScore != null ? `${app.matchScore}/100` : '—';

        const matching = app.matchingSkills || [];
        const missing = app.missingSkills || [];
        const highlights = app.resumeHighlights || [];

        const matchingUl = document.getElementById('prepReviewMatching');
        matchingUl.innerHTML = matching.length
            ? matching.map(s => `<li>${esc(s)}</li>`).join('')
            : '<li>No matching skills listed.</li>';

        const missingUl = document.getElementById('prepReviewMissing');
        missingUl.innerHTML = missing.length
            ? missing.map(s => `<li>${esc(s)}</li>`).join('')
            : '<li>No missing skills to address.</li>';

        document.getElementById('prepReviewSummary').textContent = app.tailoredProfessionalSummary || '—';
        document.getElementById('prepReviewCoverLetter').textContent = app.coverLetter || '—';

        const highlightsUl = document.getElementById('prepReviewHighlights');
        highlightsUl.innerHTML = highlights.length
            ? highlights.map(s => `<li>${esc(s)}</li>`).join('')
            : '<li>No resume highlights available.</li>';

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
        if (e.target.closest('#prepReviewClose') || e.target.closest('#prepReviewDone')) {
            closePreparedReview();
        }
        if (e.target === prepReviewOverlay) {
            closePreparedReview();
        }
        const copyBtn = e.target.closest('.copy-btn');
        if (copyBtn) {
            copyElementText(copyBtn.dataset.copyTarget, copyBtn);
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && prepReviewOverlay && !prepReviewOverlay.hidden) {
            closePreparedReview();
        }
    });

    // ─── Boot ─────────────────────────────────────────────────────────────────
    init();
})();