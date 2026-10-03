/**
 * Single source of truth for "where does this job's button go".
 *
 * A job listing carries two different URLs and they are not interchangeable:
 *
 *   sourceUrl       — the page on the originating board where the listing was found
 *                     (Remotive, Adzuna, Arbeitnow, …).
 *   applicationUrl  — the employer's own application destination, when the source
 *                     actually supplies one. It is null far more often than not,
 *                     because none of the current public feeds publish it.
 *
 * Presenting sourceUrl as "the application" would be a fabricated destination, so the
 * resolution order is fixed and the UI labels each outcome precisely:
 *
 *   employer application URL present -> "Apply on Employer Site"
 *   only a source listing URL       -> "View Job Listing"
 *   neither                         -> "Application Link Unavailable" (disabled, with a reason)
 *
 * Every consumer (Jobs, Matches, Match Details, Applications) resolves through this
 * module so the labels and the destination cannot drift apart between pages.
 */
(function () {
    'use strict';

    const MOCK_SOURCE = 'MOCK_SOURCE';

    /** Development catalog rows have no real upstream page, so they get no external link. */
    function isMockJob(job) {
        return !!job && job.source === MOCK_SOURCE;
    }

    /**
     * Mirrors the backend's {@code JobUrlValidator}: only absolute http(s) destinations to
     * a real public host are ever rendered as links. Loopback, RFC 1918 private ranges and
     * development/placeholder domains are refused here too, so a stored listing can never
     * produce a clickable internal or fake destination even if validation was bypassed
     * upstream.
     */
    const PRIVATE_IP = /^(10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.)/;
    const FAKE_DOMAIN = /mockjobs|example\.|\.test$|\.internal$|\.invalid$|0\.0\.0\.0/;
    const LOOPBACK = ['localhost', '127.0.0.1', '::1', '0.0.0.0'];

    function safeUrl(value) {
        if (typeof value !== 'string') return null;
        const trimmed = value.trim();
        if (!trimmed) return null;

        const parsed = /^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/(?:[^@/?#]*@)?(\[[^\]]*\]|[^:/?#]*)/.exec(trimmed);
        if (!parsed) return null;

        const scheme = parsed[1].toLowerCase();
        if (scheme !== 'http' && scheme !== 'https') return null;

        let host = (parsed[2] || '').toLowerCase();
        if (!host) return null;
        if (host.startsWith('[') && host.endsWith(']')) host = host.slice(1, -1);

        if (LOOPBACK.indexOf(host) !== -1) return null;
        if (PRIVATE_IP.test(host)) return null;
        if (FAKE_DOMAIN.test(host)) return null;

        return trimmed;
    }

    /**
     * Resolves the single best destination for a job.
     *
     * @param {object} job a job listing from the API
     * @returns {{kind:('employer'|'listing'|'none'), url:(string|null),
     *            label:string, reason:(string|null), source:(string|null)}}
     */
    function resolve(job) {
        if (!job || typeof job !== 'object') {
            return none('No listing data is available for this job.');
        }
        const source = typeof job.source === 'string' && job.source.trim() ? job.source.trim() : null;

        if (isMockJob(job)) {
            return none('This is a development sample listing, so it has no external destination.');
        }

        const applicationUrl = safeUrl(job.applicationUrl);
        if (applicationUrl) {
            return {
                kind: 'employer',
                url: applicationUrl,
                label: 'Apply on Employer Site',
                reason: null,
                source: source
            };
        }

        const sourceUrl = safeUrl(job.sourceUrl);
        if (sourceUrl) {
            return {
                kind: 'listing',
                url: sourceUrl,
                label: 'View Job Listing',
                reason: source
                    ? `This source provides the listing page on ${source}, not a direct employer application link.`
                    : 'This source provides the listing page, not a direct employer application link.',
                source: source
            };
        }

        return none(source
            ? `${source} did not provide a usable link for this listing.`
            : 'This source did not provide a usable link for this listing.');
    }

    function none(reason) {
        return { kind: 'none', url: null, label: 'Application Link Unavailable', reason: reason, source: null };
    }

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
     * Renders the action control for a job: a real anchor when a destination exists,
     * otherwise a disabled button with a tooltip explaining why. The button never
     * pretends to be something the data does not support.
     *
     * @param {object} job
     * @param {{className?:string, withNote?:boolean, withIcon?:boolean}} [options]
     */
    function actionHtml(job, options) {
        const opts = options || {};
        const extraClass = opts.className ? ' ' + opts.className : '';
        const target = resolve(job);

        if (target.kind === 'none') {
            return `<button type="button" class="btn-view-job is-disabled${extraClass}" disabled
                aria-disabled="true" title="${esc(target.reason || '')}">
                <span>${esc(target.label)}</span></button>`;
        }

        const isEmployer = target.kind === 'employer';
        const title = isEmployer
            ? "Opens the employer's own application page in a new tab"
            : `Opens the original listing${target.source ? ' on ' + target.source : ''} in a new tab`;
        const kindClass = isEmployer ? ' btn-apply-external' : ' btn-source-listing';
        const icon = opts.withIcon
            ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path><polyline points="15 3 21 3 21 9"></polyline><line x1="10" y1="14" x2="21" y2="3"></line></svg>`
            : '';

        const anchor = `<a href="${esc(target.url)}" target="_blank" rel="noopener noreferrer"
            class="btn-view-job${kindClass}${extraClass}" title="${esc(title)}">
            <span>${esc(target.label)}</span>${icon}</a>`;

        // Say plainly that the board page is not the application, so the user is never
        // led to believe the platform found an employer destination it does not have.
        const note = (!isEmployer && opts.withNote !== false && target.reason)
            ? `<span class="external-note">${esc(target.reason)}</span>`
            : '';
        return anchor + note;
    }

    window.jobLink = {
        resolve: resolve,
        actionHtml: actionHtml,
        isMockJob: isMockJob,
        hasDestination: function (job) {
            return resolve(job).kind !== 'none';
        }
    };
})();
