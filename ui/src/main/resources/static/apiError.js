/**
 * Turns a failed request into a specific, safe, human-readable message.
 *
 * The backend already logs the real exception with its stack trace and returns a
 * deliberately generic ProblemDetail, so the browser must never be the place where
 * technical detail appears — but it is the only place that knows *which action* failed.
 * This module supplies that context.
 *
 * Guarantees:
 *  - the user is told what failed and what to do, not "an unexpected error occurred";
 *  - a network failure is distinguished from a server error;
 *  - nothing that looks like a stack trace, filesystem path, class name, API key or raw
 *    provider body can ever be rendered, even if a backend message contains one.
 */
(function () {
    'use strict';

    /** Markers that must never reach the screen. */
    const SENSITIVE = [
        /\bat\s+[\w.$]+\(/,            // Java stack frame: at com.foo.Bar.baz(Bar.java:1)
        /\.java:\d+/,
        /[\w.$]*(Exception|Error|Throwable)\s*:/,
        /\/(?:home|var|opt|usr|tmp)\//,
        /[A-Za-z]:\\/,                 // Windows path
        /(?:api[_-]?key|secret|token|password|authorization)\s*[:=]/i,
        /jdbc:|postgres(?:ql)?:\/\//i,
        /[{}]/,                        // raw JSON / object braces
        /org\.springframework|jakarta\.|java\./
    ];

    function looksUnsafe(text) {
        return SENSITIVE.some(re => re.test(text));
    }

    function isNetworkFailure(err) {
        if (!err) return false;
        if (err instanceof TypeError) return true;
        const msg = String(err.message || '');
        return /failed to fetch|networkerror|load failed|network request failed/i.test(msg);
    }

    /**
     * Builds the user-facing message.
     *
     * @param {*} err     whatever the call rejected with (Error, Response, ProblemDetail, string)
     * @param {string} context  sentence naming the action, e.g. "Could not load Career Analysis."
     * @returns {string}
     */
    function describe(err, context) {
        const fallback = context || 'The request could not be completed. Please try again.';

        if (isNetworkFailure(err)) {
            return 'Could not reach the server. Check that the application is still running, then try again.';
        }

        // A ProblemDetail / JSON error body carries the server's own safe detail.
        const detail = err && typeof err === 'object'
            ? (err.detail || err.message || err.error)
            : (typeof err === 'string' ? err : null);

        const status = err && typeof err === 'object' ? (err.status || err.statusCode) : null;
        if (status === 404) {
            return 'That record no longer exists. It may have been removed — refresh the page and try again.';
        }
        if (status === 401 || status === 403) {
            return 'You do not have permission to perform that action.';
        }
        if (status === 429) {
            return 'Too many requests. Please wait a moment and try again.';
        }

        if (typeof detail === 'string' && detail.trim() && !looksUnsafe(detail)) {
            return detail.trim();
        }

        return fallback;
    }

    /**
     * Reads a failed {@link Response} and rejects with an Error carrying the server's
     * safe detail plus the status, so {@link #describe} can produce a precise message.
     */
    function fromResponse(response, context) {
        const status = response ? response.status : 0;
        const parse = response && typeof response.json === 'function'
            ? response.json().catch(() => null)
            : Promise.resolve(null);
        return parse.then(body => {
            const detail = body && (body.detail || body.error || body.message);
            const err = new Error(typeof detail === 'string' && !looksUnsafe(detail)
                ? detail
                : httpFallback(status));
            err.status = status;
            err.detail = err.message;
            err.context = context;
            throw err;
        });
    }

    function httpFallback(status) {
        if (status >= 500) return 'The server could not complete the request. Please try again later.';
        if (status === 404) return 'That record no longer exists.';
        if (status === 400) return 'The request was missing information or contained invalid values.';
        if (status === 429) return 'Too many requests. Please wait a moment and try again.';
        if (status > 0) return 'The request failed (HTTP ' + status + ').';
        return 'The request could not be completed. Please try again.';
    }

    window.apiError = {
        describe: describe,
        fromResponse: fromResponse,
        looksUnsafe: looksUnsafe
    };
})();
