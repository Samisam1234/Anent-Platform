/**
 * AI Job Agent — Resume Upload & Analysis page.
 *
 * Flow: upload resume → file selected → analyzing → analysis completed →
 * candidate profile available → continue to Job Search.
 *
 * The upload request is bounded on the client with an AbortController. The
 * backend bounds itself too (ollama.reasoning-timeout, enforced as a real
 * wall-clock deadline in ResumeProfileService), so the client budget is set
 * deliberately LONGER: if the server ever stalls, the server's own deterministic
 * fallback answers first and the user still receives a profile. Either way this
 * page can never stay on "Analyzing…" indefinitely — the loading state is torn
 * down on success, on failure, on timeout and on user cancel.
 */
(() => {
    'use strict';

    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';

    // Server-side AI deadline is ollama.reasoning-timeout = 120s. Give the
    // server 30s of slack so its own fallback wins over a client-side abort.
    const SERVER_AI_BUDGET_MS = 120000;
    const CLIENT_TIMEOUT_MS = SERVER_AI_BUDGET_MS + 30000;
    const MAX_FILE_BYTES = 10 * 1024 * 1024;

    const dropZone = document.getElementById('resumeDropZone');
    const fileInput = document.getElementById('resumeFileInput');
    const uploadBtn = document.getElementById('uploadBtn');
    const clearBtn = document.getElementById('clearBtn');
    const changeFileBtn = document.getElementById('changeFileBtn');
    const cancelAnalyzeBtn = document.getElementById('cancelAnalyzeBtn');
    const retryBtn = document.getElementById('retryBtn');

    const uploadPanel = document.getElementById('resumeUploadPanel');
    const analyzingPanel = document.getElementById('resumeAnalyzingPanel');
    const profileSection = document.getElementById('resumeProfileSection');

    const fileCard = document.getElementById('resumeFileCard');
    const fileName = document.getElementById('resumeFileName');
    const fileSub = document.getElementById('resumeFileSub');

    const errorAlert = document.getElementById('resumeErrorAlert');
    const errorTitle = document.getElementById('resumeErrorTitle');
    const errorMessage = document.getElementById('resumeErrorMessage');
    const errorHints = document.getElementById('resumeErrorHints');

    const analyzingSub = document.getElementById('analyzingSub');
    const analyzingElapsed = document.getElementById('analyzingElapsed');
    const analyzingTrack = document.getElementById('analyzingTrack');
    const analyzingFill = document.getElementById('analyzingFill');
    const analyzingStages = document.getElementById('analyzingStages');
    const analyzingBudgetLabel = document.getElementById('analyzingBudgetLabel');

    const resultBanner = document.getElementById('resumeResultBanner');
    const profileIdBadge = document.getElementById('profileIdBadge');
    const profileDetails = document.getElementById('profileDetails');
    const profileSub = document.getElementById('resumeProfileSub');
    const profileBadge = document.getElementById('profileBadge');
    const profileStatusText = document.getElementById('profileStatusText');
    const toastContainer = document.getElementById('toastContainer');

    const stepEls = Array.from(document.querySelectorAll('#resumeSteps .resume-step'));

    let selectedFile = null;
    let activeController = null;   // AbortController for the in-flight request
    let timeoutHandle = null;      // client-side watchdog
    let elapsedHandle = null;      // analyzing UI ticker
    let userCancelled = false;
    let requestSettled = false;    // guards against double teardown

    // ─── Toast notifications ─────────────────────────────────────────────────
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast ${type === 'error' ? 'toast-error' : ''}`;
        const icon = type === 'error'
            ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'
            : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
        toast.innerHTML = `${icon}<span>${esc(message)}</span>`;
        toastContainer.appendChild(toast);
        setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 4000);
    }

    function esc(text) {
        if (text === null || text === undefined) return '';
        return String(text)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // ─── Step rail ───────────────────────────────────────────────────────────
    // `current` is the step that is in progress; every step before it is complete.
    function setStep(current) {
        const order = stepEls.map(el => el.dataset.step);
        const index = order.indexOf(current);
        stepEls.forEach((el, i) => {
            el.classList.toggle('is-active', i === index);
            el.classList.toggle('is-complete', index > -1 && i < index);
        });
    }

    function showPanel(panel) {
        [uploadPanel, analyzingPanel, profileSection].forEach(p => { p.hidden = (p !== panel); });
    }

    // ─── Analyzing state ─────────────────────────────────────────────────────
    // The three stages are the phases the backend genuinely performs (text
    // extraction → AI structuring → persistence). They advance on elapsed time
    // because the endpoint is a single synchronous request that reports no
    // intermediate events; the bar is deliberately capped below 100% until the
    // response actually arrives.
    const STAGES = [
        { at: 0,     key: 'read',    sub: 'Reading the document…' },
        { at: 2500,  key: 'ai',      sub: 'Structuring skills, experience and education…' },
        { at: Math.max(SERVER_AI_BUDGET_MS - 20000, 30000), key: 'profile', sub: 'Saving your candidate profile…' }
    ];

    function startAnalyzingUi() {
        const startedAt = Date.now();
        analyzingFill.style.width = '0%';
        analyzingTrack.setAttribute('aria-valuenow', '0');
        analyzingElapsed.textContent = '0s';
        Array.from(analyzingStages.querySelectorAll('.resume-analyzing-stage')).forEach((li, i) => {
            li.classList.toggle('is-active', i === 0);
            li.classList.remove('is-done');
        });
        analyzingSub.textContent = STAGES[0].sub;

        elapsedHandle = setInterval(() => {
            const elapsed = Date.now() - startedAt;
            analyzingElapsed.textContent = `${Math.round(elapsed / 1000)}s`;

            // Ease towards 92% of the client budget, never reach 100% early.
            const ratio = Math.min(elapsed / CLIENT_TIMEOUT_MS, 1);
            const pct = Math.round((1 - Math.exp(-3 * ratio)) * 92);
            analyzingFill.style.width = `${pct}%`;
            analyzingTrack.setAttribute('aria-valuenow', String(pct));

            let activeIndex = 0;
            STAGES.forEach((s, i) => { if (elapsed >= s.at) activeIndex = i; });
            const stageEls = Array.from(analyzingStages.querySelectorAll('.resume-analyzing-stage'));
            stageEls.forEach((li, i) => {
                li.classList.toggle('is-active', i === activeIndex);
                li.classList.toggle('is-done', i < activeIndex);
            });
            analyzingSub.textContent = STAGES[activeIndex].sub;
        }, 250);
    }

    function stopAnalyzingUi() {
        if (elapsedHandle) { clearInterval(elapsedHandle); elapsedHandle = null; }
    }

    function completeAnalyzingUi() {
        analyzingFill.style.width = '100%';
        analyzingTrack.setAttribute('aria-valuenow', '100');
        Array.from(analyzingStages.querySelectorAll('.resume-analyzing-stage'))
            .forEach(li => { li.classList.add('is-done'); li.classList.remove('is-active'); });
    }

    // ─── Error state ─────────────────────────────────────────────────────────
    function showError(title, message, hints) {
        errorTitle.textContent = title;
        errorMessage.textContent = message;
        errorHints.innerHTML = '';
        const list = (hints || []).filter(Boolean);
        if (list.length) {
            list.forEach(h => {
                const li = document.createElement('li');
                li.textContent = h;
                errorHints.appendChild(li);
            });
            errorHints.hidden = false;
        } else {
            errorHints.hidden = true;
        }
        errorAlert.hidden = false;
        if (retryBtn) retryBtn.hidden = !selectedFile;
    }

    function hideError() {
        errorAlert.hidden = true;
        if (retryBtn) retryBtn.hidden = true;
    }

    // ─── Profile rendering ───────────────────────────────────────────────────
    function listItems(values) {
        if (!Array.isArray(values)) return [];
        return values.filter(v => v !== null && v !== undefined && String(v).trim() !== '');
    }

    const ICONS = {
        mail: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="4" width="20" height="16" rx="2"></rect><polyline points="2 6 12 13 22 6"></polyline></svg>',
        phone: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.2a2 2 0 0 1 2.1-.5c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"></path></svg>',
        pin: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>'
    };

    function renderListSection(title, values) {
        const items = listItems(values);
        if (items.length === 0) return '';
        return `
            <div class="resume-profile-section">
                <h4 class="resume-profile-section-title">${esc(title)}</h4>
                <ul class="resume-profile-list">${items.map(v => `<li>${esc(v)}</li>`).join('')}</ul>
            </div>`;
    }

    function renderProfile(profile) {
        if (!profile) return '<p class="resume-profile-empty">No profile data was returned by the server.</p>';

        const details = [];
        if (profile.email) details.push(`<span class="resume-contact-item">${ICONS.mail}<span>${esc(profile.email)}</span></span>`);
        if (profile.phone) details.push(`<span class="resume-contact-item">${ICONS.phone}<span>${esc(profile.phone)}</span></span>`);
        if (profile.location) details.push(`<span class="resume-contact-item">${ICONS.pin}<span>${esc(profile.location)}</span></span>`);

        const initials = String(profile.name || 'C').trim().split(/\s+/).slice(0, 2)
            .map(part => part.charAt(0).toUpperCase()).join('') || 'C';

        const header = `
            <div class="resume-profile-id-card">
                <span class="resume-avatar" aria-hidden="true">${esc(initials)}</span>
                <div class="resume-identity">
                    <h3 class="resume-identity-name">${esc(profile.name || 'Candidate')}</h3>
                    ${details.length ? `<div class="resume-contact-row">${details.join('')}</div>` : ''}
                </div>
            </div>`;

        const sections = [
            header,
            renderListSection('Preferred roles', profile.preferredRoles),
            renderListSection('Skills', profile.skills),
            renderListSection('Software skills', profile.softwareSkills),
            renderListSection('Hardware skills', profile.hardwareSkills),
            renderListSection('Education', profile.education),
            renderListSection('Experience', profile.experience),
            renderListSection('Internships', profile.internships),
            renderListSection('Projects', profile.projects),
            renderListSection('Certifications', profile.certifications)
        ].filter(Boolean);

        if (sections.length === 1) {
            sections.push('<p class="resume-profile-empty">Only a name could be extracted. '
                + 'If the file is a scanned image, upload a text-selectable PDF for a richer profile.</p>');
        }
        return sections.join('');
    }

    // ─── File selection ──────────────────────────────────────────────────────
    function formatBytes(bytes) {
        if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
        return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    }

    function handleFile(file) {
        if (!file) return;
        const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
        const looksValid = validTypes.includes(file.type)
            || /\.pdf$/i.test(file.name) || /\.docx$/i.test(file.name);
        if (!looksValid) {
            showError('Unsupported file format',
                `“${file.name}” is not a PDF or DOCX file.`,
                ['Export your resume as a text-selectable PDF or DOCX and try again.']);
            showToast('Unsupported file format. Please upload PDF or DOCX.', 'error');
            return;
        }
        if (file.size > MAX_FILE_BYTES) {
            showError('File too large',
                `“${file.name}” is ${formatBytes(file.size)}. The limit is 10 MB.`,
                ['Remove embedded images or scan artifacts to reduce the file size.']);
            showToast('File too large. Maximum size is 10 MB.', 'error');
            return;
        }

        hideError();
        selectedFile = file;
        fileName.textContent = file.name;
        fileSub.textContent = `${/\.docx$/i.test(file.name) ? 'Word document' : 'PDF document'} · ${formatBytes(file.size)} · ready to analyze`;
        fileCard.hidden = false;
        uploadBtn.disabled = false;
        dropZone.classList.add('has-file');
        setStep('review');
    }

    function resetFileSelection() {
        selectedFile = null;
        fileInput.value = '';
        fileCard.hidden = true;
        uploadBtn.disabled = true;
        dropZone.classList.remove('has-file');
    }

    // ─── Upload (bounded on both client and server) ──────────────────────────
    function uploadResume() {
        if (!selectedFile || activeController) return;

        const formData = new FormData();
        formData.append('file', selectedFile);

        hideError();
        profileSection.hidden = true;
        userCancelled = false;
        requestSettled = false;
        setStep('analyzing');
        showPanel(analyzingPanel);
        startAnalyzingUi();
        uploadBtn.disabled = true;

        // Client-side watchdog. It does not merely abort: it tears the analyzing
        // state down itself, so the page leaves it even if aborting the signal
        // never settles the fetch promise.
        activeController = new AbortController();
        timeoutHandle = setTimeout(onWatchdogTimeout, CLIENT_TIMEOUT_MS);

        fetch('/api/v1/resume/upload', {
            method: 'POST',
            body: formData,
            signal: activeController.signal
        })
        .then(response => {
            if (!response.ok) {
                // The backend answers JSON {"error": "..."}; fall back to text.
                return response.text().then(raw => {
                    let message = raw;
                    try {
                        const parsed = JSON.parse(raw);
                        if (parsed && parsed.error) message = parsed.error;
                    } catch (ignored) { /* not JSON — use the raw body */ }
                    const err = new Error(message || `Server responded with HTTP ${response.status}.`);
                    err.status = response.status;
                    throw err;
                });
            }
            return response.json();
        })
        .then(handleSuccess)
        .catch(handleFailure)
        .finally(finishRequest);
    }

    function onWatchdogTimeout() {
        if (requestSettled) return;
        requestSettled = true;
        if (activeController) activeController.abort(new Error('timeout'));
        showError('Analysis timed out',
            `The server did not finish within ${Math.round(CLIENT_TIMEOUT_MS / 1000)} seconds, `
            + 'so the request was stopped. Your file was not lost.',
            [
                'Start the AI provider (Ollama) and make sure the model is pulled: ollama pull gemma3:4b',
                'The first run after starting Ollama is slow while the model loads into memory — retry once it is warm.',
                'A smaller or text-only resume parses faster.'
            ]);
        showToast('Analysis timed out. Check that the AI provider is running.', 'error');
        setStep(selectedFile ? 'review' : 'upload');
        showPanel(uploadPanel);
        finishRequest();
    }

    function handleSuccess(data) {
        if (requestSettled) return;
        requestSettled = true;
        completeAnalyzingUi();

        const profile = data.profile || {};
        const name = profile.name || 'Candidate';

        profileIdBadge.textContent = `ID ${data.candidateId}`;
        profileDetails.innerHTML = renderProfile(profile);
        profileSub.textContent = data.aiModelUsed
            ? 'Analysis completed with the AI model.'
            : 'Analysis completed with the built-in parser.';

        // Result banner: success, or a warning when the AI provider was not used.
        if (data.aiModelUsed === false) {
            resultBanner.className = 'resume-result-banner is-warning';
            resultBanner.innerHTML = `
                <span class="resume-result-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
                </span>
                <div class="resume-result-text">
                    <strong>Profile created — AI model was not used</strong>
                    <p>${esc(data.notice || 'The AI provider was unavailable, so the built-in parser extracted your profile. '
                        + 'It is accurate for skills and contact details but less detailed than the AI result. '
                        + 'Start the AI provider and re-upload for a richer profile.')}</p>
                </div>`;
        } else {
            resultBanner.className = 'resume-result-banner is-success';
            resultBanner.innerHTML = `
                <span class="resume-result-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><polyline points="16 9 10.5 14.5 8 12"></polyline></svg>
                </span>
                <div class="resume-result-text">
                    <strong>Analysis completed</strong>
                    <p>Your candidate profile is ready and saved. Job Search and Matches will use it automatically.</p>
                </div>`;
        }

        localStorage.setItem(LS_CANDIDATE_ID, String(data.candidateId));
        localStorage.setItem(LS_CANDIDATE_NAME, name);
        profileStatusText.textContent = `Profile: ${name}`;
        profileBadge.classList.add('status-live');

        const nextText = document.getElementById('resumeNextText');
        if (nextText) {
            nextText.innerHTML = `<strong>${esc(name)}’s profile is ready.</strong> `
                + 'Job Search and Matches now use these skills automatically — no keyword typing needed.';
        }

        setStep('profile');
        showPanel(profileSection);
        showToast('Resume analyzed. Continue to Job Search to find matching roles.', 'success');
    }

    function handleFailure(err) {
        if (requestSettled) return;
        requestSettled = true;
        // User pressed Cancel — not an error, just return to the upload panel.
        if (userCancelled) {
            setStep(selectedFile ? 'review' : 'upload');
            showPanel(uploadPanel);
            return;
        }

        const timedOut = err && (err.name === 'AbortError' || err.name === 'TimeoutError'
            || (err.cause && err.cause.message === 'timeout'));
        if (timedOut) {
            showError('Analysis timed out',
                `The server did not finish within ${Math.round(CLIENT_TIMEOUT_MS / 1000)} seconds, `
                + 'so the request was stopped. Your file was not lost.',
                [
                    'Start the AI provider (Ollama) and make sure the model is pulled: ollama pull gemma3:4b',
                    'The first run after starting Ollama is slow while the model loads into memory — retry once it is warm.',
                    'A smaller or text-only resume parses faster.'
                ]);
            showToast('Analysis timed out. Check that the AI provider is running.', 'error');
        } else {
            const message = (err && err.message) ? err.message : 'Unknown error';
            showError('Analysis failed', message, hintsFor(message, err && err.status));
            showToast(message, 'error');
        }

        setStep(selectedFile ? 'review' : 'upload');
        showPanel(uploadPanel);
    }

    function hintsFor(message, status) {
        const lower = String(message || '').toLowerCase();
        const hints = [];
        if (lower.includes('image-based') || lower.includes('scanned') || lower.includes('no useful text')) {
            hints.push('The file appears to be a scanned image. Upload a text-selectable PDF instead.');
        }
        if (lower.includes('password')) {
            hints.push('Remove the password from the file and upload it again.');
        }
        if (lower.includes('ollama') || lower.includes('connection refused') || lower.includes('connect')) {
            hints.push('Start Ollama and pull the model: ollama pull gemma3:4b');
        }
        if (status === 413) hints.push('The file is larger than the server upload limit.');
        if (status === 400) hints.push('Check the file is a valid, uncorrupted PDF or DOCX.');
        if (hints.length === 0) hints.push('Try again — if it keeps failing, check the server logs for the exact cause.');
        return hints;
    }

    // Runs on every path: success, server error, timeout and user cancel.
    function finishRequest() {
        if (timeoutHandle) { clearTimeout(timeoutHandle); timeoutHandle = null; }
        stopAnalyzingUi();
        activeController = null;
        analyzingPanel.setAttribute('aria-busy', 'false');
        uploadBtn.disabled = !selectedFile;
    }

    // ─── Clear ───────────────────────────────────────────────────────────────
    function clearResume() {
        if (activeController) {
            userCancelled = true;
            if (!requestSettled) {
                requestSettled = true;
                activeController.abort(new Error('cancelled'));
            }
        }
        resetFileSelection();
        hideError();
        profileSection.hidden = true;
        analyzingPanel.hidden = true;
        uploadPanel.hidden = false;
        profileDetails.innerHTML = '';
        setStep('upload');
    }

    // ─── Event listeners ─────────────────────────────────────────────────────
    function openFilePicker() { fileInput.click(); }

    dropZone.addEventListener('click', openFilePicker);
    dropZone.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openFilePicker(); }
    });
    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });
    dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('dragover');
        handleFile(e.dataTransfer.files[0]);
    });
    fileInput.addEventListener('change', (e) => handleFile(e.target.files[0]));
    uploadBtn.addEventListener('click', uploadResume);
    clearBtn.addEventListener('click', clearResume);
    changeFileBtn.addEventListener('click', openFilePicker);
    retryBtn && retryBtn.addEventListener('click', uploadResume);
    cancelAnalyzeBtn.addEventListener('click', () => {
        if (!activeController) return;
        userCancelled = true;
        // Tear down directly rather than waiting for the abort to settle the
        // promise, exactly like the watchdog does.
        if (!requestSettled) {
            requestSettled = true;
            activeController.abort(new Error('cancelled'));
            setStep(selectedFile ? 'review' : 'upload');
            showPanel(uploadPanel);
            finishRequest();
        }
        showToast('Analysis cancelled.', 'info');
    });

    // ─── Init ────────────────────────────────────────────────────────────────
    function init() {
        if (analyzingBudgetLabel) {
            analyzingBudgetLabel.textContent = `${Math.round(SERVER_AI_BUDGET_MS / 60000)} minute${SERVER_AI_BUDGET_MS >= 120000 ? 's' : ''}`;
        }
        setStep('upload');
        const candidateId = localStorage.getItem(LS_CANDIDATE_ID);
        const candidateName = localStorage.getItem(LS_CANDIDATE_NAME);
        if (candidateId && candidateName) {
            profileStatusText.textContent = `Profile: ${candidateName}`;
            profileBadge.classList.add('status-live');
        }
    }
    init();
})();
