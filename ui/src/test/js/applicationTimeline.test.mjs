/**
 * Focused tests for the application event timeline conditions.
 * Run: node --test ui/src/test/js/applicationTimeline.test.mjs
 *
 * The module under test is the same file the browser loads, so these assertions
 * cover the real rendering logic (no reimplementation, no fixtures for events that
 * the platform cannot observe).
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
// the browser file is a plain script: it publishes itself on the global object
require('../../main/resources/static/applicationTimeline.js');
const timeline = globalThis.applicationTimeline;
assert.ok(timeline && typeof timeline.rows === 'function', 'applicationTimeline did not publish rows()');

const T0 = '2026-10-01T10:00:00.000Z';
const T1 = '2026-10-01T11:00:00.000Z';
const T2 = '2026-10-01T12:00:00.000Z';
const T3 = '2026-10-01T13:00:00.000Z';

const labels = app => timeline.rows(app).map(r => r.label);

test('no persisted timestamps produces no rows and no section', () => {
    assert.deepEqual(labels({}), []);
    assert.equal(timeline.section({}), '');
    assert.equal(timeline.section(null), '');
});

test('prepared row only when createdAt is persisted', () => {
    assert.deepEqual(labels({ createdAt: T0 }), ['Prepared']);
});

test('last-updated row is suppressed when it equals creation', () => {
    assert.deepEqual(labels({ createdAt: T0, updatedAt: T0 }), ['Prepared']);
});

test('last-updated row appears when it differs from creation', () => {
    assert.deepEqual(labels({ createdAt: T0, updatedAt: T1 }), ['Prepared', 'Last updated']);
});

test('approved row appears only with approvedAt', () => {
    assert.deepEqual(labels({ createdAt: T0, approvedAt: T1 }), ['Prepared', 'Approved for application']);
    assert.deepEqual(labels({ createdAt: T0 }), ['Prepared']);
});

test('rows are ordered by real event time, not by category', () => {
    // updatedAt deliberately later than approvedAt
    const app = { createdAt: T0, approvedAt: T1, updatedAt: T2 };
    assert.deepEqual(labels(app), ['Prepared', 'Approved for application', 'Last updated']);
    const times = timeline.rows(app).map(r => r.at.getTime());
    assert.deepEqual(times, [...times].sort((a, b) => a - b));
});

test('unparseable timestamps never become a row', () => {
    const app = { createdAt: T0, approvedAt: 'not-a-date', employerOpenedAt: '' };
    assert.deepEqual(labels(app), ['Prepared']);
});

test('simulated email send is labelled simulated, never mailed or delivered', () => {
    const [row] = timeline.rows({ createdAt: T0, emailSendAttemptedAt: T1, emailSendResult: 'SENT_SIMULATED' }).slice(1);
    assert.equal(row.label, 'Email send attempt');
    assert.match(row.detail, /simulated/i);
    assert.match(row.detail, /nothing was actually mailed/i);
    assert.doesNotMatch(row.detail, /delivered/i);
    assert.doesNotMatch(row.detail, /sent successfully/i);
});

test('real send outcome says the service reported it, not that mail arrived', () => {
    const rows = timeline.rows({ createdAt: T0, emailSendAttemptedAt: T1, emailSendResult: 'SENT' });
    const row = rows[1];
    assert.match(row.detail, /reported by email service/i);
    assert.doesNotMatch(row.detail, /delivered/i);
});

test('an unknown email outcome still records the attempt without claiming success', () => {
    const rows = timeline.rows({ createdAt: T0, emailSendAttemptedAt: T1, emailSendResult: 'FAILED' });
    assert.equal(rows[1].label, 'Email send attempt');
    assert.match(rows[1].detail, /attempt recorded/i);
});

test('employer-site opening never claims a submission, and keeps the real URL', () => {
    const url = 'https://www.arbeitnow.com/jobs/12345';
    const row = timeline.rows({ createdAt: T0, employerOpenedAt: T1, employerUrl: url })[1];
    assert.equal(row.label, 'Employer site opened');
    assert.equal(row.url, url);
    assert.match(row.detail, /not confirmed by this platform/i);
    const markup = timeline.html([row]);
    assert.match(markup, /href="https:\/\/www\.arbeitnow\.com\/jobs\/12345"/);
    assert.doesNotMatch(markup, /submitted/i);
});

test('an observed opening without a URL still renders, without inventing one', () => {
    const row = timeline.rows({ createdAt: T0, employerOpenedAt: T1 })[1];
    assert.equal(row.url, null);
    const markup = timeline.html([row]);
    assert.doesNotMatch(markup, /<a /);
    assert.match(markup, /not confirmed by this platform/i);
});

test('a hostile employer URL cannot inject markup into the timeline', () => {
    const row = timeline.rows({
        createdAt: T0,
        employerOpenedAt: T1,
        employerUrl: 'https://evil.example/"><script>alert(1)</script>'
    })[1];
    const markup = timeline.html([row]);
    assert.doesNotMatch(markup, /<script>/);
    assert.match(markup, /&quot;&gt;&lt;script&gt;/);
});

test('every row type renders a label, a time and a dot', () => {
    const app = {
        createdAt: T0, updatedAt: T1, approvedAt: T1,
        emailSendAttemptedAt: T2, emailSendResult: 'SENT_SIMULATED',
        employerOpenedAt: T3, employerUrl: 'https://www.arbeitnow.com/jobs/12345'
    };
    assert.deepEqual(labels(app), ['Prepared', 'Last updated', 'Approved for application',
        'Email send attempt', 'Employer site opened']);
    const markup = timeline.section(app);
    assert.match(markup, /id="reviewTimelineSection"/);
    assert.match(markup, /id="reviewTimeline"/);
    assert.equal((markup.match(/application-timeline-item/g) || []).length, 5);
    assert.equal((markup.match(/application-timeline-dot/g) || []).length, 5);
    assert.equal((markup.match(/<time /g) || []).length, 5);
});
