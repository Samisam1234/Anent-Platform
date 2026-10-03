/**
 * AI Job Agent — single global modal shell.
 *
 * WHY THIS EXISTS
 * Match Details, Career Analysis, the Application Advisor and the prepared-application
 * review each used to create their own fixed-position overlay. Three of them sat at
 * z-index 200 and one at 1000, so opening Career Analysis from Match Details rendered
 * it *behind* the dialog that launched it, and both stayed on screen with the body
 * scroll lock applied twice. That is a lifecycle problem, not a stacking problem, so it
 * is fixed by having exactly one dialog container instead of by raising z-index.
 *
 * CONTRACT
 *  - One overlay element exists for the whole page, created once and reused.
 *  - open() REPLACES the previous content; it never nests or stacks a second dialog.
 *  - Escape and backdrop click close the active dialog (unless dismissable is false).
 *  - Body scrolling is locked while open and restored exactly once on close.
 *  - Callers own only their content; they never create their own overlay.
 *
 * Content is supplied as HTML by the callers, which are responsible for escaping
 * untrusted values (every caller here uses its own esc()).
 */
(() => {
    'use strict';

    let overlay = null;
    let closeHandler = null;
    let lastFocused = null;

    const CARD_HTML = `
        <div class="ms-card" role="dialog" aria-modal="true" aria-labelledby="msTitle">
            <header class="ms-header">
                <div class="ms-header-text">
                    <span class="ms-kicker" id="msKicker" hidden></span>
                    <h2 class="ms-title" id="msTitle">Details</h2>
                    <p class="ms-subtitle" id="msSubtitle" hidden></p>
                </div>
                <button type="button" class="modal-close" id="msClose" aria-label="Close">&times;</button>
            </header>
            <div class="ms-body" id="msBody"></div>
            <footer class="ms-footer" id="msFooter" hidden></footer>
        </div>`;

    function ensureOverlay() {
        if (overlay) return overlay;

        overlay = document.createElement('div');
        overlay.className = 'ms-overlay';
        overlay.hidden = true;
        overlay.innerHTML = CARD_HTML;

        overlay.addEventListener('click', (e) => {
            // Backdrop click only — clicks inside the card must not dismiss it.
            if (e.target === overlay && overlay.dataset.dismissable !== 'false') close();
        });
        overlay.querySelector('#msClose').addEventListener('click', () => close());

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && overlay && !overlay.hidden) close();
        });

        document.body.appendChild(overlay);
        return overlay;
    }

    function setText(id, value) {
        const el = overlay.querySelector('#' + id);
        if (!el) return;
        const text = value == null ? '' : String(value);
        el.textContent = text;
        el.hidden = text === '';
    }

    /**
     * Opens the single global dialog, replacing whatever was there.
     *
     * @param {object} spec
     * @param {string} [spec.kicker]    small label above the title
     * @param {string} spec.title       dialog title
     * @param {string} [spec.subtitle]  one-line context under the title
     * @param {string} [spec.body]      HTML body content
     * @param {string} [spec.footer]    HTML footer content (actions)
     * @param {boolean} [spec.dismissable=true] whether backdrop/Escape may close it
     * @param {Function} [spec.onClose] invoked after the dialog is dismissed
     */
    function open(spec) {
        const s = spec || {};
        const el = ensureOverlay();

        // Replace, never stack: any previously registered close hook is dropped so a
        // superseded dialog cannot run cleanup after a newer one has taken over.
        closeHandler = typeof s.onClose === 'function' ? s.onClose : null;
        lastFocused = document.activeElement;

        setText('msKicker', s.kicker);
        setText('msTitle', s.title || 'Details');
        setText('msSubtitle', s.subtitle);

        const body = el.querySelector('#msBody');
        body.innerHTML = s.body || '';
        body.scrollTop = 0;

        const footer = el.querySelector('#msFooter');
        footer.innerHTML = s.footer || '';
        footer.hidden = !footer.innerHTML.trim();

        el.dataset.dismissable = s.dismissable === false ? 'false' : 'true';
        el.hidden = false;
        document.body.classList.add('modal-open');

        const closeBtn = el.querySelector('#msClose');
        if (closeBtn) closeBtn.focus();
    }

    function close() {
        if (!overlay || overlay.hidden) return;
        overlay.hidden = true;
        document.body.classList.remove('modal-open');

        const handler = closeHandler;
        closeHandler = null;
        if (lastFocused && typeof lastFocused.focus === 'function') {
            try { lastFocused.focus({ preventScroll: true }); } catch (_) { /* element may be gone */ }
        }
        lastFocused = null;
        if (handler) {
            try { handler(); } catch (e) { console.error('modal onClose failed:', e); }
        }
    }

    /** Updates just the body, for callers that load content asynchronously. */
    function setBody(html) {
        const el = ensureOverlay();
        el.querySelector('#msBody').innerHTML = html || '';
    }

    /** Updates just the footer, for callers that add actions after content resolves. */
    function setFooter(html) {
        const el = ensureOverlay();
        const footer = el.querySelector('#msFooter');
        footer.innerHTML = html || '';
        footer.hidden = !footer.innerHTML.trim();
    }

    function isOpen() {
        return !!overlay && !overlay.hidden;
    }

    window.modalShell = {
        open: open,
        close: close,
        setBody: setBody,
        setFooter: setFooter,
        isOpen: isOpen
    };
})();
