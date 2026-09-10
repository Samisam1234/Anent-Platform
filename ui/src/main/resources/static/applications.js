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
        ARCHIVED: { label: 'Archived', cls: 'archived', hint: 'Archived.' }
    };

    // Cache of resolved job listings so a list of applications for the same role does
    // not refetch, and a listing that is no longer indexed is only probed once.
    const jobLookupCache = new Map();

    // Shared with matches.js
    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';

    let candidateId = null;
    let candidateName = null;
    let currentApplicationId = null;
    let isEditing = false;
    let originalData = {}; // Store original data for cancel

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
                showToast(err.message || 'Failed to load application.', 'error');
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
    function viewApplication(id) {
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
            })
            .catch(err => {
                console.error('Error viewing application:', err);
                showToast(err.message || 'Failed to load application details.', 'error');
            });
    }

    // ─── Update action buttons based on application status ────────────────────
    function updateActionButtons(status) {
        const isGenerated = status === 'GENERATED' || status === 'DRAFT' || status === 'UNDER_REVIEW';
        const isApproved = status === 'APPROVED_FOR_APPLICATION';
        const isRejected = status === 'REJECTED';
        const isSent = status === 'SENT'; // For future use

        // Edit button: allow editing before sending (GENERATED, APPROVED)
        if (editApplicationBtn) {
            editApplicationBtn.hidden = !isGenerated && !isApproved;
        }

        // Approve button: only show for GENERATED/DRAFT/UNDER_REVIEW
        if (approveApplicationBtn) {
            approveApplicationBtn.hidden = !isGenerated;
            approveApplicationBtn.textContent = 'Approve Application';
        }

        // Send Email button: only show for APPROVED_FOR_APPLICATION
        if (sendEmailBtn) {
            sendEmailBtn.hidden = !isApproved;
        }
    }

    // ─── Edit mode ───────────────────────────────────────────────────────────
    function enterEditMode() {
        if (!currentApplicationId) {
            showToast('No application selected.', 'error');
            return;
        }

        isEditing = true;

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
        if (sendEmailBtn) sendEmailBtn.hidden = true;
        if (saveEditBtn) saveEditBtn.hidden = false;
        if (cancelEditBtn) cancelEditBtn.hidden = false;
    }

    function exitEditMode() {
        isEditing = false;

        // Show view mode elements
        document.getElementById('applicationDetailSummary').hidden = false;
        document.getElementById('applicationDetailCoverLetter').hidden = false;
        document.getElementById('applicationDetailAnswers').hidden = false;

        // Hide edit mode section
        if (editModeSection) editModeSection.hidden = true;

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

        const updates = {
            coverLetter: editCoverLetter ? editCoverLetter.value : originalData.coverLetter,
            professionalSummary: editProfessionalSummary ? editProfessionalSummary.value : originalData.professionalSummary,
            applicationAnswers: editApplicationAnswers ? editApplicationAnswers.value : originalData.applicationAnswers
        };

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
            // Refresh view
            viewApplication(currentApplicationId);
        })
        .catch(err => {
            console.error('Error saving application:', err);
            showToast(err.message || 'Failed to save changes.', 'error');
        });
    }

    // ─── Send Email ───────────────────────────────────────────────────────────
    function sendEmail() {
        if (!currentApplicationId) {
            showToast('No application selected.', 'error');
            return;
        }

        // Confirm before sending
        if (!confirm('Send this application email now?\n\nThis will attempt to send the application to the recipient. This action cannot be undone.')) {
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
                approved: true
            })
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.detail || `HTTP ${response.status}`); });
            }
            return response.json();
        })
        .then(data => {
            if (data && data.status === 'SENT') {
                showToast('Application email sent successfully!', 'success');
            } else if (data && data.status === 'FAILED') {
                showToast('Email could not be sent: ' + (data.message || 'Unknown error'), 'error');
            } else {
                showToast('Email send completed: ' + (data.message || data.status), 'info');
            }
            // Refresh the application to get updated status
            viewApplication(currentApplicationId);
        })
        .catch(err => {
            console.error('Error sending email:', err);
            showToast(err.message || 'Failed to send email.', 'error');
        })
        .finally(() => {
            if (sendBtn) {
                sendBtn.disabled = false;
                sendBtn.textContent = 'Send Email';
            }
        });
    }

    // ─── Approve application ──────────────────────────────────────────────────
    function approveApplication(id) {
        if (!confirm('Are you sure you want to approve this application for manual submission?\n\nThis will mark the application as APPROVED_FOR_APPLICATION. The application will not be automatically submitted to any job website.')) {
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
            showToast(err.message || 'Failed to approve application.', 'error');
        });
    }

    // ─── Load applications for current candidate ──────────────────────────────
    function loadApplications() {
        if (candidateId == null) {
            // Show onboarding state
            applicationsOnboarding.hidden = false;
            applicationsListSection.hidden = true;
            applicationDetailSection.hidden = true;
            applicationsMain.hidden = false;
            return;
        }

        fetch(`${API_ENDPOINT}/candidate/${candidateId}`)
            .then(response => {
                if (!response.ok) {
                    if (response.status === 404) {
                        // No applications yet
                        applicationsOnboarding.hidden = false;
                        applicationsListSection.hidden = true;
                        applicationDetailSection.hidden = true;
                        applicationsEmpty.hidden = false;
                        applicationsCount.textContent = '0';
                        return;
                    }
                    throw new Error(`Failed to fetch applications: ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                if (!data || data.length === 0) {
                    applicationsOnboarding.hidden = false;
                    applicationsListSection.hidden = true;
                    applicationDetailSection.hidden = true;
                    applicationsEmpty.hidden = false;
                    applicationsCount.textContent = '0';
                    return;
                }

                // Hide onboarding, show list
                applicationsOnboarding.hidden = true;
                applicationsListSection.hidden = false;
                applicationDetailSection.hidden = true;
                applicationsEmpty.hidden = true;

                // Render cards
                applicationsCardsGrid.innerHTML = '';
                data.forEach(app => {
                    const card = renderApplicationCard(app);
                    applicationsCardsGrid.appendChild(card);
                });

                applicationsCount.textContent = data.length;
            })
            .catch(err => {
                console.error('Error loading applications:', err);
                showToast(err.message || 'Failed to load applications.', 'error');
                applicationsOnboarding.hidden = false;
                applicationsListSection.hidden = true;
                applicationDetailSection.hidden = true;
                applicationsEmpty.hidden = false;
                applicationsCount.textContent = '0';
            });
    }

    // ─── Initialize ────────────────────────────────────────────────────────────
    function init() {
        // Get candidate info from localStorage
        candidateId = localStorage.getItem(LS_CANDIDATE_ID) ? Number(localStorage.getItem(LS_CANDIDATE_ID)) : null;
        candidateName = localStorage.getItem(LS_CANDIDATE_NAME);

        updateProfileBadge();
        loadApplications();

        // Copy buttons (delegated)
        document.addEventListener('click', (e) => {
            const copyBtn = e.target.closest('.copy-btn');
            if (copyBtn) {
                copyElementText(copyBtn.dataset.copyTarget, copyBtn);
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