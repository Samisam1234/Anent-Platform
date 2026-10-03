/**
 * Focused tests for renderApplicationAdvisor() in applications.js.
 * Run: node --test ui/src/test/js/applicationAdvisor.test.mjs
 *
 * applications.js is an IIFE that boots the whole page, so the function under test is
 * lifted out of the real source and run against a stub document. This exercises the
 * shipped code (not a reimplementation) and pins the advisor contract: the payload
 * carries applicationReadinessScore and jobMatchScore, and nothing else to show.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../../main/resources/static/applications.js', import.meta.url), 'utf8');

// The function closes on its own line at the IIFE's 4-space indent; inner blocks are deeper.
const fnSource = source.match(/function renderApplicationAdvisor\(data\) \{[\s\S]*?\n {4}\}/);
assert.ok(fnSource, 'renderApplicationAdvisor not found in applications.js');

function element() {
    return { textContent: '', innerHTML: '' };
}

/**
 * Runs the real function against a payload.
 * @returns {object} the stub elements it wrote to
 */
function render(payload) {
    const els = {
        applicationDetailRecommendation: element(),
        applicationDetailMatchScore: element(),
        applicationDetailStrengths: element(),
        applicationDetailGaps: element()
    };
    const recEl = els.applicationDetailRecommendation;
    recEl.parentElement = { insertAdjacentElement() {} };

    const sandbox = {
        document: {
            getElementById: id => els[id] || null,
            createElement: () => ({ className: '', innerHTML: '' })
        },
        esc: v => String(v),
        stepTitle: d => (d && d.title) || ''
    };
    vm.createContext(sandbox);
    vm.runInContext(fnSource[0] + '\nrenderApplicationAdvisor;', sandbox);
    sandbox.renderApplicationAdvisor(payload);
    return els;
}

test('renders both advisor scores when the payload is populated', () => {
    const text = render({ applicationReadinessScore: 72, jobMatchScore: 88 })
        .applicationDetailMatchScore.textContent;
    assert.match(text, /Readiness: 72\/100/);
    assert.match(text, /Job Match: 88\/100/);
});

test('never shows a Match Score segment: the advisor payload has no such field', () => {
    const text = render({ applicationReadinessScore: 72, jobMatchScore: 88 })
        .applicationDetailMatchScore.textContent;
    assert.doesNotMatch(text, /Match Score/);
});

test('a zero job match is rendered, not replaced by the missing-value placeholder', () => {
    const text = render({ applicationReadinessScore: 40, jobMatchScore: 0 })
        .applicationDetailMatchScore.textContent;
    assert.match(text, /Job Match: 0\/100/);
});

test('a missing job match falls back to a placeholder and never prints undefined', () => {
    const text = render({ applicationReadinessScore: 40 })
        .applicationDetailMatchScore.textContent;
    assert.match(text, /Job Match: —/);
    assert.doesNotMatch(text, /undefined|null|NaN/);
});

test('a missing readiness leaves the score line untouched instead of throwing', () => {
    const els = render({ jobMatchScore: 88 });
    assert.equal(els.applicationDetailMatchScore.textContent, '');
});

test('an empty payload throws nothing and still renders the recommendation', () => {
    const els = render(undefined);
    assert.equal(els.applicationDetailRecommendation.textContent, '—');
    assert.equal(els.applicationDetailMatchScore.textContent, '');
});

test('no ReferenceError: the function never touches an undefined `app` variable', () => {
    // `app` is not in scope here; any stray `app.` reference is the B1 crash.
    assert.doesNotMatch(fnSource[0], /\bapp\./);
    // A payload that also carries an application-shaped matchScore must not leak into the line.
    const text = render({ applicationReadinessScore: 55, jobMatchScore: 66, matchScore: 99 })
        .applicationDetailMatchScore.textContent;
    assert.doesNotMatch(text, /99|Match Score/);
});