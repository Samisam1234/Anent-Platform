/**
 * AI Job Agent — Dashboard page.
 * Lightweight enhancements: reflects backend status and the active candidate
 * profile stored by resume upload (localStorage keys preserved from the
 * Resume → Matches journey so nothing breaks when the user jumps around).
 */
(() => {
    'use strict';

    const LS_CANDIDATE_ID = 'agentplatform:candidateId';
    const LS_CANDIDATE_NAME = 'agentplatform:candidateName';

    const statusBadge = document.getElementById('statusBadge');
    const profileBadge = document.getElementById('profileBadge');
    const profileStatusText = document.getElementById('profileStatusText');
    const toastContainer = document.getElementById('toastContainer');

    function showToast(message, type = 'info') {
        if (!toastContainer) return;
        const toast = document.createElement('div');
        toast.className = `toast ${type === 'error' ? 'toast-error' : ''}`;
        const icon = type === 'error'
            ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'
            : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>';
        toast.innerHTML = `${icon}<span>${message}</span>`;
        toastContainer.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(30px)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    function initProfileBadge() {
        const candidateId = localStorage.getItem(LS_CANDIDATE_ID);
        const candidateName = localStorage.getItem(LS_CANDIDATE_NAME);
        if (candidateId && candidateName) {
            profileBadge.classList.add('status-live');
            profileStatusText.textContent = `Profile: ${candidateName}`;
        } else {
            profileStatusText.textContent = 'No profile';
        }
        profileBadge.hidden = false;
    }

    function probeStatus() {
        if (!statusBadge) return;
        fetch('/api/v1/ai/status')
            .then(response => response.json())
            .then(data => {
                const live = data && data.configured;
                const dot = statusBadge.querySelector('.status-dot');
                const text = statusBadge.querySelector('.status-text');
                statusBadge.classList.toggle('status-live', !!live);
                if (text) text.textContent = live ? 'Online' : 'Online (no AI key)';
            })
            .catch(() => {
                const text = statusBadge.querySelector('.status-text');
                if (text) text.textContent = 'Offline';
            });
    }

    // ─── Career Fit ───────────────────────────────────────────────────────────
    const careerFitPanel = document.getElementById('careerFitPanel');
    const careerFitBody = document.getElementById('careerFitBody');
    const careerFitDesc = document.getElementById('careerFitDesc');

    const LEVEL_CLASS = {
        Strong: 'level-strong',
        Developing: 'level-developing',
        Emerging: 'level-emerging'
    };

    /**
     * Renders the Career Fit section from the snapshot resume.js stored after the
     * upload. Every entry traces back to parsed resume evidence, so the section is
     * hidden entirely when there is no profile or no usable evidence — it never
     * shows a placeholder score.
     */
    function renderCareerFit() {
        if (!careerFitPanel || !careerFitBody || !window.careerFit) return;

        const snapshot = window.careerFit.load();
        const candidateId = localStorage.getItem(LS_CANDIDATE_ID);
        if (!snapshot || !candidateId) {
            careerFitPanel.hidden = true;
            return;
        }

        careerFitPanel.hidden = false;
        if (careerFitDesc) {
            careerFitDesc.textContent = snapshot.candidateName
                ? `Derived from the evidence in ${snapshot.candidateName}'s uploaded resume.`
                : 'Derived from the evidence in your uploaded resume.';
        }

        const blocks = [];

        if (snapshot.tracks && snapshot.tracks.length) {
            blocks.push(careerFitBlock('Strongest career tracks',
                `<ul class="career-fit-tracks">${snapshot.tracks.map(track => `
                    <li class="career-fit-track">
                        <span class="career-fit-track-label">${esc(track.label)}</span>
                        <span class="career-fit-level ${LEVEL_CLASS[track.level] || ''}">${esc(track.level)}</span>
                        <span class="career-fit-track-evidence">${esc(track.skillCount)} skill${track.skillCount === 1 ? '' : 's'} as evidence</span>
                        ${track.skills && track.skills.length
                            ? `<span class="career-fit-track-skills">${track.skills.map(sk => `<span class="skill-tag">${esc(sk)}</span>`).join('')}</span>`
                            : ''}
                    </li>`).join('')}</ul>`,
                'Ranked by how much resume evidence supports each track.'));
        }

        if (snapshot.roles && snapshot.roles.length) {
            blocks.push(careerFitBlock('Recommended roles',
                `<div class="career-fit-chips">${snapshot.roles.map(r => `<span class="career-fit-chip">${esc(r)}</span>`).join('')}</div>`,
                'Roles inferred from your detected career tracks.'));
        }

        if (snapshot.strongSkills && snapshot.strongSkills.length) {
            blocks.push(careerFitBlock('Strong skills',
                `<div class="career-fit-chips">${snapshot.strongSkills.map(sk => `<span class="career-fit-chip is-strong">${esc(sk)}</span>`).join('')}</div>`,
                'Backed by strong evidence in your resume.'));
        }

        if (snapshot.developingSkills && snapshot.developingSkills.length) {
            blocks.push(careerFitBlock('Developing skills',
                `<div class="career-fit-chips">${snapshot.developingSkills.map(sk => `<span class="career-fit-chip">${esc(sk)}</span>`).join('')}</div>`,
                'Present with moderate evidence — worth more detail.'));
        }

        if (snapshot.limitedSkills && snapshot.limitedSkills.length) {
            blocks.push(careerFitBlock('Where to add evidence',
                `<div class="career-fit-chips">${snapshot.limitedSkills.map(sk => `<span class="career-fit-chip is-limited">${esc(sk)}</span>`).join('')}</div>`,
                'Mentioned once with little supporting detail. Per-role gaps are listed in Matches.'));
        }

        careerFitBody.innerHTML = blocks.length
            ? blocks.join('')
            : `<p class="career-fit-empty">Your resume did not contain enough structured evidence to describe a career fit.
                 Re-upload a text-selectable PDF for a richer profile.</p>`;
    }

    function careerFitBlock(title, bodyHtml, note) {
        return `
            <div class="career-fit-block">
                <h3 class="career-fit-block-title">${esc(title)}</h3>
                ${bodyHtml}
                ${note ? `<p class="career-fit-note">${esc(note)}</p>` : ''}
            </div>`;
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

    initProfileBadge();
    probeStatus();
    renderCareerFit();
})();