// F4 follow-up evidence: per-width measurements with the action section open, and
// approval-time no-send proof. Real API, real browser, no interception, no fixtures.
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const BASE = 'http://127.0.0.1:8080';
const CANDIDATE_ID = 3;
const OUT = join(process.env.TEMP, 'f4-actions');
mkdirSync(OUT, { recursive: true });

const widths = [1440, 768, 390];
const evidence = { widths, checks: [], measurements: {}, consoleErrs: [], netFails: [], sendRequests: [] };
const check = (name, passed, detail) => evidence.checks.push({ name, passed, detail });
const api = async (p) => (await fetch(`${BASE}${p}`)).json();

const browser = await chromium.launch();
const ctx = await browser.newContext();
await ctx.addInitScript((id) => localStorage.setItem('agentplatform:candidateId', String(id)), CANDIDATE_ID);
const page = await ctx.newPage();
page.on('console', (m) => { if (m.type() === 'error') evidence.consoleErrs.push(m.text()); });
page.on('requestfailed', (r) => evidence.netFails.push(`${r.method()} ${r.url()} :: ${r.failure()?.errorText}`));
page.on('request', (r) => {
  if (/\/applications\/email\/send/.test(r.url())) evidence.sendRequests.push({ at: new Date().toISOString(), body: r.postData() });
});

const list = await api(`/api/v1/applications/candidate/${CANDIDATE_ID}`);
const approved = list.find((a) => a.applicationStatus === 'APPROVED_FOR_APPLICATION');
check('live APPROVED application available for the width sweep', !!approved, approved ? `id=${approved.id}` : 'none found');

await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('.application-card');

// ---- A. approval must never send an email ----
const jobs = await (await fetch(`${BASE}/api/v1/jobs/search`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}'
})).json();
const job = jobs.jobs[0];
let freshId = list.find((a) => a.applicationStatus === 'GENERATED' && a.candidateId === CANDIDATE_ID)?.id;
const created = await (await fetch(`${BASE}/api/v1/applications/prepare`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ candidateId: CANDIDATE_ID, jobId: job.id })
})).json();
// Always prepare a dedicated package for this check, so the GENERATED package used by the
// ineligible-status gating evidence stays GENERATED and the run stays repeatable.
freshId = created.applicationId;
check('dedicated GENERATED package created through the real prepare endpoint', !!freshId, `id=${freshId} job=${job.id} existingGeneratedKept=${list.find((a) => a.applicationStatus === 'GENERATED')?.id}`);

await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForSelector(`.application-card[data-id="${freshId}"]`);
const beforeState = await api(`/api/v1/applications/${freshId}`);
check('the package really starts GENERATED (card chip reads "Prepared")', beforeState.applicationStatus === 'GENERATED' && !beforeState.approvedAt, `${beforeState.applicationStatus} approvedAt=${beforeState.approvedAt}`);
await page.click(`.application-card[data-id="${freshId}"] .application-view-btn`);
await page.waitForSelector('.ms-card');
const beforeApproval = await page.evaluate(() => ({
  actions: !!document.querySelector('#reviewActionsSection'),
  emailBtn: !!document.querySelector('[data-review-email]'),
  applyBtn: !!document.querySelector('[data-review-apply]')
}));
check('GENERATED modal shows no email/apply action before approval', !beforeApproval.actions && !beforeApproval.emailBtn && !beforeApproval.applyBtn, JSON.stringify(beforeApproval));
await page.screenshot({ path: join(OUT, 'f4-widths-generated-1440.png'), fullPage: false });
await page.click('#msClose');
await page.waitForTimeout(400);

const sendsBeforeApprove = evidence.sendRequests.length;
await page.click(`.application-card[data-id="${freshId}"] .application-approve-btn`);
await page.waitForSelector('.ms-overlay [data-confirm-accept]');
const confirmText = await page.evaluate(() => (document.querySelector('.confirm-message')?.textContent || '').trim());
check('approval asks for explicit confirmation in the shared shell', /approve/i.test(confirmText), confirmText.slice(0, 120));
await page.click('.ms-overlay [data-confirm-accept]');
await page.waitForTimeout(2500);
const afterApproval = await (await fetch(`${BASE}/api/v1/applications/${freshId}`)).json();
check('approval flips the real package to APPROVED_FOR_APPLICATION', afterApproval.applicationStatus === 'APPROVED_FOR_APPLICATION', `${afterApproval.applicationStatus} approvedAt=${afterApproval.approvedAt}`);
check('approval sent NO email (no /email/send request)', evidence.sendRequests.length === sendsBeforeApprove, `requests=${evidence.sendRequests.length}`);
check('approval left email fields untouched', !afterApproval.emailSendAttemptedAt && !afterApproval.emailSendResult, `attemptedAt=${afterApproval.emailSendAttemptedAt} result=${afterApproval.emailSendResult}`);

await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForSelector(`.application-card[data-id="${freshId}"]`);
await page.click(`.application-card[data-id="${freshId}"] .application-view-btn`);
await page.waitForSelector('#reviewActionsSection [data-review-email]');
const afterApprovalActions = await page.evaluate(() => ({
  email: !!document.querySelector('[data-review-email]'),
  apply: !!document.querySelector('[data-review-apply]'),
  note: (document.querySelector('#reviewActionsSection .agent-note')?.textContent || '').replace(/\s+/g, ' ').trim()
}));
check('the newly approved modal exposes email + apply', afterApprovalActions.email && afterApprovalActions.apply, JSON.stringify(afterApprovalActions));
check('still no email request after opening the approved modal', evidence.sendRequests.length === sendsBeforeApprove, `requests=${evidence.sendRequests.length}`);
await page.click('#msClose');
await page.waitForTimeout(300);

// ---- B. per-width measurements with the action section open ----
for (const width of widths) {
  await page.setViewportSize({ width, height: width === 390 ? 844 : 900 });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForSelector(`.application-card[data-id="${approved.id}"]`);
  await page.click(`.application-card[data-id="${approved.id}"] .application-view-btn`);
  await page.waitForSelector('#reviewActionsSection [data-review-email]');
  await page.waitForTimeout(350);
  evidence.measurements[width] = await page.evaluate(() => {
    const card = document.querySelector('.ms-card');
    const row = document.querySelector('.review-action-row');
    const rect = card.getBoundingClientRect();
    return {
      viewport: window.innerWidth,
      pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      modalLeft: Math.round(rect.left),
      modalRight: Math.round(rect.right),
      modalInternalOverflow: card.scrollWidth - card.clientWidth,
      modalInternalHorizontalScroll: card.scrollWidth > card.clientWidth,
      actionRowWrapped: row ? row.scrollWidth > row.clientWidth : null,
      actionButtons: [...document.querySelectorAll('#reviewActionsSection button')].map((b) => b.textContent.trim()),
      emailDisabled: document.querySelector('[data-review-email]').disabled,
      applyDisabled: document.querySelector('[data-review-apply]').disabled
    };
  });
  const m = evidence.measurements[width];
  check(`width ${width}: no page horizontal overflow`, m.pageOverflow === 0, `overflow=${m.pageOverflow}px`);
  check(`width ${width}: modal fits the viewport`, m.modalLeft >= 0 && m.modalRight <= width, `left=${m.modalLeft} right=${m.modalRight}`);
  check(`width ${width}: no internal modal horizontal scroll`, m.modalInternalOverflow === 0 && !m.modalInternalHorizontalScroll, `overflow=${m.modalInternalOverflow}px`);
  check(`width ${width}: action row fits without clipping`, m.actionRowWrapped === false, `wrapped=${m.actionRowWrapped} buttons=${JSON.stringify(m.actionButtons)}`);
  await page.screenshot({ path: join(OUT, `f4-actions-${width}.png`), fullPage: false });
  await page.click('#msClose');
  await page.waitForTimeout(250);
}

check('no console errors', evidence.consoleErrs.length === 0, evidence.consoleErrs.join(' | ') || 'none');
check('no failed requests', evidence.netFails.length === 0, evidence.netFails.join(' | ') || 'none');
check('no email was ever sent during this run', evidence.sendRequests.length === 0, JSON.stringify(evidence.sendRequests));

evidence.passed = evidence.checks.filter((c) => c.passed).length;
evidence.total = evidence.checks.length;
evidence.freshAppId = freshId;
evidence.approvedAppId = approved.id;
writeFileSync(join(OUT, 'f4-widths.json'), JSON.stringify(evidence, null, 2));

console.log(`${evidence.passed}/${evidence.total} checks passed`);
evidence.checks.filter((c) => !c.passed).forEach((c) => console.log(`  FAIL ${c.name} :: ${c.detail}`));
console.log('measurements:', JSON.stringify(evidence.measurements, null, 2));
console.log('fresh app id:', freshId, '| approved app id:', approved.id);
await browser.close();
process.exit(evidence.passed === evidence.total ? 0 : 1);
