/**
 * AI Job Agent — Job Search page.
 * Uses the deterministic /api/v1/jobs/search engine (no LLM) to browse the
 * job catalog, and the shared jobDetails.js modal for per-job detail + match
 * checking.
 */
(() => {
    'use strict';

    const API_ENDPOINT = '/api/v1/jobs/search';

    const searchBtn = document.getElementById('jobsSearchBtn');
    const resetBtn = document.getElementById('jobsResetBtn');
    const form = document.getElementById('jobsSearchForm');

    const keywordsInput = document.getElementById('jobsKeywordsInput');
    const locationInput = document.getElementById('jobsLocationInput');
    const experienceSelect = document.getElementById('jobsExperienceSelect');
    const typeSelect = document.getElementById('jobsTypeSelect');
    const limitSelect = document.getElementById('jobsLimitSelect');

    const sourceBanner = document.getElementById('jobsSourceBanner');
    const sourceBannerTitle = document.getElementById('jobsBannerTitle');
    const sourceBannerMessage = document.getElementById('jobsBannerMessage');

    const resultsCount = document.getElementById('jobsResultsCount');
    const resultsCountLabel = document.getElementById('jobsResultsCountLabel');
    const activeSummary = document.getElementById('jobsActiveSummary');
    const loading = document.getElementById('jobsLoading');
    const empty = document.getElementById('jobsEmpty');
    const emptyDesc = document.getElementById('jobsEmptyDesc');
    const cardsGrid = document.getElementById('jobsCardsGrid');
    const toastContainer = document.getElementById('toastContainer');

    let searchInFlight = false;

    // ─── Toast ──────────────────────────────────────────────────────────────
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

    function parseKeywords(value) {
        return String(value || '')
            .split(',')
            .map(s => s.trim())
            .filter(Boolean);
    }

    function formatEmploymentType(type) {
        if (!type) return 'Full-time';
        switch (String(type).toUpperCase()) {
            case 'FULL_TIME': return 'Full-time';
            case 'INTERNSHIP': return 'Internship';
            case 'CONTRACT': return 'Contract';
            case 'PART_TIME': return 'Part-time';
            default: return type;
        }
    }

    // ─── Source banner ──────────────────────────────────────────────────────
    function updateSourceBanner(data) {
        if (!data) { sourceBanner.hidden = true; return; }
        sourceBannerTitle.textContent = data.live ? 'Live Source Active' : 'Development Mock Source Active';
        sourceBannerMessage.textContent = data.message || (data.live ? 'Jobs loaded from a live source.' : 'Browsing the development mock job catalog.');
        // Show combined source names if multiple sources were queried
        if (data.source && data.source.includes(', ')) {
            sourceBannerMessage.textContent += ' (' + data.source + ')';
        }
        sourceBanner.className = 'source-info-banner' + (data.live ? ' source-live' : '');
        sourceBanner.hidden = false;
    }

    // ─── Active summary chips ───────────────────────────────────────────────
    function createChip(text) {
        const chip = document.createElement('span');
        chip.className = 'summary-chip';
        chip.textContent = text;
        return chip;
    }

    function renderActiveSummary(payload) {
        activeSummary.innerHTML = '';
        if (payload.keywords && payload.keywords.length) {
            activeSummary.appendChild(createChip('Keywords: ' + payload.keywords.join(', ')));
        }
        if (payload.location) activeSummary.appendChild(createChip('Location: ' + payload.location));
        if (payload.experience) activeSummary.appendChild(createChip('Experience: ' + payload.experience));
        if (payload.employmentType) activeSummary.appendChild(createChip('Type: ' + formatEmploymentType(payload.employmentType)));
    }

    // ─── Card rendering ─────────────────────────────────────────────────────
    function jobFooterHtml(job) {
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

    function createJobCard(job) {
        const card = document.createElement('article');
        card.className = 'match-card';
        card.setAttribute('data-id', job.id || '');

        const reqTags = (job.requiredSkills || [])
            .map(s => `<span class="skill-tag required-skill">${esc(s)}</span>`).join('');
        const prefTags = (job.preferredSkills || [])
            .map(s => `<span class="skill-tag preferred-skill">${esc(s)}</span>`).join('');

        card.innerHTML = `
            <div class="match-card-head">
                <div class="match-card-title-group">
                    <h3 class="job-title">${esc(job.title || 'Untitled Role')}</h3>
                    <div class="job-company">${esc(job.company || 'Unknown Company')}</div>
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
                    <span>${esc(formatEmploymentType(job.employmentType))}</span>
                </div>
                <div class="job-meta-item" title="Listing Source">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>
                    <span>${esc(job.source || 'UNKNOWN')}</span>
                </div>
            </div>

            <div class="match-explanation">${esc((job.description || '').slice(0, 220))}${(job.description || '').length > 220 ? '…' : ''}</div>

            ${reqTags ? `
                <div class="job-skills-section">
                    <div class="skills-sub-group">
                        <span class="skills-label">Required Skills:</span>
                        <div class="skills-tags-wrap">${reqTags}</div>
                    </div>
                </div>` : ''}
            ${prefTags ? `
                <div class="job-skills-section">
                    <div class="skills-sub-group">
                        <span class="skills-label">Preferred Skills:</span>
                        <div class="skills-tags-wrap">${prefTags}</div>
                    </div>
                </div>` : ''}

            <div class="job-card-footer">
                ${jobFooterHtml(job)}
            </div>
        `;

        if (window.jobDetails) window.jobDetails.register(job);
        return card;
    }

    // ─── Search ─────────────────────────────────────────────────────────────
    function buildPayload() {
        const payload = {};
        const keywords = parseKeywords(keywordsInput.value);
        if (keywords.length) payload.keywords = keywords;
        if (locationInput.value.trim()) payload.location = locationInput.value.trim();
        if (experienceSelect.value) payload.experience = experienceSelect.value;
        if (typeSelect.value) payload.employmentType = typeSelect.value;
        if (limitSelect.value) payload.limit = Number(limitSelect.value);
        return payload;
    }

    function runSearch() {
        if (searchInFlight) return;
        searchInFlight = true;
        searchBtn.disabled = true;

        const payload = buildPayload();
        renderActiveSummary(payload);
        loading.hidden = false;
        empty.hidden = true;
        cardsGrid.innerHTML = '';

        fetch(API_ENDPOINT, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.detail || `HTTP ${response.status}`); });
            }
            return response.json();
        })
        .then(data => {
            loading.hidden = true;
            updateSourceBanner(data);

            const jobs = data.jobs || [];
            resultsCount.textContent = data.total != null ? data.total : jobs.length;
            resultsCountLabel.textContent = (data.total === 1) ? 'job' : 'jobs';

            // Highlight if all results are from mock source
            const allMock = jobs.length > 0 && jobs.every(job => job.source === 'MOCK_SOURCE');

            cardsGrid.innerHTML = '';
            if (jobs.length === 0) {
                emptyDesc.textContent = 'No jobs matched the current filters. Try broadening your keywords or removing filters.';
                empty.hidden = false;
                return;
            }
            jobs.forEach(job => cardsGrid.appendChild(createJobCard(job)));
            // Show notice if all results are mock data
            if (allMock) {
                showToast('Showing development mock jobs. Enable the public job source for live listings.', 'info');
            }
        })
        .catch(err => {
            console.error('Error searching jobs:', err);
            loading.hidden = true;
            showToast(err.message || 'Failed to load jobs.', 'error');
            emptyDesc.textContent = err.message || 'Failed to load jobs. Please try again later.';
            empty.hidden = true;
        })
        .finally(() => {
            searchInFlight = false;
            searchBtn.disabled = false;
        });
    }

    function resetFilters() {
        keywordsInput.value = '';
        locationInput.value = '';
        experienceSelect.value = '';
        typeSelect.value = '';
        limitSelect.value = '';
        runSearch();
    }

    // ─── Init ───────────────────────────────────────────────────────────────
    function init() {
        form.addEventListener('submit', (e) => { e.preventDefault(); runSearch(); });
        searchBtn.addEventListener('click', runSearch);
        resetBtn.addEventListener('click', resetFilters);
        empty.querySelector('#jobsEmptyResetBtn').addEventListener('click', resetFilters);

        runSearch();
    }

    init();
})();
