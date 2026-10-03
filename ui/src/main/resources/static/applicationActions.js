/**
 * Action bar for the visible review modal: the next-step actions a package offers.
 *
 * Pure for the same reason applicationTimeline.js is — the eligibility matrix and the
 * wording are the part that must not drift, and both are assertable without a browser
 * (`ui/src/test/js/applicationActions.test.mjs`).
 *
 * The matrix is the one the product already documents (applications.js
 * `updateActionButtons`): both actions belong to a package the user approved, and to
 * nothing else. Draft, prepared, ready-for-review, not-pursuing, archived and
 * already-emailed packages get no action bar at all.
 *
 * Rendering an action is not performing it. Nothing here sends mail or opens the
 * employer site: both stay strictly click-driven in applications.js, and the wording
 * keeps the same limits the timeline keeps — a simulated send mails nothing, and
 * opening the employer's site is not a submission this platform can confirm.
 */
(function (global) {
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

    const ELIGIBLE_STATUS = 'APPROVED_FOR_APPLICATION';

    const ACTIONS = [
        {
            key: 'email',
            attribute: 'data-review-email',
            label: 'Email this package',
            cls: 'btn-secondary',
            title: 'Asks you to confirm the recipient address first. Nothing is emailed until you confirm it.'
        },
        {
            key: 'apply',
            attribute: 'data-review-apply',
            label: 'Assisted Apply',
            cls: 'btn-primary',
            title: 'Opens the Apply Kit, which prepares values to copy and hands you to the employer\u2019s own site.'
        }
    ];

    const NOTE = 'Email is simulated unless SMTP is configured, so a simulated attempt mails nothing and no delivery can be claimed. Assisted Apply opens the employer site for you to submit yourself \u2014 this platform cannot submit an application or confirm that one happened.';

    /**
     * @param {object} app persisted application entity
     * @returns {Array<object>} the offered actions; empty for every ineligible status.
     */
    function actions(app) {
        const a = app || {};
        return (a.applicationStatus || 'DRAFT') === ELIGIBLE_STATUS ? ACTIONS.slice() : [];
    }

    /**
     * The modal section. Returns '' when the package is not eligible, so an ineligible
     * package never renders a disabled or stale action control.
     * @param {object} app persisted application entity
     * @returns {string} section markup, or '' when no action is offered
     */
    function section(app) {
        const list = actions(app);
        if (!list.length || !app || app.id === undefined || app.id === null) return '';
        const id = esc(app.id);
        const buttons = list.map(action => `
                        <button type="button" class="${esc(action.cls)}" ${action.attribute}="${id}" title="${esc(action.title)}">${esc(action.label)}</button>`).join('');
        return `
                <div class="modal-job-section" id="reviewActionsSection">
                    <h4 class="modal-section-title">Send or apply</h4>
                    <div class="review-action-row">${buttons}</div>
                    <p class="agent-note">${esc(NOTE)}</p>
                </div>`;
    }

    global.applicationActions = { actions: actions, section: section, ELIGIBLE_STATUS: ELIGIBLE_STATUS };
})(typeof window !== 'undefined' ? window : globalThis);
