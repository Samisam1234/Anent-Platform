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
    function renderApplicationCard(application) {
        const card = document.createElement('div');
        card.className = 'application-card';
        card.dataset.id = application.id;

        const statusCls = application.applicationStatus || 'DRAFT';
        const statusLabels = {
            DRAFT: 'draft',
            GENERATED: 'generated',
            UNDER_REVIEW: 'under-review',
            APPROVED_FOR_APPLICATION: 'approved',
            REJECTED: 'rejected'
        };
        const statusLabel = statusLabels[statusCls] || statusCls.toLowerCase();

        const job = application.jobTitle || 'Unknown Role';
        const company = application.company || 'Unknown Company';
        const location = application.location || 'Not Specified';

        card.innerHTML = `
            <div class="application-card-header">
                <h3 class="application-card-job-title">${esc(job)}</h3>
                <div class="application-card-company">${esc(company)}</div>
                <span class="application-card-status badge-${statusLabel}">${statusLabel.toUpperCase()}</span>
            </div>

            <div class="application-card-meta">
                <div class="application-card-meta-item">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>
                    <span>${esc(location)}</span>
                </div>
                <div class="application-card-meta-item">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
                    <span>${application.generatedResumeSummary ? 'Summary generated' : 'No summary'}</span>
                </div>
            </div>

            <div class="application-card-excerpt">
                <p>${esc(application.coverLetter ? application.coverLetter.split('\n')[0] : 'No cover letter')}</p>
            </div>

            <div class="application-card-footer">
                <button class="btn-view application-view-btn" data-id="${application.id}">View</button>
                <button class="btn-apply application-approve-btn" data-id="${application.id}">${statusLabel === 'approved' ? 'Approved' : 'Prepare'}</button>
            </div>
        `;

        // Add click handlers
        const viewBtn = card.querySelector('.application-view-btn');
        viewBtn.addEventListener('click', () => viewApplication(application.id));

        const approveBtn = card.querySelector('.application-approve-btn');
        approveBtn.addEventListener('click', () => {
            if (statusLabel === 'approved') {
                showToast('This application has already been approved.', 'info');
                return;
            }
            approveApplication(application.id);
        });

        return card;
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
                applicationDetailStatus.textContent = app.applicationStatus || 'DRAFT';
                applicationDetailJobId.textContent = `Job ID: ${app.jobId}`;
                applicationDetailCompany.textContent = `Company: ${app.company}`;
                applicationDetailLocation.textContent = `Location: ${app.location || 'Not Specified'}`;
                document.getElementById('applicationDetailMatchScore').textContent = `Match Score: ${app.matchScore != null ? app.matchScore + '/100' : '—'}`;

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

                // Recommendation
                const recommendation = app.recommendation || 'POSSIBLE_MATCH';
                applicationDetailRecommendation.textContent = esc(recommendation);

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

    // ─── Boot ──────────────────────────────────────────────────────────────────
    init();
})();