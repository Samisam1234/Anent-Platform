/**
 * AI Job Agent — Resume Upload Page
 * Handles file selection, upload to /api/v1/resume/upload,
 * and displays the parsed candidate profile.
 */
(() => {
    'use strict';

    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';

    const dropZone = document.getElementById('resumeDropZone');
    const fileInput = document.getElementById('resumeFileInput');
    const uploadBtn = document.getElementById('uploadBtn');
    const clearBtn = document.getElementById('clearBtn');
    const fileInfo = document.getElementById('resumeFileInfo');
    const parseStatus = document.getElementById('resumeParseStatus');
    const profileSection = document.getElementById('resumeProfileSection');
    const profileIdBadge = document.getElementById('profileIdBadge');
    const profileDetails = document.getElementById('profileDetails');
    const profileBadge = document.getElementById('profileBadge');
    const profileStatusText = document.getElementById('profileStatusText');
    const toastContainer = document.getElementById('toastContainer');

    let selectedFile = null;

    // ─── Toast notifications ──
    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `toast ${type === 'error' ? 'toast-error' : ''}`;
        const icon = type === 'error'
            ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'
            : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
        toast.innerHTML = `${icon}<span>${message}</span>`;
        toastContainer.appendChild(toast);
        setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, 4000);
    }

    function esc(text) {
        if (!text) return '';
        return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function listItems(values) {
        if (!Array.isArray(values) || values.length === 0) return [];
        return values.filter(v => v != null && String(v).trim() !== '');
    }

    function renderListSection(title, values) {
        const items = listItems(values);
        if (items.length === 0) return '';
        return `
            <div class="prep-review-section">
                <h4 class="prep-review-section-title">${esc(title)}</h4>
                <ul class="prep-ul">${items.map(v => `<li>${esc(v)}</li>`).join('')}</ul>
            </div>`;
    }

    function renderProfile(profile) {
        if (!profile) return '<p class="muted">No profile data available.</p>';
        const sections = [];

        const details = [];
        if (profile.email) details.push(`<span>📧 ${esc(profile.email)}</span>`);
        if (profile.phone) details.push(`<span>📞 ${esc(profile.phone)}</span>`);
        if (profile.location) details.push(`<span>📍 ${esc(profile.location)}</span>`);

        const header = `
            <div class="prep-review-section">
                <h3 class="prep-review-card-name">${esc(profile.name || 'Candidate')}</h3>
                ${details.length ? `<div class="prep-review-card-contact">${details.join('')}</div>` : ''}
            </div>`;

        sections.push(header);
        sections.push(renderListSection('Preferred Roles', profile.preferredRoles));
        sections.push(renderListSection('Skills', profile.skills));
        sections.push(renderListSection('Software Skills', profile.softwareSkills));
        sections.push(renderListSection('Hardware Skills', profile.hardwareSkills));
        sections.push(renderListSection('Education', profile.education));
        sections.push(renderListSection('Projects', profile.projects));
        sections.push(renderListSection('Experience', profile.experience));
        sections.push(renderListSection('Internships', profile.internships));
        sections.push(renderListSection('Certifications', profile.certifications));

        return sections.join('');
    }

    // ─── File selection ──
    function handleFile(file) {
        if (!file) return;
        const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
        if (!validTypes.includes(file.type) && !file.name.endsWith('.pdf') && !file.name.endsWith('.docx')) {
            showToast('Unsupported file format. Please upload PDF or DOCX.', 'error');
            return;
        }
        if (file.size > 10 * 1024 * 1024) {
            showToast('File too large. Maximum size is 10 MB.', 'error');
            return;
        }
        selectedFile = file;
        fileInfo.textContent = `Selected: ${file.name} (${(file.size / 1024).toFixed(1)} KB)`;
        uploadBtn.disabled = false;
    }

    // ─── Upload ──
    function uploadResume() {
        if (!selectedFile) return;
        const formData = new FormData();
        formData.append('file', selectedFile);

        setStatus('loading', 'Parsing resume…');
        uploadBtn.disabled = true;

        fetch('/api/v1/resume/upload', {
            method: 'POST',
            body: formData
        })
        .then(response => {
            if (!response.ok) {
                return response.text().then(msg => { throw new Error(msg || `HTTP ${response.status}`); });
            }
            return response.json();
        })
        .then(data => {
            setStatus('success', `Profile created successfully (ID: ${data.candidateId}).`);
            profileIdBadge.textContent = `ID: ${data.candidateId}`;
            profileDetails.innerHTML = renderProfile(data.profile);
            profileSection.hidden = false;

            // Store candidate info in localStorage
            localStorage.setItem(LS_CANDIDATE_ID, String(data.candidateId));
            localStorage.setItem(LS_CANDIDATE_NAME, data.profile.name || 'Candidate');
            profileStatusText.textContent = `Profile: ${data.profile.name || 'Candidate'}`;
            profileBadge.classList.add('status-live');

            showToast('Resume uploaded and profile created successfully!', 'success');
        })
        .catch(err => {
            setStatus('error', `Upload failed: ${err.message || 'Unknown error'}`);
            showToast(err.message || 'Upload failed.', 'error');
        })
        .finally(() => { uploadBtn.disabled = false; });
    }

    function setStatus(type, message) {
        parseStatus.className = `resume-parse-status ${type}`;
        parseStatus.textContent = message;
        parseStatus.hidden = false;
    }

    // ─── Clear ──
    function clearResume() {
        selectedFile = null;
        fileInput.value = '';
        fileInfo.textContent = '';
        uploadBtn.disabled = true;
        parseStatus.hidden = true;
        profileSection.hidden = true;
    }

    // ─── Event listeners ──
    dropZone.addEventListener('click', () => fileInput.click());
    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('dragover'); });
    dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('dragover');
        const file = e.dataTransfer.files[0];
        handleFile(file);
    });
    fileInput.addEventListener('change', (e) => handleFile(e.target.files[0]));
    uploadBtn.addEventListener('click', uploadResume);
    clearBtn.addEventListener('click', clearResume);

    // ─── Init ──
    function init() {
        const candidateId = localStorage.getItem(LS_CANDIDATE_ID);
        const candidateName = localStorage.getItem(LS_CANDIDATE_NAME);
        if (candidateId && candidateName) {
            profileStatusText.textContent = `Profile: ${candidateName}`;
            profileBadge.classList.add('status-live');
        }
    }
    init();
})();