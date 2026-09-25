/**
 * Confirmation dialog built on the single global modal shell.
 *
 * Replaces the browser's native {@code window.confirm()}, which renders as
 * "localhost:8080 says..." and cannot be styled, focused or made consistent with the
 * rest of the application.
 *
 * This deliberately does NOT create its own overlay. It composes {@code window.modalShell}
 * so there is exactly one dialog system in the app: the same z-index, the same Escape and
 * backdrop handling, the same focus management, and no possibility of a confirm stacking
 * behind or on top of another analysis dialog.
 *
 * Usage:
 *   const ok = await window.confirmDialog.ask({
 *       title: 'Prepare application',
 *       message: 'This builds a review-ready package.',
 *       confirmLabel: 'Continue'
 *   });
 *   if (!ok) return;
 *
 * Resolves {@code true} only when the user explicitly chooses Continue. Closing with
 * Escape, the backdrop or the header close button resolves {@code false}, so a dismissed
 * dialog can never be mistaken for consent.
 */
(function () {
    'use strict';

    function esc(text) {
        if (text === 0) return '0';
        if (text === undefined || text === null) return '';
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /**
     * @param {object} spec
     * @param {string} spec.title          dialog heading
     * @param {string} [spec.subtitle]     company / job line shown under the heading
     * @param {string} spec.message        the question, in plain language
     * @param {string} [spec.detail]       supporting explanation
     * @param {string} [spec.warning]      consequence text, rendered in the caution tone
     * @param {string} [spec.confirmLabel] defaults to "Continue"
     * @param {string} [spec.cancelLabel]  defaults to "Cancel"
     * @returns {Promise<boolean>}
     */
    // Single delegated click handler for the answer buttons, attached once for the
    // whole page. The modal shell reuses one persistent overlay and replaces its
    // content on every open(), so attaching a listener here per ask() would
    // accumulate them (and stale ones would fire on later dialogs). Routing every
    // dialog through the active state keeps exactly one effective handler.
    let active = null;
    document.addEventListener('click', e => {
        const state = active;
        if (!state) return;
        if (e.target.closest('[data-confirm-accept]')) {
            state.confirmed = true;
            window.modalShell.close();
        } else if (e.target.closest('[data-confirm-cancel]')) {
            state.confirmed = false;
            window.modalShell.close();
        }
    });

    function ask(spec) {
        const s = spec || {};
        const confirmLabel = s.confirmLabel || 'Continue';
        const cancelLabel = s.cancelLabel || 'Cancel';

        return new Promise(resolve => {
            if (!window.modalShell || typeof window.modalShell.open !== 'function') {
                // No shell available: fail closed rather than silently proceeding with a
                // destructive or irreversible action the user never confirmed.
                console.warn('modalShell unavailable — confirmation declined');
                resolve(false);
                return;
            }

            const state = { confirmed: false };
            let settled = false;
            const finish = value => {
                if (settled) return;
                settled = true;
                if (active === state) active = null;
                resolve(value);
            };

            active = state;

            window.modalShell.open({
                title: s.title || 'Please confirm',
                subtitle: s.subtitle || '',
                dismissable: true,
                body: `
                    <div class="confirm-body">
                        <p class="confirm-message">${esc(s.message || 'Are you sure?')}</p>
                        ${s.detail ? `<p class="confirm-detail">${esc(s.detail)}</p>` : ''}
                        ${s.warning ? `<p class="confirm-warning">${esc(s.warning)}</p>` : ''}
                    </div>`,
                footer: `
                    <button type="button" class="btn-secondary" data-confirm-cancel="true">${esc(cancelLabel)}</button>
                    <button type="button" class="btn-primary" data-confirm-accept="true">${esc(confirmLabel)}</button>`,
                onClose: () => finish(state.confirmed)
            });

            // Move focus to the affirmative action so Enter confirms and Tab starts
            // from a predictable place.
            const overlay = document.querySelector('.ms-overlay');
            if (overlay) {
                const accept = overlay.querySelector('[data-confirm-accept]');
                if (accept) accept.focus();
            }
        });
    }

    window.confirmDialog = { ask: ask };
})();
