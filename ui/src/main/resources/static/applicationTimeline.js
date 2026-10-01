/**
 * Application event timeline: rows + markup for the review modal.
 *
 * Pure on purpose — it takes the persisted application fields and returns data/HTML.
 * No DOM lookups, no network, no clock of its own, so every row condition can be
 * asserted in isolation (`ui/src/test/js/applicationTimeline.test.mjs`).
 *
 * Truthfulness rules this file must keep:
 *  - a row exists only when its timestamp is persisted and parseable; never fabricate one;
 *  - "Last updated" only appears when it differs from creation;
 *  - a simulated email send is labelled simulated and never as mailed or delivered;
 *  - opening the employer's site is what was observed — this platform cannot observe a
 *    submission, so no row may claim one.
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

    const ms = value => new Date(value).getTime();

    /**
     * @param {object} app persisted application entity
     * @returns {Array<{label:string, at:Date, detail:string|null, url:string|null}>}
     *   events in real chronological order; empty when nothing is persisted.
     */
    function rows(app) {
        const a = app || {};
        const out = [];

        const push = (label, value, detail, url) => {
            if (!value) return; // no timestamp -> no row
            const at = new Date(value);
            if (Number.isNaN(at.getTime())) return; // unusable timestamp -> no row
            out.push({ label: label, at: at, detail: detail || null, url: url || null });
        };

        push('Prepared', a.createdAt);
        if (a.updatedAt && (!a.createdAt || ms(a.updatedAt) !== ms(a.createdAt))) {
            push('Last updated', a.updatedAt);
        }
        push('Approved for application', a.approvedAt);
        if (a.emailSendAttemptedAt) {
            push('Email send attempt', a.emailSendAttemptedAt,
                a.emailSendResult === 'SENT_SIMULATED'
                    ? 'Email send simulated — no SMTP configured. Nothing was actually mailed.'
                    : (a.emailSendResult === 'SENT'
                        ? 'Email send reported by email service'
                        : 'Email send attempt recorded'));
        }
        if (a.employerOpenedAt) {
            push('Employer site opened', a.employerOpenedAt,
                '— submission not confirmed by this platform', a.employerUrl);
        }

        return out.sort((x, y) => x.at - y.at);
    }

    function itemHtml(row) {
        const when = row.at.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
        const link = row.url
            ? `<a href="${esc(row.url)}" target="_blank" rel="noopener noreferrer">${esc(row.url)}</a> `
            : '';
        const detail = row.detail
            ? `<div class="application-timeline-detail">${link}${esc(row.detail)}</div>`
            : '';
        return `
                <li class="application-timeline-item">
                    <span class="application-timeline-dot" aria-hidden="true"></span>
                    <div class="application-timeline-body">
                        <div class="application-timeline-head">
                            <span class="application-timeline-label">${esc(row.label)}</span>
                            <time class="application-timeline-when">${esc(when)}</time>
                        </div>
                        ${detail}
                    </div>
                </li>`;
    }

    function html(list) {
        return list.map(itemHtml).join('');
    }

    /**
     * The modal section. Returns '' when no event is persisted, so the caller never
     * renders an empty (or stale) timeline heading.
     * @param {object} app persisted application entity
     * @returns {string} section markup, or '' when there is nothing to show
     */
    function section(app) {
        const list = rows(app);
        if (!list.length) return '';
        return `
                <div class="modal-job-section" id="reviewTimelineSection">
                    <h4 class="modal-section-title">Timeline</h4>
                    <ul class="application-timeline" id="reviewTimeline">${html(list)}</ul>
                </div>`;
    }

    global.applicationTimeline = { rows: rows, html: html, section: section };
})(typeof window !== 'undefined' ? window : globalThis);
