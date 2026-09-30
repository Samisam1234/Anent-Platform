/**
 * AI Job Agent — Applications Page (Milestone 5)
 * Handles fetching, displaying, editing, and approving prepared job applications.
 */

(() => {
    'use strict';

    // ─── DOM References ───────────────────────────────────────────────────────
    const applicationsMain = document.getElementById('applicationsMain');
    const applicationsOnboarding = document.getElementById('applicationsOnboarding');
    const applicationsListSection = document.getElementById('applicationsListSection');
    const applicationsCardsGrid = document.getElementById('applicationsCardsGrid');
    const applicationsEmpty = document.getElementById('applicationsEmpty');
    const applicationsCount = document.getElementById('applicationsCount');
    const applicationsStatusFilterEl = document.getElementById('applicationsStatusFilter');
    const applicationsEmptyMessage = document.getElementById('applicationsEmptyMessage');
    const applicationsShowAllBtn = document.getElementById('applicationsShowAllBtn');
    const applicationsRetryBtn = document.getElementById('applicationsRetryBtn');
    const applicationDetailSection = document.getElementById('applicationDetailSection');
    const applicationDetailTitle = document.getElementById('applicationDetailTitle');
    const applicationDetailStatus = document.getElementById('applicationDetailStatus');
    const applicationDetailJobId = document.getElementById('applicationDetailJobId');
    const applicationDetailCompany = document.getElementById('applicationDetailCompany');
    const applicationDetailLocation = document.getElementById('applicationDetailLocation');
    const applicationDetailSummary = document.getElementById('applicationDetailSummary');
    const applicationDetailCoverLetter = document.getElementById('applicationDetailCoverLetter');
    const applicationDetailAnswers = document.getElementById('applicationDetailAnswers');
    const applicationDetailStrengths = document.getElementById('applicationDetailStrengths');
    const applicationDetailGaps = document.getElementById('applicationDetailGaps');
    const applicationDetailRecommendation = document.getElementById('applicationDetailRecommendation');
    const editApplicationBtn = document.getElementById('editApplicationBtn');
    const approveApplicationBtn = document.getElementById('approveApplicationBtn');
    const assistedApplyBtn = document.getElementById('assistedApplyBtn');
    const sendEmailBtn = document.getElementById('sendEmailBtn');
    const goToResumeBtn = document.getElementById('goToResumeBtn');
    const backToApplicationsBtn = document.getElementById('backToApplicationsBtn');
    const profileBadge = document.getElementById('profileBadge');
    const profileStatusText = document.getElementById('profileStatusText');

    // Edit mode elements
    const editModeSection = document.getElementById('editModeSection');
    const editCoverLetter = document.getElementById('editCoverLetter');
    const editProfessionalSummary = document.getElementById('editProfessionalSummary');
    const editApplicationAnswers = document.getElementById('editApplicationAnswers');
    const saveEditBtn = document.getElementById('saveEditBtn');
    const cancelEditBtn = document.getElementById('cancelEditBtn');

    const API_ENDPOINT = '/api/v1/applications';
    const EMAIL_ENDPOINT = '/api/v1/applications/email/send';
    const JOB_ENDPOINT = '/api/v1/jobs/';
    const CANDIDATE_ENDPOINT = '/api/v1/candidate/';

    // Shared UI + backend recipient syntax; mirrors ApplicationEmailService.
    const EMAIL_RE = /^[\w.]+@([a-z0-9-]+\.)+[a-z]{2,6}$/i;
    // Pre-fill convenience when available; never a guessed address.
    const LS_CANDIDATE_EMAIL = 'agentplatform:candidateEmail';

    /**
     * User-facing lifecycle wording for the statuses the backend actually stores
     * (ApplicationStatus). Nothing here claims a submission happened: this platform
     * has no submission path, so the furthest honest state is "Approved for
     * application", meaning the user has signed off on the prepared package.
     */
    const STATUS_LABELS = {
        DRAFT: { label: 'Draft', cls: 'draft', hint: 'Saved but not yet prepared.' },
        GENERATED: { label: 'Prepared', cls: 'generated', hint: 'Application package is ready for your review.' },
        UNDER_REVIEW: { label: 'Ready for review', cls: 'under-review', hint: 'Waiting for your review before approval.' },
        APPROVED_FOR_APPLICATION: { label: 'Approved for application', cls: 'approved', hint: 'You approved this package. Apply on the employer site to send it.' },
        REJECTED: { label: 'Not pursuing', cls: 'rejected', hint: 'You decided not to pursue this application.' },
        ARCHIVED: { label: 'Archived', cls: 'archived', hint: 'Archived.' },
        EMAIL_SENT: { label: 'Email sent', cls: 'email-sent', hint: 'Package email sent (reported by email service); employer submission not confirmed.' }
    };

    // Cache of resolved job listings so a list of applications for the same role does
    // not refetch, and a listing that is no longer indexed is only probed once.
    const jobLookupCache = new Map();
    // Same idea for candidate kit profiles: fetch once per candidate, keep misses cached.
    const candidateLookupCache = new Map();

    // Shared with matches.js
    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';

    let candidateId = null;
    let candidateName = null;
    let currentApplicationId = null;
    let isEditing = false;
    let originalData = {}; // Store original data for cancel

    // Active status filter for the applications list (?status=...). Empty string = All
    // Applications. Not persisted to localStorage: the list always starts unfiltered.
    let applicationsStatusFilter = '';

    // Active Apply Kit session (Phase 12.8, Slice 3): one kit at a time. Holds the
    // resolved application/job/destination plus the edited values, so the prepare →
    // final review → employer handoff steps all read the same snapshot.
    let kitSession = null; // { app, job, target, fields, currentValues, acknowledged }

    // ─── Status Badge ─────────────────────────────────────────────────────────
    function updateProfileBadge() {
        if (candidateId) {
            profileBadge.classList.add('status-live');
            profileStatusText.textContent = `Profile: ${candidateName || 'Candidate'}`;
            profileBadge.hidden = false;
        } else {
            profileBadge.classList.remove('status-live');
            profileStatusText.textContent = 'No profile';
            profileBadge.hidden = false;
        }
    }

    // ─── Toast notifications ──────────────────────────────────────────────────
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast ${type === 'error' ? 'toast-error' : ''}`;
        const icon = type === 'error'
            ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'
            : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
        toast.innerHTML = `${icon}<span>${message}</span>`;
        const toastContainer = document.getElementById('toastContainer');
        toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(30px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    // ─── Escape HTML ──────────────────────────────────────────────────────────
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

    function splitList(text) {
        if (!text) return [];
        return String(text)
            .split(/[,\u2014-]\s*|\s+\|\|\s+/)
            .map(s => s.trim())
            .filter(Boolean);
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

    // ─── Render application card ──────────────────────────────────────────────
    /**
     * Renders one prepared application. Every line states something the stored record
     * actually holds — status, match score, whether a tailored summary and cover letter
     * exist, and the advisor recommendation recorded at preparation time.
     */
    function renderApplicationCard(application) {
        const card = document.createElement('div');
        card.className = 'application-card';
        card.dataset.id = application.id;

        const statusKey = application.applicationStatus || 'DRAFT';
        const status = STATUS_LABELS[statusKey]
            || { label: humanizeStatus(statusKey), cls: statusKey.toLowerCase().replace(/_/g, '-'), hint: '' };

        const job = application.jobTitle || 'Unknown Role';
        const company = application.company || 'Unknown Company';
        const location = application.location || 'Not Specified';
        const isApproved = statusKey === 'APPROVED_FOR_APPLICATION';

        card.innerHTML = `
            <div class="application-card-header">
                <h3 class="application-card-job-title">${esc(job)}</h3>
                <div class="application-card-company">${esc(company)}</div>
                <span class="application-card-status badge-${status.cls}" title="${esc(status.hint)}">${esc(status.label)}</span>
            </div>

            <div class="application-card-meta">
                <div class="application-card-meta-item">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>
                    <span>${esc(location)}</span>
                </div>
                <div class="application-card-meta-item" title="Deterministic required-skill coverage at preparation time">
                    <span>Skill coverage: ${application.matchScore != null ? esc(application.matchScore) + '/100' : 'not calculated'}</span>
                </div>
            </div>

            <div class="application-card-package">
                <span class="package-chip ${application.generatedResumeSummary ? 'is-present' : 'is-missing'}">
                    ${application.generatedResumeSummary ? 'Tailored summary' : 'No tailored summary'}</span>
                <span class="package-chip ${application.coverLetter ? 'is-present' : 'is-missing'}">
                    ${application.coverLetter ? 'Cover letter' : 'No cover letter'}</span>
                <span class="package-chip ${application.recommendation ? 'is-present' : 'is-missing'}">
                    ${application.recommendation ? 'Advisor: ' + esc(humanizeStatus(application.recommendation)) : 'No advisor result'}</span>
            </div>

            <div class="application-card-external" data-external-slot="${esc(application.id)}" hidden></div>

            <div class="application-card-footer">
                <button class="btn-view application-view-btn" data-id="${esc(application.id)}">Review</button>
                <button class="btn-apply application-approve-btn" data-id="${esc(application.id)}" ${isApproved ? 'disabled' : ''}>
                    ${isApproved ? 'Approved' : 'Approve for application'}</button>
            </div>
        `;

        // Add click handlers
        const viewBtn = card.querySelector('.application-view-btn');
        viewBtn.addEventListener('click', () => openReviewModal(application.id));

        const approveBtn = card.querySelector('.application-approve-btn');
        approveBtn.addEventListener('click', () => {
            if (isApproved) {
                showToast('This application has already been approved.', 'info');
                return;
            }
            approveApplication(application.id);
        });

        attachExternalApplicationLink(card, application);
        return card;
    }

    /**
     * Marks the application as EXTERNAL and links the employer's own application page,
     * but only once the stored job id resolves to a listing with a real http(s) URL.
     * A listing that is no longer indexed is reported as unavailable — the platform
     * never guesses or reconstructs an application URL.
     */
    /**
     * Renders the application destination for a stored application.
     *
     * The destination is resolved by jobLink.js so this portal agrees with Jobs, Match
     * Details and the prepared-application footer: the employer's own application page is
     * offered only when the source actually supplied one. A board listing page is labelled
     * "View Job Listing" and is never described as the employer's application page.
     */
    function externalDestinationHtml(job) {
        const target = window.jobLink
            ? window.jobLink.resolve(job)
            : { kind: 'none', label: 'Application Link Unavailable', reason: 'Link resolution is unavailable.' };

        if (target.kind === 'none') {
            const reason = target.reason
                || 'The original listing is no longer available, so the application link cannot be shown.';
            return `<span class="external-badge is-muted">Application</span>
                    <span class="external-note">${esc(reason)}</span>`;
        }

        const title = target.kind === 'employer'
            ? "Opens the employer's own application page in a new tab"
            : 'Opens the original job listing in a new tab';
        const badge = target.kind === 'employer' ? 'Employer application' : 'Job listing';
        const cls = target.kind === 'employer' ? 'btn-apply-external' : 'btn-source-listing';
        const note = target.kind === 'employer'
            ? 'This platform does not submit applications on your behalf.'
            : (target.reason || 'This source provides the listing page, not a direct employer application link.');
        return `<span class="external-badge">${esc(badge)}</span>
                <a class="${cls}" href="${esc(target.url)}" target="_blank" rel="noopener noreferrer"
                   title="${esc(title)}">${esc(target.label)}</a>
                <span class="external-note">${esc(note)}</span>`;
    }

    function attachExternalApplicationLink(card, application) {
        const slotEl = card.querySelector('[data-external-slot]');
        if (!slotEl || !application.jobId) return;

        resolveJob(application.jobId).then(job => {
            slotEl.hidden = false;
            slotEl.innerHTML = externalDestinationHtml(job);
        });
    }

    /**
     * Shows the external-application link in the detail view, using the same resolver
     * (and cache) as the cards so a listing is looked up once either way.
     */
    function renderDetailExternal(app) {
        const slotEl = document.getElementById('applicationDetailExternal');
        if (!slotEl) return;
        if (!app || !app.jobId) {
            slotEl.hidden = true;
            slotEl.innerHTML = '';
            return;
        }
        slotEl.hidden = false;
        slotEl.innerHTML = '<span class="external-note">Checking the original listing…</span>';
        resolveJob(app.jobId).then(job => {
            slotEl.innerHTML = externalDestinationHtml(job);
        });
    }

    /**
     * Read-only review of a prepared application in the single global modal shell, so it
     * can never stack behind another dialog. Shows the package contents, the stored
     * status and the employer application link when the listing still resolves.
     *
     * Nothing here implies the platform submitted anything: the furthest state the
     * backend can reach is an approval the user granted.
     */
    function openReviewModal(id) {
        fetch(`${API_ENDPOINT}/${id}`)
            .then(response => {
                if (!response.ok) throw new Error(`Failed to fetch application: ${response.status}`);
                return response.json();
            })
            .then(app => {
                if (!app || !app.id) {
                    showToast('Application not found.', 'error');
                    return;
                }
                renderReviewModal(app);
            })
            .catch(err => {
                console.error('Error reviewing application:', err);
                showToast(window.apiError.describe(err, 'Failed to load application.'), 'error');
            });
    }

    function renderReviewModal(app) {
        const statusKey = app.applicationStatus || 'DRAFT';
        const status = STATUS_LABELS[statusKey]
            || { label: humanizeStatus(statusKey), cls: 'draft', hint: '' };

        const list = (value, emptyText) => {
            const items = splitList(value);
            return items.length
                ? `<ul class="match-sc-list">${items.map(v => `<li>${esc(v)}</li>`).join('')}</ul>`
                : `<p class="modal-empty-line">${esc(emptyText)}</p>`;
        };
        const block = (title, html) => `
            <div class="modal-job-section">
                <h4 class="modal-section-title">${esc(title)}</h4>
                ${html}
            </div>`;

        const preparedOn = app.createdAt
            ? new Date(app.createdAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
            : 'not recorded';

        window.modalShell.open({
            kicker: 'Prepared Application',
            title: `${app.jobTitle || 'Role'} — ${app.company || 'Company'}`,
            subtitle: `${status.label} · prepared ${preparedOn} · not submitted`,
            body: `
                <div class="prep-summary">
                    <span class="summary-chip">Job ID: ${esc(app.jobId || '—')}</span>
                    <span class="summary-chip">Location: ${esc(app.location || 'Not specified')}</span>
                    <span class="summary-chip">Skill coverage: ${app.matchScore != null ? esc(app.matchScore) + '/100' : 'not calculated'}</span>
                    <span class="package-chip ${app.generatedResumeSummary ? 'is-present' : 'is-missing'}">${app.generatedResumeSummary ? 'Tailored summary' : 'No tailored summary'}</span>
                    <span class="package-chip ${app.coverLetter ? 'is-present' : 'is-missing'}">${app.coverLetter ? 'Cover letter' : 'No cover letter'}</span>
                </div>

                <div class="application-card-external" id="reviewExternal"></div>

                ${block('Tailored professional summary',
                    `<p class="modal-description">${esc(app.generatedResumeSummary || '—')}</p>`)}
                ${block('Cover letter',
                    `<p class="modal-description prep-pre">${esc(app.coverLetter || 'No cover letter generated')}</p>`)}
                ${block('Suggested application answers', list(app.applicationAnswers, 'No suggested answers available.'))}
                ${block('Your strengths', list(app.candidateStrengths, 'No candidate strengths listed.'))}
                ${block('Skills to address', list(app.missingSkills, 'No missing skills to address.'))}
                ${block('Advisor result',
                    app.recommendation
                        ? `<p class="modal-description">${esc(humanizeStatus(app.recommendation))}</p>`
                        : `<p class="modal-empty-line">No advisor result recorded for this application.</p>`)}

                <p class="agent-note">This package is prepared for your review. The platform has not submitted it and cannot submit it — you apply on the employer's own site.</p>`,
            footer: `
                <span class="modal-hint">Nothing has been submitted.</span>
                <button type="button" class="btn-secondary" data-edit-application="${esc(app.id)}">Edit package</button>`
        });

        // Reuse the same resolver and markup as the list view.
        const slot = document.getElementById('reviewExternal');
        if (slot) {
            resolveJob(app.jobId).then(job => {
                slot.innerHTML = externalDestinationHtml(job);
            });
        }
    }

    // ─── Apply Kit field helpers (Phase 12.8, Slice 2) ─────────────────────────
    /**
     * Resolves a stored candidate id to its allowlisted kit profile, caching both
     * hits and misses. A failure resolves to null — the kit then shows the
     * candidate-derived fields as unavailable instead of failing the whole screen.
     */
    function resolveCandidate(candidateId) {
        if (candidateId == null) return Promise.resolve(null);
        const key = String(candidateId);
        if (candidateLookupCache.has(key)) return Promise.resolve(candidateLookupCache.get(key));
        return fetch(CANDIDATE_ENDPOINT + encodeURIComponent(key))
            .then(response => response.ok ? response.json() : null)
            .catch(() => null)
            .then(candidate => {
                candidateLookupCache.set(key, candidate || null);
                return candidate || null;
            });
    }

    /** Joins the allowlisted skill list into one editable line: deduped, capped. */
    function joinSkills(skills) {
        if (!Array.isArray(skills) || skills.length === 0) return null;
        const seen = new Set();
        const out = [];
        for (const s of skills) {
            const t = String(s || '').trim();
            if (!t || seen.has(t.toLowerCase())) continue;
            seen.add(t.toLowerCase());
            out.push(t);
        }
        if (!out.length) return null;
        const joined = out.join(', ');
        // ponytail: soft cap at 2000 chars with a visible truncation marker; the edit
        // box stays honest about it, a real text store would add length validation.
        return joined.length > 2000 ? joined.slice(0, 2000).replace(/,\s*$/, '') + ' …' : joined;
    }

    /**
     * Builds the whitelisted, source-labelled kit fields for one package.
     * Values come only from the stored candidate profile or this application record;
     * anything missing stays null and the row says so instead of inventing a value.
     * Links (GitHub/LinkedIn) are deliberately not offered — the parser stores none.
     */
    function buildKitFields(app, candidate) {
        const location = candidate && (candidate.location || candidate.preferredLocation) ? (candidate.location || candidate.preferredLocation) : null;
        const locationSource = candidate && !candidate.location && candidate.preferredLocation
            ? 'From your resume profile · preferred location'
            : 'From your resume profile';
        const summary = app.generatedResumeSummary ? app.generatedResumeSummary : null;
        const summarySource = 'From your prepared application for this role';
        return [
            { key: 'name', label: 'Name', source: 'From your resume profile', value: candidate ? candidate.name || null : null, type: 'text', maxlength: 200,
              absent: 'No name on file — type your own if the employer form needs one.' },
            { key: 'email', label: 'Email', source: 'From your resume profile', value: candidate && candidate.email ? String(candidate.email).trim().toLowerCase() : null, type: 'text', maxlength: 200,
              absent: 'No email on file — the platform never guesses an address; type your own if needed.' },
            { key: 'phone', label: 'Phone', source: 'From your resume profile', value: candidate ? candidate.phone || null : null, type: 'text', maxlength: 200,
              absent: 'No phone number on file — type your own if needed.' },
            { key: 'location', label: 'Location', source: locationSource, value: location, type: 'text', maxlength: 200,
              absent: 'No location on file — type your own if needed.' },
            { key: 'headline', label: 'Headline', source: 'From your resume profile · preferred roles', value: candidate ? candidate.headline || null : null, type: 'text', maxlength: 200,
              absent: 'No headline on file — it is derived from your preferred roles; none are stored.' },
            { key: 'summary', label: 'Professional summary', source: summarySource, value: summary, type: 'textarea', maxlength: 4000,
              absent: 'This package has no professional summary — generate it in Applications, or type your own here.' },
            { key: 'skills', label: 'Skills', source: 'From your skills on your resume profile', value: candidate ? joinSkills(candidate.skills) : null, type: 'textarea', maxlength: 2000,
              absent: 'No skills on file — type your own if the employer form needs them.' }
        ];
    }

    function kitFieldRow(field, value) {
        const v = (value !== undefined ? value : field.value) || '';
        const control = field.type === 'textarea'
            ? `<textarea data-kit-field="${field.key}" data-kit-server="${esc(field.value || '')}" maxlength="${field.maxlength}" rows="3" placeholder="Type the value the employer form needs">${esc(v)}</textarea>`
            : `<input data-kit-field="${field.key}" data-kit-server="${esc(field.value || '')}" maxlength="${field.maxlength}" type="text" value="${esc(v)}" placeholder="Type the value the employer form needs">`;
        const absentNote = v ? '' : `<p class="apply-kit-absent">${esc(field.absent)}</p>`;
        return `
            <div class="apply-kit-field">
                <div class="apply-kit-field-head">
                    <label class="apply-kit-field-label" for="kit-${field.key}">${esc(field.label)}</label>
                    <span class="apply-kit-source">${esc(field.source)}</span>
                </div>
                <div class="apply-kit-field-control">
                    ${control}
                    <button type="button" class="btn-apply-copy" data-copy-kit-field="${field.key}" title="Copies the value shown in this field">Copy</button>
                </div>
                ${absentNote}
            </div>`;
    }

    function copyKitText(text, btn) {
        if (!text) {
            showToast('Nothing to copy yet — type a value first.', 'info');
            return;
        }
        const restore = (btn && btn.getAttribute('data-restore-label')) || 'Copy';
        const done = () => {
            if (btn) {
                btn.textContent = 'Copied ✓';
                setTimeout(() => { btn.textContent = restore; }, 1600);
            }
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(done).catch(() => fallbackCopy(text, done));
        } else {
            fallbackCopy(text, done);
        }
    }

    // ─── Apply Kit final review + handoff (Phase 12.8, Slice 3; 12.9 records the open) ──
    function kitInputValues() {
        const map = {};
        document.querySelectorAll('[data-kit-field]').forEach((el) => {
            map[el.getAttribute('data-kit-field')] = (el.value || '').trim();
        });
        return map;
    }

    function revalidateKitPackage(s) {
        return fetch(`${API_ENDPOINT}/${s.app.id}`)
            .then(response => (response.ok ? response.json() : null))
            .then(fresh => {
                if (!fresh) return { stale: true, reason: 'This application is no longer available.' };
                const changed =
                    String(fresh.applicationStatus || 'DRAFT') !== String(s.app.applicationStatus || 'DRAFT') ||
                    String(fresh.updatedAt || '') !== String(s.app.updatedAt || '') ||
                    String(fresh.generatedResumeSummary || '') !== String(s.app.generatedResumeSummary || '');
                if (changed || !isApproved(fresh.applicationStatus)) {
                    return { stale: true, reason: 'This application package changed after you prepared it — the values below may be out of date.' };
                }
                return { stale: false };
            });
    }

    function prepareKitBodyHtml() {
        const { app, target, fields } = kitSession;
        const current = kitSession.currentValues || {};
        const value = (f) => (f.key in current ? current[f.key] : f.value);
        return `
            <div class="apply-kit" data-apply-state="eligible">
                <div class="prep-summary">
                    <span class="summary-chip">Job ID: ${esc(app.jobId || '—')}</span>
                    <span class="summary-chip">Company: ${esc(app.company || '—')}</span>
                    <span class="summary-chip">Location: ${esc(app.location || 'Not specified')}</span>
                    <span class="summary-chip">Skill coverage: ${app.matchScore != null ? esc(app.matchScore) + '/100' : 'not calculated'}</span>
                </div>
                <div class="apply-kit-destination" title="The employer application URL exactly as the job source supplied it">
                    <span class="external-badge">Assisted Apply — review only</span>
                    <code class="apply-kit-url" data-apply-url="${esc(target.url)}">${esc(target.url)}</code>
                    <span class="external-note">From ${esc(target.source || 'the job source')} · never edited, guessed or rewritten.</span>
                </div>
                ${block('What the Apply Kit prepares', `
                    <p class="modal-description">Review, edit and copy each value. Every field states its source — your resume profile or your prepared application for this role. Links (GitHub/LinkedIn) are not offered: the resume parser stores none, so nothing is invented.</p>
                    <div class="apply-kit-fields">${fields.map((f) => kitFieldRow(f, value(f))).join('')}</div>
                    <div class="apply-kit-toolbar">
                        <button type="button" class="btn-secondary" id="applyKitResetBtn" title="Restores the server-derived values from your profile and application">Reset kit values</button>
                        <button type="button" class="btn-primary" id="applyKitReviewBtn">Review &amp; hand off</button>
                    </div>
                    <p class="apply-kit-note">Values are copied and applied by you on the employer site. This screen never fills a form, writes to the employer page, or submits anything.</p>`)}
                ${block('Where you apply', `
                    <p class="modal-description">Open the employer site, review the prepared values there, and submit yourself. The platform never writes to or submits a page outside this app, and it never applies on your behalf.</p>`)}
            </div>`;
    }

    function prepareKitFooterHtml() {
        const { target } = kitSession;
        return `
            <span class="modal-hint">You submit on the employer site — the platform never does.</span>
            ${kitCloseBtn}
            <a class="btn-primary btn-apply-external" href="${esc(target.url)}" target="_blank" rel="noopener noreferrer"
               title="Opens the employer's own application page in a new tab — you review and submit there yourself">Open employer site to apply</a>`;
    }

    function renderKitPrepareFromSession() {
        window.modalShell.setBody(prepareKitBodyHtml());
        window.modalShell.setFooter(prepareKitFooterHtml());
    }

    function renderKitReview() {
        const { target, fields } = kitSession;
        const current = kitSession.currentValues || {};
        const rows = fields.map((f) => {
            const v = (f.key in current ? current[f.key] : f.value) || '';
            return `<div class="apply-kit-review-row">
                <span class="apply-kit-review-label">${esc(f.label)}</span>
                ${v
                    ? `<code class="apply-kit-review-value">${esc(v)}</code>`
                    : '<span class="apply-kit-review-missing">not provided</span>'}
            </div>`;
        }).join('');
        window.modalShell.setBody(`
            <div class="apply-kit" data-apply-state="eligible" data-kit-mode="review">
                <div class="apply-kit-review">
                    <div class="apply-kit-review-disclaimer">These values will not be written or submitted by this app — you copy them, paste them and submit yourself on the employer site.</div>
                    <div class="apply-kit-review-list">${rows}</div>
                    <div class="apply-kit-review-actions">
                        <button type="button" class="btn-primary" id="applyKitCopyAllBtn" data-restore-label="Copy all fields">Copy all fields</button>
                    </div>
                    <label class="apply-kit-ack">
                        <input type="checkbox" id="applyKitAck">
                        <span>I have reviewed the values above and will copy and submit them myself on the employer site.</span>
                    </label>
                    <p class="apply-kit-note">Assisted Apply only prepares and copies values. It never fills a form, writes to the employer page, submits anything, or sends email.</p>
                </div>
            </div>`);
        window.modalShell.setFooter(`
            <span class="modal-hint">You submit on the employer site — the platform never does.</span>
            <button type="button" class="btn-secondary" id="applyKitBackBtn">Back to editing</button>
            ${kitCloseBtn}
            <button type="button" class="btn-primary btn-handoff" id="applyKitHandoffBtn" disabled title="Enabled after you confirm you have reviewed the values">Open employer site to apply</button>`);
    }

    function renderKitStale(reason) {
        window.modalShell.setBody(`
            <div class="apply-kit" data-apply-state="blocked-stale">
                <div class="apply-kit-state">
                    <div class="apply-kit-state-title">Package changed</div>
                    <p>${esc(reason)} Re-open Assisted Apply to load the latest values.</p>
                </div>
            </div>`);
        window.modalShell.setFooter(`
            ${kitCloseBtn}
            <button type="button" class="btn-primary" id="applyKitReloadBtn">Reload package</button>`);
    }

    function enterKitReview() {
        const s = kitSession;
        if (!s) return;
        s.currentValues = kitInputValues();
        revalidateKitPackage(s)
            .then(res => {
                if (!kitSession) return; // closed while revalidating
                if (res.stale) {
                    renderKitStale(res.reason);
                    return;
                }
                renderKitReview();
            })
            .catch(() => renderKitReview()); // a local-first tool must not trap the user on a network hiccup; the values are already in the dialog
    }

    function resetKitValues() {
        document.querySelectorAll('[data-kit-field]').forEach(el => {
            el.value = el.getAttribute('data-kit-server') || '';
        });
        if (kitSession) kitSession.currentValues = {};
        showToast('Kit values reset to the values from your profile and application.', 'info');
    }

    function copyAllKitValues(btn) {
        const s = kitSession;
        if (!s) return;
        const current = s.currentValues || {};
        const lines = s.fields
            .map(f => ({ label: f.label, value: (f.key in current ? current[f.key] : f.value) || '' }))
            .filter(row => row.value)
            .map(row => `${row.label}: ${row.value}`);
        if (!lines.length) {
            showToast('Nothing to copy yet — type a value first.', 'info');
            return;
        }
        copyKitText(lines.join('\n'), btn);
    }

    function kitHandoff() {
        const s = kitSession;
        if (!s) return;
        const ack = document.getElementById('applyKitAck');
        if (!ack || !ack.checked) {
            showToast('Confirm you have reviewed the values first.', 'info');
            return;
        }
        // Phase 12.9: the server now records this opening (employerOpenedAt + validated
        // URL). Best-effort, fire-and-forget — a failure must never block the user from
        // applying, so the tab opens regardless and we only surface a non-blocking toast.
        const url = (s.target && s.target.url) || null;
        fetch(`${API_ENDPOINT}/${s.app.id}/handoff`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url })
        }).then(response => {
            if (!response.ok) throw new Error(String(response.status));
        }).catch(() => {
            showToast('Could not record this opening on the server.', 'error');
        });
        window.open(s.target.url, '_blank');
    }

    const kitCloseBtn = '<button type="button" class="btn-secondary" data-apply-close>Close</button>';

    document.addEventListener('click', (e) => {
        const copyBtn = e.target.closest('[data-copy-kit-field]');
        if (copyBtn) {
            const key = copyBtn.getAttribute('data-copy-kit-field');
            const input = document.querySelector(`[data-kit-field="${key}"]`);
            if (input) copyKitText((input.value || '').trim(), copyBtn);
            return;
        }
        if (e.target.closest('#applyKitResetBtn')) { resetKitValues(); return; }
        if (e.target.closest('#applyKitReviewBtn')) { enterKitReview(); return; }
        if (e.target.closest('#applyKitBackBtn')) { renderKitPrepareFromSession(); return; }
        if (e.target.closest('#applyKitCopyAllBtn')) { copyAllKitValues(e.target.closest('#applyKitCopyAllBtn')); return; }
        if (e.target.closest('#applyKitHandoffBtn')) { kitHandoff(); return; }
        if (e.target.closest('#applyKitReloadBtn')) {
            const freshId = kitSession && kitSession.app ? kitSession.app.id : null;
            kitSession = null;
            if (freshId) {
                window.modalShell.close();
                openApplyKit(freshId);
            }
        }
    });

    document.addEventListener('change', (e) => {
        if (e.target && e.target.id === 'applyKitAck') {
            const handoff = document.getElementById('applyKitHandoffBtn');
            if (handoff) handoff.disabled = !e.target.checked;
        }
    });

    const block = (title, html) => `
        <div class="modal-job-section">
            <h4 class="modal-section-title">${esc(title)}</h4>
            ${html}
        </div>`;

    /**
     * Assisted Apply — review surface (Phase 12.8, Slice 1).
     *
     * Opens the Apply Kit in the single global modal shell for an approved package.
     * The kit REVIEWS the destination: it resolves the genuine employer application
     * URL from the stored job id (never constructed, guessed or rewritten) and shows
     * the package context, the plan for whitelisted fields (later slice) and the
     * honest handoff to the employer site. Nothing here fills a form, writes to the
     * employer page, submits anything, or sends email.
     *
     * The modal is opened exactly once with its final state after the application
     * and the listing both resolve, so a closed or superseded dialog can never be
     * overwritten by a late callback.
     */
    function openApplyKit(id) {
        fetch(`${API_ENDPOINT}/${id}`)
            .then(response => {
                if (!response.ok) throw new Error(`Failed to fetch application: ${response.status}`);
                return response.json();
            })
            .then(app => {
                if (!app || !app.id) {
                    showToast('Application not found.', 'error');
                    return;
                }
                Promise.all([
                    resolveJob(app.jobId),
                    resolveCandidate(app.candidateId)
                ]).then(([job, candidate]) => renderApplyKit(app, job, candidate));
            })
            .catch(err => {
                console.error('Error opening Apply Kit:', err);
                showToast(window.apiError.describe(err, 'Failed to load application.'), 'error');
            });
    }

    function renderApplyKit(app, job, candidate) {
        const statusKey = app.applicationStatus || 'DRAFT';
        const status = STATUS_LABELS[statusKey]
            || { label: humanizeStatus(statusKey), cls: 'draft', hint: '' };
        const approved = isApproved(statusKey);
        // jobLink.resolve() is the single source of truth for the destination — safeUrl
        // has already rejected loopback/private/fake hosts, so only a genuine source
        // URL can ever be shown here. A missing listing resolves to kind 'none'.
        const target = window.jobLink
            ? window.jobLink.resolve(job)
            : { kind: 'none', url: null, label: 'Application Link Unavailable', reason: 'Link resolution is unavailable.', source: null };

        const preparedOn = app.createdAt
            ? new Date(app.createdAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
            : 'not recorded';

        const fallbackNote = 'The manual link above remains available while this screen is blocked.';

        let state, body, footer, subtitle;

        if (!approved) {
            // Defensive: the entry button is hidden for non-approved packages, but a
            // stale detail view could still reach this if the status changed server-side.
            state = 'blocked-status';
            subtitle = `${status.label} · prepared ${preparedOn} · not submitted`;
            body = `
                <div class="apply-kit" data-apply-state="${state}">
                    <div class="apply-kit-state">
                        <div class="apply-kit-state-title">Not approved for application</div>
                        <p>The Apply Kit needs a package you have approved first. This application is currently <strong>${esc(status.label)}</strong> — approve it in Applications, then reopen Assisted Apply.</p>
                    </div>
                    <p class="agent-note">${fallbackNote}</p>
                </div>`;
            footer = kitCloseBtn;
        } else if (!job || !job.id) {
            state = 'blocked-resolve';
            subtitle = `${status.label} · prepared ${preparedOn} · not submitted`;
            body = `
                <div class="apply-kit" data-apply-state="${state}">
                    <div class="apply-kit-state">
                        <div class="apply-kit-state-title">Employer destination not found</div>
                        <p>The original listing is no longer indexed, so its employer application URL cannot be shown. The URL is never guessed or reconstructed.</p>
                    </div>
                    <p class="agent-note">${fallbackNote}</p>
                </div>`;
            footer = kitCloseBtn;
        } else if (target.kind !== 'employer') {
            state = 'blocked-resolve';
            subtitle = `${status.label} · prepared ${preparedOn} · not submitted`;
            const reason = target.reason
                || 'The source supplied no employer application link, so Assisted Apply cannot be used.';
            body = `
                <div class="apply-kit" data-apply-state="${state}">
                    <div class="apply-kit-state">
                        <div class="apply-kit-state-title">Employer application URL unavailable</div>
                        <p>${esc(reason)}</p>
                        <p>Assisted Apply needs a genuine employer application URL from the job source. This job has nothing to transfer to, so only the manual path applies.</p>
                    </div>
                    <p class="agent-note">${fallbackNote}</p>
                </div>`;
            footer = kitCloseBtn;
        } else {
            state = 'eligible';
            subtitle = `${status.label} · prepared ${preparedOn} · not submitted`;
            kitSession = { app, job, target, fields: buildKitFields(app, candidate), currentValues: {} };
            body = prepareKitBodyHtml();
            footer = prepareKitFooterHtml();
        }

        window.modalShell.open({
            kicker: 'Assisted Apply',
            title: `${app.jobTitle || 'Role'} — ${app.company || 'Company'}`,
            subtitle: subtitle,
            body: body,
            footer: footer
        });
    }

    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-edit-application]');
        if (!btn) return;
        const id = Number(btn.getAttribute('data-edit-application'));
        if (!id) return;
        window.modalShell.close();
        viewApplication(id);
    });

    /** Resolves a stored job id to its listing, caching both hits and misses. */
    function resolveJob(jobId) {
        const key = String(jobId);
        if (jobLookupCache.has(key)) return Promise.resolve(jobLookupCache.get(key));
        return fetch(JOB_ENDPOINT + encodeURIComponent(key))
            .then(response => response.ok ? response.json() : null)
            .catch(() => null)
            .then(job => {
                jobLookupCache.set(key, job || null);
                return job || null;
            });
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

    /** Renders a stored enum value as readable words without inventing meaning. */
    function humanizeStatus(value) {
        if (!value) return 'Unknown';
        const words = String(value).toLowerCase().split('_').filter(Boolean);
        if (!words.length) return 'Unknown';
        return words.map((w, i) => i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w).join(' ');
    }

    // ─── View application details ─────────────────────────────────────────────
    function viewApplication(id, openInEdit) {
        currentApplicationId = id;
        fetch(`${API_ENDPOINT}/${id}`)
            .then(response => {
                if (!response.ok) throw new Error(`Failed to fetch application: ${response.status}`);
                return response.json();
            })
            .then(data => {
                if (!data || !data.id) {
                    showToast('Application not found.', 'error');
                    return;
                }

                const app = data;
                currentApplicationId = app.id;

                applicationDetailTitle.textContent = `Application: ${app.jobTitle} at ${app.company}`;
                const detailStatus = STATUS_LABELS[app.applicationStatus]
                    || { label: humanizeStatus(app.applicationStatus || 'DRAFT'), cls: 'draft', hint: '' };
                applicationDetailStatus.textContent = detailStatus.label;
                applicationDetailStatus.setAttribute('title', detailStatus.hint || '');
                renderDetailExternal(app);
                applicationDetailJobId.textContent = `Job ID: ${app.jobId}`;
                applicationDetailCompany.textContent = `Company: ${app.company}`;
                applicationDetailLocation.textContent = `Location: ${app.location || 'Not Specified'}`;
                const scoreEl = document.getElementById('applicationDetailMatchScore');
                scoreEl.textContent = `Skill coverage: ${app.matchScore != null ? app.matchScore + '/100' : 'not calculated'}`;
                scoreEl.setAttribute('title', 'Deterministic required-skill coverage at preparation time. The weighted match score is shown on the Matches page.');

                // Fetch and display Application Advisor results
                loadApplicationAdvisor(app.jobId);

                // Store original data for edit mode
                originalData = {
                    coverLetter: app.coverLetter || '',
                    professionalSummary: app.generatedResumeSummary || app.tailoredProfessionalSummary || '',
                    applicationAnswers: app.applicationAnswers || app.suggestedAnswers || ''
                };

                applicationDetailSummary.textContent = app.generatedResumeSummary || app.tailoredProfessionalSummary || '—';
                applicationDetailCoverLetter.textContent = app.coverLetter || 'No cover letter generated';

                // Suggested answers
                const suggestedAnswers = splitList(app.applicationAnswers || app.suggestedAnswers);
                applicationDetailAnswers.innerHTML = '';
                if (suggestedAnswers.length) {
                    suggestedAnswers.forEach((answer, index) => {
                        const answerItem = document.createElement('div');
                        answerItem.className = 'application-answer-item';
                        answerItem.innerHTML = `
                            <strong>Q${index + 1}:</strong> ${esc(answer)}
                        `;
                        applicationDetailAnswers.appendChild(answerItem);
                    });
                } else {
                    applicationDetailAnswers.innerHTML = '<span class="muted">No suggested answers available.</span>';
                }

                // Candidate strengths
                const strengths = splitList(app.candidateStrengths);
                applicationDetailStrengths.innerHTML = '';
                if (strengths.length) {
                    strengths.forEach(strength => {
                        const li = document.createElement('li');
                        li.textContent = esc(strength);
                        applicationDetailStrengths.appendChild(li);
                    });
                } else {
                    const li = document.createElement('li');
                    li.textContent = 'No candidate strengths listed.';
                    applicationDetailStrengths.appendChild(li);
                }

                // Skill gaps
                const gaps = splitList(app.missingSkills);
                applicationDetailGaps.innerHTML = '';
                if (gaps.length) {
                    gaps.forEach(gap => {
                        const li = document.createElement('li');
                        li.textContent = esc(gap);
                        applicationDetailGaps.appendChild(li);
                    });
                } else {
                    const li = document.createElement('li');
                    li.textContent = 'No missing skills to address.';
                    applicationDetailGaps.appendChild(li);
                }

                // Recommendation — recorded by the advisor when this package was prepared.
                const recommendation = app.recommendation;
                applicationDetailRecommendation.textContent = recommendation
                    ? humanizeStatus(recommendation)
                    : 'No advisor result recorded for this application.';

                // Update action buttons based on status
                updateActionButtons(app.applicationStatus);

                // Show detail section, hide onboarding/list
                applicationDetailSection.hidden = false;
                applicationsOnboarding.hidden = true;
                applicationsListSection.hidden = true;

                // Prepare-modal edit deep link: applications.html?application=<id>&edit=1
                // Enter the Applications-page edit surface for that application (only when editing is allowed).
                if (openInEdit && !isEditing && (isGenerated(app.applicationStatus) || isApproved(app.applicationStatus))) {
                    enterEditMode();
                }
            })
            .catch(err => {
                console.error('Error viewing application:', err);
                showToast(window.apiError.describe(err, 'Failed to load application details.'), 'error');
            });
    }

    // ─── Status helpers ────────────────────────────────────────────────────────
    function isGenerated(status) {
        return status === 'GENERATED' || status === 'DRAFT' || status === 'UNDER_REVIEW';
    }

    function isApproved(status) {
        return status === 'APPROVED_FOR_APPLICATION';
    }

    // ─── Update action buttons based on application status ────────────────────
    function updateActionButtons(status) {
        // Edit button: allow editing before sending (GENERATED, APPROVED)
        if (editApplicationBtn) {
            editApplicationBtn.hidden = !isGenerated(status) && !isApproved(status);
        }

        // Approve button: only show for GENERATED/DRAFT/UNDER_REVIEW
        if (approveApplicationBtn) {
            approveApplicationBtn.hidden = !isGenerated(status);
            approveApplicationBtn.textContent = 'Approve Application';
        }

        // Assisted Apply button: only for a package the user has approved. The kit is
        // a review + assisted-transfer surface, so it is gated on the same approval
        // the manual apply path uses.
        if (assistedApplyBtn) {
            assistedApplyBtn.hidden = !isApproved(status);
        }

        // Send Email button: only show for APPROVED_FOR_APPLICATION
        if (sendEmailBtn) {
            sendEmailBtn.hidden = !isApproved(status);
        }
    }

    // ─── Edit mode ───────────────────────────────────────────────────────────
    function enterEditMode() {
        if (!currentApplicationId) {
            showToast('No application selected.', 'error');
            return;
        }

        isEditing = true;
        setEditSaving(false);
        clearFieldError(editProfessionalSummary);
        clearFieldError(editCoverLetter);

        // Hide view mode elements
        document.getElementById('applicationDetailSummary').hidden = true;
        document.getElementById('applicationDetailCoverLetter').hidden = true;
        document.getElementById('applicationDetailAnswers').hidden = true;

        // Show edit mode section
        if (editModeSection) editModeSection.hidden = false;

        // Populate edit fields
        if (editCoverLetter) editCoverLetter.value = originalData.coverLetter || '';
        if (editProfessionalSummary) editProfessionalSummary.value = originalData.professionalSummary || '';
        if (editApplicationAnswers) editApplicationAnswers.value = originalData.applicationAnswers || '';

        // Update button states
        if (editApplicationBtn) editApplicationBtn.hidden = true;
        if (approveApplicationBtn) approveApplicationBtn.hidden = true;
        if (assistedApplyBtn) assistedApplyBtn.hidden = true;
        if (sendEmailBtn) sendEmailBtn.hidden = true;
        if (saveEditBtn) saveEditBtn.hidden = false;
        if (cancelEditBtn) cancelEditBtn.hidden = false;
    }

    function exitEditMode() {
        isEditing = false;
        setEditSaving(false);

        // Show view mode elements
        document.getElementById('applicationDetailSummary').hidden = false;
        document.getElementById('applicationDetailCoverLetter').hidden = false;
        document.getElementById('applicationDetailAnswers').hidden = false;

        // Hide edit mode section
        if (editModeSection) editModeSection.hidden = true;

        // Hide the edit-mode-only buttons; the status-based buttons are restored below.
        if (saveEditBtn) saveEditBtn.hidden = true;
        if (cancelEditBtn) cancelEditBtn.hidden = true;

        // Restore button states based on current application status
        fetch(`${API_ENDPOINT}/${currentApplicationId}`)
            .then(response => response.json())
            .then(app => {
                updateActionButtons(app.applicationStatus);
            })
            .catch(() => {
                // Fallback
                if (editApplicationBtn) editApplicationBtn.hidden = false;
                if (approveApplicationBtn) approveApplicationBtn.hidden = false;
                if (sendEmailBtn) sendEmailBtn.hidden = true;
            });
    }

    function saveEdit() {
        if (!currentApplicationId) return;
        if (saveEditSaving) return;

        // Validate before persisting — actionable per-field messages, no silent overwrite.
        if (!validateEditForm()) return;

        const updates = {
            coverLetter: editCoverLetter ? editCoverLetter.value : originalData.coverLetter,
            professionalSummary: editProfessionalSummary ? editProfessionalSummary.value : originalData.professionalSummary,
            applicationAnswers: editApplicationAnswers ? editApplicationAnswers.value : originalData.applicationAnswers
        };

        setEditSaving(true);

        fetch(`${API_ENDPOINT}/${currentApplicationId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(updates)
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.detail || `HTTP ${response.status}`); });
            }
            return response.json();
        })
        .then(data => {
            showToast('Application updated successfully.', 'success');
            // Update original data
            originalData = {
                coverLetter: updates.coverLetter,
                professionalSummary: updates.professionalSummary,
                applicationAnswers: updates.applicationAnswers
            };
            // Leave edit mode and reload from the server so the saved content is confirmed.
            exitEditMode();
            viewApplication(currentApplicationId);
        })
        .catch(err => {
            console.error('Error saving application:', err);
            showToast(window.apiError.describe(err, 'Failed to save changes.'), 'error');
            setEditSaving(false);
        });
    }

    // ─── Edit form validation ─────────────────────────────────────────────────
    function validateEditForm() {
        let valid = true;
        const summary = editProfessionalSummary ? editProfessionalSummary.value.trim() : '';
        const coverLetter = editCoverLetter ? editCoverLetter.value.trim() : '';

        clearFieldError(editProfessionalSummary);
        clearFieldError(editCoverLetter);

        if (!summary) {
            showFieldError(editProfessionalSummary, 'Professional summary is required.');
            valid = false;
        }
        if (!coverLetter) {
            showFieldError(editCoverLetter, 'Cover letter is required.');
            valid = false;
        }
        if (!valid) {
            showToast('Please correct the highlighted fields.', 'error');
        }
        return valid;
    }

    function showFieldError(input, message) {
        if (!input) return;
        setFieldError(input, message);
    }

    function clearFieldError(input) {
        if (!input) return;
        setFieldError(input, '');
    }

    function setFieldError(input, message) {
        const wrapper = input.closest('.edit-form-group');
        let errorEl = wrapper ? wrapper.querySelector('.edit-field-error') : null;
        if (!message) {
            if (errorEl) errorEl.remove();
            input.classList.remove('is-invalid');
            input.removeAttribute('aria-invalid');
            return;
        }
        if (!errorEl) {
            errorEl = document.createElement('p');
            errorEl.className = 'edit-field-error';
            if (wrapper) wrapper.appendChild(errorEl);
        }
        errorEl.textContent = message;
        input.classList.add('is-invalid');
        input.setAttribute('aria-invalid', 'true');
    }

    let saveEditSaving = false;
    function setEditSaving(saving) {
        saveEditSaving = saving;
        if (saveEditBtn) saveEditBtn.disabled = saving;
        if (cancelEditBtn) cancelEditBtn.disabled = saving;
        if (saveEditBtn) saveEditBtn.textContent = saving ? 'Saving...' : 'Save Changes';
    }

    // ─── Send Email ───────────────────────────────────────────────────────────
    async function sendEmail() {
        if (!currentApplicationId) {
            showToast('No application selected.', 'error');
            return;
        }

        // Recipient is user-entered and user-verified before any send; the dialog
        // blocks blank/invalid addresses inline and never substitutes a guessed one.
        const recipient = await window.confirmDialog.ask({
            title: 'Send application email',
            message: 'Send this application email now?',
            detail: 'The prepared application will be sent to the recipient you confirm below.',
            warning: 'The email goes out only after you confirm the address. This cannot be undone.',
            confirmLabel: 'Send email',
            cancelLabel: 'Cancel',
            input: {
                label: 'Recipient email',
                value: localStorage.getItem(LS_CANDIDATE_EMAIL) || '',
                placeholder: 'hiring@company.com',
                required: true,
                requiredMessage: 'Recipient email is required.',
                validate: value => EMAIL_RE.test(value)
                    ? null
                    : 'Enter a valid email address (e.g. name@example.com).'
            }
        });
        if (recipient === false) {
            return;
        }

        const sendBtn = sendEmailBtn;
        if (sendBtn) {
            sendBtn.disabled = true;
            sendBtn.textContent = 'Sending...';
        }

        fetch(EMAIL_ENDPOINT, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                applicationId: currentApplicationId,
                approved: true,
                recipientEmail: recipient
            })
        })
        .then(async response => {
            // email/send returns ApplicationSendResult JSON on non-2xx too — surface
            // its message verbatim instead of a bare "HTTP 400".
            const body = await response.json().catch(() => null);
            if (!response.ok) {
                const reason = body && body.message
                    ? `${body.status || 'Email send'} — ${body.message}`
                    : ((body && body.detail) || `HTTP ${response.status}`);
                throw new Error(reason);
            }
            return body;
        })
        .then(data => {
            if (data && data.status === 'SENT') {
                // Simulated sends are labelled as such — never disguised as real mail.
                if (data.simulated) {
                    showToast('Email send simulated — no SMTP configured. Nothing was actually mailed.', 'info');
                } else {
                    showToast('Application email sent successfully!', 'success');
                }
            } else if (data && data.status === 'FAILED') {
                showToast('Email could not be sent: ' + (data.message || 'Unknown error'), 'error');
            } else if (data && data.status === 'REJECTED') {
                showToast('Email send rejected: ' + (data.message || 'Not approved for sending.'), 'error');
            } else {
                showToast('Email send completed: ' + (data.message || data.status), 'info');
            }
            // Refresh the application to get updated status
            viewApplication(currentApplicationId);
        })
        .catch(err => {
            console.error('Error sending email:', err);
            showToast(window.apiError.describe(err, 'Failed to send email.'), 'error');
        })
        .finally(() => {
            if (sendBtn) {
                sendBtn.disabled = false;
                sendBtn.textContent = 'Send Email';
            }
        });
    }

    // ─── Approve application ──────────────────────────────────────────────────
    async function approveApplication(id) {
        // Styled confirmation via the shared modal shell — never a native browser dialog.
        const proceed = await window.confirmDialog.ask({
            title: 'Approve for manual submission',
            message: 'Approve this application for manual submission?',
            detail: 'The application will be marked as approved and ready for you to submit yourself.',
            warning: 'This platform never submits an application to a job website on your behalf.',
            confirmLabel: 'Approve',
            cancelLabel: 'Cancel'
        });
        if (!proceed) {
            return;
        }

        fetch(`${API_ENDPOINT}/${id}/approve`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.detail || `HTTP ${response.status}`); });
            }
            return response.json();
        })
        .then(data => {
            showToast('Application approved successfully.', 'success');
            // Refresh the list and return to list view
            currentApplicationId = null;
            applicationDetailSection.hidden = true;
            applicationsListSection.hidden = false;
            loadApplications();
        })
        .catch(err => {
            console.error('Error approving application:', err);
            showToast(window.apiError.describe(err, 'Failed to approve application.'), 'error');
        });
    }

    // ─── Load applications for current candidate ──────────────────────────────
    // Phase 12.9: an optional status filter (select#applicationsStatusFilter) is
    // sent as ?status=<ENUM_NAME>; absent/blank returns all applications. The select
    // only ever offers known statuses, so a 400 here is defensive (stale value) —
    // reset to All and reload instead of surfacing an unusable error.
    class InvalidStatusFilterError extends Error {}

    function loadApplications() {
        if (candidateId == null) {
            // Show onboarding state
            applicationsOnboarding.hidden = false;
            applicationsListSection.hidden = true;
            applicationDetailSection.hidden = true;
            applicationsMain.hidden = false;
            return;
        }

        if (applicationsStatusFilterEl) applicationsStatusFilterEl.disabled = true;

        const query = applicationsStatusFilter ? `?status=${encodeURIComponent(applicationsStatusFilter)}` : '';
        fetch(`${API_ENDPOINT}/candidate/${candidateId}${query}`)
            .then(response => {
                if (response.status === 400) {
                    throw new InvalidStatusFilterError();
                }
                if (response.status === 404) {
                    // No applications yet
                    return [];
                }
                if (!response.ok) {
                    throw new Error(`Failed to fetch applications: ${response.status}`);
                }
                return response.json();
            })
            .then(data => renderApplicationList(data || []))
            .catch(err => {
                if (err instanceof InvalidStatusFilterError) {
                    applicationsStatusFilter = '';
                    if (applicationsStatusFilterEl) applicationsStatusFilterEl.value = '';
                    showToast('Unknown status filter — showing all applications.', 'info');
                    return loadApplications();
                }
                if (applicationsStatusFilterEl) applicationsStatusFilterEl.disabled = false;
                console.error('Error loading applications:', err);
                showToast(window.apiError.describe(err, 'Failed to load applications.'), 'error');
                renderApplicationList(null);
            });
    }

    // Paints list, empty and failure states. null data = request failure (keeps the
    // toolbar, shows a Retryable empty state). The data-present branch does NOT touch
    // applicationDetailSection, preserving the matches-page deep link (?application=...):
    // that surface is owned by viewApplication / back / approve.
    function renderApplicationList(data) {
        if (applicationsStatusFilterEl) applicationsStatusFilterEl.disabled = false;

        if (data === null) {
            applicationsOnboarding.hidden = true;
            applicationsListSection.hidden = false;
            applicationDetailSection.hidden = true;
            applicationsEmptyMessage.textContent = 'Could not load applications.';
            applicationsShowAllBtn.hidden = true;
            applicationsRetryBtn.hidden = false;
            applicationsEmpty.hidden = false;
            applicationsCardsGrid.innerHTML = '';
            applicationsCount.textContent = '0';
            return;
        }

        if (data.length === 0) {
            applicationsOnboarding.hidden = true;
            applicationsListSection.hidden = false;
            applicationDetailSection.hidden = true;
            applicationsCardsGrid.innerHTML = '';
            applicationsCount.textContent = '0';
            if (applicationsStatusFilter) {
                applicationsEmptyMessage.textContent = 'No applications with this status.';
                applicationsShowAllBtn.hidden = false;
                applicationsRetryBtn.hidden = true;
            } else {
                applicationsEmptyMessage.textContent = 'No applications prepared yet.';
                applicationsShowAllBtn.hidden = true;
                applicationsRetryBtn.hidden = true;
            }
            applicationsEmpty.hidden = false;
            return;
        }

        applicationsOnboarding.hidden = true;
        applicationsListSection.hidden = false;
        applicationsEmpty.hidden = true;

        // Render cards
        applicationsCardsGrid.innerHTML = '';
        data.forEach(app => {
            const card = renderApplicationCard(app);
            applicationsCardsGrid.appendChild(card);
        });

        applicationsCount.textContent = data.length;
    }

    // Populates the status filter options from STATUS_LABELS (so every known status —
    // including EMAIL_SENT — is offered with its badge colour) and wires the change
    // handler plus the show-all/retry empty-state actions.
    function initStatusFilter() {
        if (!applicationsStatusFilterEl) return;
        Object.entries(STATUS_LABELS).forEach(([statusKey, s]) => {
            const opt = document.createElement('option');
            opt.value = statusKey;
            opt.textContent = s.label;
            opt.title = s.hint;
            opt.className = `filter-option filter-option-${s.cls}`;
            applicationsStatusFilterEl.appendChild(opt);
        });
        applicationsStatusFilterEl.addEventListener('change', () => {
            applicationsStatusFilter = applicationsStatusFilterEl.value;
            applicationDetailSection.hidden = true;
            loadApplications();
        });
        if (applicationsShowAllBtn) {
            applicationsShowAllBtn.addEventListener('click', () => {
                applicationsStatusFilter = '';
                applicationsStatusFilterEl.value = '';
                loadApplications();
            });
        }
        if (applicationsRetryBtn) {
            applicationsRetryBtn.addEventListener('click', loadApplications);
        }
    }

    // ─── Initialize ────────────────────────────────────────────────────────────
    function init() {
        // Get candidate info from localStorage
        candidateId = localStorage.getItem(LS_CANDIDATE_ID) ? Number(localStorage.getItem(LS_CANDIDATE_ID)) : null;
        candidateName = localStorage.getItem(LS_CANDIDATE_NAME);

        updateProfileBadge();
        initStatusFilter();
        loadApplications();

        // Deep link from the Matches page: applications.html?application=<id>[&edit=1]
        // auto-opens the detail and (when edit=1) drops straight into the edit surface.
        const params = new URLSearchParams(window.location.search);
        const deepLinkId = Number(params.get('application'));
        if (Number.isFinite(deepLinkId) && deepLinkId > 0 && candidateId != null) {
            const openInEdit = params.get('edit') === '1';
            viewApplication(deepLinkId, openInEdit);
        }

        // Copy buttons (delegated)
        document.addEventListener('click', (e) => {
            const copyBtn = e.target.closest('.copy-btn');
            if (copyBtn) {
                copyElementText(copyBtn.dataset.copyTarget, copyBtn);
            }
        });

        // Apply Kit close button (delegated — the kit lives in the shared modal shell)
        document.addEventListener('click', (e) => {
            if (e.target.closest('[data-apply-close]')) {
                window.modalShell.close();
            }
        });

        // Edit application button
        if (editApplicationBtn) {
            editApplicationBtn.addEventListener('click', enterEditMode);
        }

        // Save edit button
        if (saveEditBtn) {
            saveEditBtn.addEventListener('click', saveEdit);
        }

        // Cancel edit button
        if (cancelEditBtn) {
            cancelEditBtn.addEventListener('click', exitEditMode);
        }

        // Approve application button (detail view). The list cards get their own
        // handler in renderApplicationCard; without this binding the detail-page
        // button renders for every generated application but clicks do nothing.
        if (approveApplicationBtn) {
            approveApplicationBtn.addEventListener('click', () => {
                if (currentApplicationId) approveApplication(currentApplicationId);
            });
        }

        // Assisted Apply button (detail view, shown for approved packages only).
        if (assistedApplyBtn) {
            assistedApplyBtn.addEventListener('click', () => {
                if (currentApplicationId) openApplyKit(currentApplicationId);
            });
        }

        // Send email button
        if (sendEmailBtn) {
            sendEmailBtn.addEventListener('click', sendEmail);
        }

        // Go to resume button
        if (goToResumeBtn) {
            goToResumeBtn.addEventListener('click', () => {
                window.location.href = 'resume.html';
            });
        }

        // Back to applications button
        if (backToApplicationsBtn) {
            backToApplicationsBtn.addEventListener('click', () => {
                currentApplicationId = null;
                applicationDetailSection.hidden = true;
                applicationsListSection.hidden = false;
            });
        }
    }

    // ─── Load Application Advisor results ──────────────────────────────────────
    function loadApplicationAdvisor(jobId) {
        if (!candidateId || !jobId) return;

        fetch('/api/v1/jobs/advisor', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ candidateId, jobId })
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.detail || `HTTP ${response.status}`); });
            }
            return response.json();
        })
        .then(data => {
            renderApplicationAdvisor(data);
        })
        .catch(err => {
            console.error('Error loading application advisor:', err);
        });
    }

    function renderApplicationAdvisor(data) {
        const adv = data || {};
        
        // Update recommendation badge
        const recEl = document.getElementById('applicationDetailRecommendation');
        if (recEl) recEl.textContent = adv.recommendation || '—';

        // Add readiness score next to match score
        const matchScoreEl = document.getElementById('applicationDetailMatchScore');
        if (matchScoreEl && adv.applicationReadinessScore != null) {
            matchScoreEl.textContent = `Match Score: ${app.matchScore != null ? app.matchScore + '/100' : '—'} | Readiness: ${adv.applicationReadinessScore}/100 | Job Match: ${adv.jobMatchScore || '—'}/100`;
        }

        // Strengths
        const strengthsUl = document.getElementById('applicationDetailStrengths');
        if (strengthsUl && adv.strengths && adv.strengths.length) {
            strengthsUl.innerHTML = adv.strengths.map(s => `<li>${esc(s)}</li>`).join('');
        }

        // Concerns
        const gapsUl = document.getElementById('applicationDetailGaps');
        if (gapsUl && adv.concerns && adv.concerns.length) {
            gapsUl.innerHTML = adv.concerns.map(c => `<li>${esc(c)}</li>`).join('');
        }

        // Recommended actions
        const recActionsContainer = document.createElement('div');
        recActionsContainer.className = 'application-detail-section';
        recActionsContainer.innerHTML = `
            <h3>Recommended Actions</h3>
            <ul class="application-detail-actions-list">
                ${(adv.recommendedActions && adv.recommendedActions.length)
                    ? adv.recommendedActions.map(a => `<li>${esc(a)}</li>`).join('')
                    : '<li>No recommended actions.</li>'}
            </ul>
            ${adv.recommendedActionDetails && adv.recommendedActionDetails.length ? `
                <h4>Action Details</h4>
                <ul class="advisor-action-details-list">
                    ${adv.recommendedActionDetails.map(d => `<li><strong>${esc(stepTitle(d))}</strong>${d.description ? `: ${esc(d.description)}` : ''}</li>`).join('')}
                </ul>
            ` : ''}
        `;

        // Insert after recommendation section
        const recSection = document.getElementById('applicationDetailRecommendation');
        if (recSection && recSection.parentElement) {
            recSection.parentElement.insertAdjacentElement('afterend', recActionsContainer);
        }
    }

    // ─── Boot ──────────────────────────────────────────────────────────────────
    init();
})();