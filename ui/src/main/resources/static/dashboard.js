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

    initProfileBadge();
    probeStatus();
})();