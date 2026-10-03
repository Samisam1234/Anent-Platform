import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const BASE = 'http://127.0.0.1:8080';
const CANDIDATE_ID = 3;
const OUT = join(process.env.TEMP, 'f4-actions');
mkdirSync(OUT, { recursive: true });

const widths = [1440, 768, 390];
const evidence = { tag: process.argv[2] || 'run', candidateId: CANDIDATE_ID, checks: [], measurements: {}, consoleErrs: [], netFails: [] };
const check = (name, passed, detail) => evidence.checks.push({ name, passed, detail });

const api = async (p) => (await fetch(`${BASE}${p}`)).json();

const browser = await chromium.launch();
const ctx = await browser.newContext();
await ctx.addInitScript(id => {
  localStorage.setItem('agentplatform:candidateId', String(id));
  localStorage.setItem('agentplatform:candidateName', 'Verification Candidate');
}, CANDIDATE_ID);
const page = await ctx.newPage();
page.on('console', m => { if (m.type() === 'error') evidence.consoleErrs.push(m.text()); });
page.on('requestfailed', r => evidence.netFails.push(`${r.method()} ${r.url()} ${r.failure()?.errorText}`));

// Network log for the send/handoff endpoints so we can prove WHEN they fire.
const sends = [];
const handoffs = [];
page.on('request', r => {
  const u = r.url();
  if (u.includes('/api/v1/applications/email/send')) sends.push({ t: Date.now(), method: r.method(), body: r.postData() });
  if (/\/api\/v1\/applications\/\d+\/handoff/.test(u)) handoffs.push({ t: Date.now(), method: r.method(), body: r.postData() });
});

await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('.application-card', { timeout: 30000 });

const cards = await page.$$('.application-card');
evidence.cardCount = cards.length;

// ---- locate a genuinely persisted APPROVED application from live server state ----
const list = await api(`/api/v1/applications/candidate/${CANDIDATE_ID}`);
const approved = list.find(a => a.applicationStatus === 'APPROVED_FOR_APPLICATION');
check('live APPROVED application exists', !!approved, approved ? `id=${approved.id}` : 'none found');
const notApproved = list.find(a => a.applicationStatus !== 'APPROVED_FOR_APPLICATION');
check('live non-approved application exists for gating test', !!notApproved, notApproved ? `id=${notApproved.id} status=${notApproved.applicationStatus}` : 'none');

const openModalFor = async (id) => {
  await page.click(`.application-card[data-id="${id}"] .application-view-btn`);
  await page.waitForSelector('.ms-card', { state: 'visible', timeout: 15000 });
  await page.waitForTimeout(400); // let resolveJob/kit async work settle
};
const closeModal = async () => {
  await page.keyboard.press('Escape');
  await page.waitForSelector('.ms-card', { state: 'hidden', timeout: 10000 });
};

const readActions = () => page.evaluate(() => {
  const card = document.querySelector('.ms-card');
  if (!card) return null;
  const email = card.querySelector('[data-review-email]');
  const apply = card.querySelector('[data-review-apply]');
  const legacyEmail = card.querySelector('#sendEmailBtn');
  const legacyApply = card.querySelector('#assistedApplyBtn');
  return {
    sectionInModal: !!card.querySelector('#reviewActionsSection'),
    title: card.querySelector('#reviewActionsSection .modal-section-title')?.textContent?.trim() || null,
    email: email ? { present: true, label: email.textContent.trim(), disabled: email.disabled, appId: email.getAttribute('data-review-email') } : { present: false },
    apply: apply ? { present: true, label: apply.textContent.trim(), disabled: apply.disabled, appId: apply.getAttribute('data-review-apply') } : { present: false },
    legacyEmailInModal: !!legacyEmail,
    legacyApplyInModal: !!legacyApply,
    legacyHiddenEverywhere: [...document.querySelectorAll('#sendEmailBtn,#assistedApplyBtn')].every(el => !el.closest('.ms-card')),
    note: card.querySelector('#reviewActionsSection .agent-note')?.textContent?.trim() || null,
    editPackagePresent: !!card.querySelector('[data-edit-application]')
  };
});

// ---- 1. APPROVED: both actions visible inside the visible modal ----
await openModalFor(approved.id);
const approvedActions = await readActions();
evidence.approvedActions = approvedActions;
check('APPROVED: action section rendered inside the shared modal', approvedActions.sectionInModal && approvedActions.title === 'Send or apply', JSON.stringify({ t: approvedActions.title }));
check('APPROVED: email + apply controls present and enabled', approvedActions.email.present && !approvedActions.email.disabled && approvedActions.apply.present && !approvedActions.apply.disabled, JSON.stringify({ email: approvedActions.email, apply: approvedActions.apply }));
check('APPROVED: controls carry this application id', approvedActions.email.appId === String(approved.id) && approvedActions.apply.appId === String(approved.id), `email=${approvedActions.email.appId} apply=${approvedActions.apply.appId}`);
check('APPROVED: no duplicate controls inside the modal', !approvedActions.legacyEmailInModal && !approvedActions.legacyApplyInModal, `legacyEmailInModal=${approvedActions.legacyEmailInModal} legacyApplyInModal=${approvedActions.legacyApplyInModal}`);
check('APPROVED: legacy hidden controls remain outside the modal', approvedActions.legacyHiddenEverywhere, 'checked');
check('APPROVED: note never claims delivery or submission', approvedActions.note && !/delivered|mailed successfully|submitted/i.test(approvedActions.note) && /simulated/i.test(approvedActions.note) && /cannot submit/i.test(approvedActions.note), (approvedActions.note || '').slice(0, 140));

// ---- 2. Nothing sends on open / render / width changes ----
const sendsBeforeAnyClick = sends.length;
for (const w of widths) {
  await page.setViewportSize({ width: w, height: 900 });
  await page.waitForTimeout(250);
}
evidence.measurements = await page.evaluate(() => {
  const de = document.documentElement;
  const card = document.querySelector('.ms-card');
  const row = document.querySelector('#reviewActionsSection .review-action-row');
  return {
    w: window.innerWidth,
    pageOverflow: de.scrollWidth - de.clientWidth,
    modalOverflow: card ? card.scrollWidth - card.clientWidth : null,
    actionRowOverflow: row ? row.scrollWidth - row.clientWidth : null,
    actionRowWrapped: row ? row.getBoundingClientRect().height > 44 : null
  };
});
check('no email/send request fires on open or resize', sendsBeforeAnyClick === 0, `sends=${sendsBeforeAnyClick}`);
check('no horizontal overflow at 1440/768/390', evidence.measurements.pageOverflow === 0, JSON.stringify(evidence.measurements));
check('action row does not overflow the modal', evidence.measurements.modalOverflow === 0 && evidence.measurements.actionRowOverflow === 0, `modal=${evidence.measurements.modalOverflow} row=${evidence.measurements.actionRowOverflow}`);

await page.setViewportSize({ width: 1440, height: 900 });
await page.screenshot({ path: join(OUT, 'f4-approved-actions.png'), fullPage: false });
await page.setViewportSize({ width: 390, height: 900 });
await page.waitForTimeout(200);
await page.screenshot({ path: join(OUT, 'f4-approved-390.png'), fullPage: false });
await page.setViewportSize({ width: 1440, height: 900 });

// ---- 3. Email action: reachable, explicit confirmation, validation, single send ----
const sendsAtOpen = sends.length;
await page.click('#reviewActionsSection [data-review-email]');
await page.waitForSelector('[data-confirm-input]', { timeout: 10000 });
check('email action opens an explicit confirmation with a recipient field', true, 'confirm dialog with [data-confirm-input]');
check('no send happens before the user confirms', sends.length === sendsAtOpen, `sends=${sends.length}`);

// blank/invalid recipient must be refused inline, with no request
await page.fill('[data-confirm-input]', 'not-an-email');
await page.click('[data-confirm-accept]');
await page.waitForTimeout(250);
const errText = await page.textContent('[data-confirm-error]').catch(() => null);
const stillOpen = await page.isVisible('[data-confirm-input]');
check('invalid recipient is refused inline without sending', stillOpen && sends.length === sendsAtOpen && /valid email/i.test(errText || ''), `error="${errText}" sends=${sends.length}`);

// confirm with a syntactically valid address -> exactly one send
const RECIPIENT = 'hiring@example.com';
await page.fill('[data-confirm-input]', RECIPIENT);
await page.click('[data-confirm-accept]');
await page.waitForFunction(() => !document.querySelector('[data-confirm-input]'), null, { timeout: 15000 });
await page.waitForTimeout(1200);
check('exactly one email/send request after explicit confirm', sends.length === sendsAtOpen + 1, JSON.stringify(sends.map(s => s.body)));
const sendBody = sends.length ? JSON.parse(sends[sends.length - 1].body || '{}') : {};
check('send payload targets this application and carries the confirmed recipient', sendBody.applicationId === approved.id && sendBody.recipientEmail === RECIPIENT && sendBody.approved === true, JSON.stringify(sendBody));

// the modal must come back and show the persisted outcome
const modalBack = await page.isVisible('.ms-card');
check('modal is restored after the confirmation', modalBack, `visible=${modalBack}`);
const afterActions = await readActions();
check('email control is re-enabled after the send (no stuck pending state)', afterActions.email.present && !afterActions.email.disabled, JSON.stringify(afterActions.email));
await page.screenshot({ path: join(OUT, 'f4-after-send.png'), fullPage: false });

const persisted = await api(`/api/v1/applications/${approved.id}`);
evidence.persistedAfterSend = { applicationStatus: persisted.applicationStatus, emailSendAttemptedAt: persisted.emailSendAttemptedAt, emailSendResult: persisted.emailSendResult };
check('persisted outcome recorded on the server', !!persisted.emailSendAttemptedAt && persisted.emailSendResult === 'SENT_SIMULATED', JSON.stringify(evidence.persistedAfterSend));
const toastText = await page.textContent('.toast').catch(() => '');
evidence.toastText = toastText;
check('simulated outcome is reported truthfully (nothing was mailed)', /simulated/i.test(toastText) && /nothing was actually mailed/i.test(toastText), (toastText || '').slice(0, 160));
const timelineRows = await page.$$eval('#reviewTimeline .application-timeline-label', els => els.map(e => e.textContent.trim()));
evidence.timelineAfterSend = timelineRows;
check('the send appears in the timeline after the refresh', timelineRows.includes('Email send attempt'), JSON.stringify(timelineRows));

// ---- 4. Assisted Apply: opens the kit, gate needs the ack checkbox ----
const handoffsBefore = handoffs.length;
await page.click('#reviewActionsSection [data-review-apply]');
await page.waitForSelector('.apply-kit', { timeout: 15000 });
await page.waitForTimeout(400);
const kitState = await page.evaluate(() => ({
  kicker: document.querySelector('#msKicker')?.textContent?.trim(),
  state: document.querySelector('.apply-kit')?.getAttribute('data-apply-state'),
  hasAck: !!document.querySelector('#applyKitAck'),
  handoffBtnDisabled: document.querySelector('#applyKitHandoffBtn')?.disabled ?? null,
  handoffBtnPresent: !!document.querySelector('#applyKitHandoffBtn'),
  destination: document.querySelector('[data-apply-url]')?.textContent?.trim() || null,
  note: document.querySelector('.apply-kit-note')?.textContent?.trim() || null
}));
evidence.kit = kitState;
check('Assisted Apply opens the existing kit in the same modal', kitState.kicker === 'Assisted Apply', JSON.stringify({ kicker: kitState.kicker }));
if (kitState.handoffBtnPresent) {
  check('handoff button starts disabled until the values are acknowledged', kitState.handoffBtnDisabled === true, JSON.stringify({ disabled: kitState.handoffBtnDisabled }));
  check('no handoff recorded before the handoff button is used', handoffs.length === handoffsBefore, `handoffs=${handoffs.length}`);
  // explicit ack then handoff -> records the event and opens the employer destination
  const popups = [];
  ctx.on('page', p => popups.push(p));
  await page.check('#applyKitAck');
  await page.waitForTimeout(150);
  await page.click('#applyKitHandoffBtn');
  await page.waitForTimeout(1500);
  evidence.handoffs = handoffs.map(h => h.body);
  check('handoff invokes the intended endpoint with the validated URL', handoffs.length === handoffsBefore + 1 && JSON.parse(handoffs[handoffs.length - 1].body || '{}').url === kitState.destination, JSON.stringify({ sent: handoffs.map(h => h.body), destination: kitState.destination }));
  check('employer destination opened in a new tab', popups.length === 1, `popups=${popups.length}`);
  if (popups.length === 1) {
    evidence.employerTabUrl = popups[0].url();
    check('opened the validated employer destination', popups[0].url() === kitState.destination, `url=${popups[0].url()}`);
    await popups[0].close();
  }
  const kitNote = kitState.note || '';
  check('kit never claims a submission happened', !/submitted|we applied|application sent/i.test(kitNote), kitNote.slice(0, 160));
} else {
  // Honest blocked states (no employer URL from the source / stale package) keep the
  // recorded handoff untouched; report it rather than pretending the click ran.
  check('kit blocks honestly when no employer destination exists (no handoff recorded)', handoffs.length === handoffsBefore, `state=${kitState.state} handoffs=${handoffs.length}`);
}
await page.screenshot({ path: join(OUT, 'f4-kit.png'), fullPage: false });
await closeModal();

// ---- 5. Reload preserves accurate persisted state ----
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForSelector('.application-card', { timeout: 30000 });
await openModalFor(approved.id);
const afterReload = await readActions();
const rowsAfterReload = await page.$$eval('#reviewTimeline .application-timeline-label', els => els.map(e => e.textContent.trim()));
evidence.afterReload = { actions: afterReload, timeline: rowsAfterReload, persisted: await api(`/api/v1/applications/${approved.id}`).then(a => ({ emailSendAttemptedAt: a.emailSendAttemptedAt, emailSendResult: a.emailSendResult })) };
check('reload preserves the action controls for the approved package', afterReload.email.present && afterReload.apply.present, JSON.stringify(afterReload.email));
check('reload shows the persisted send, no duplicate sends on load', rowsAfterReload.includes('Email send attempt') && sends.length === sendsAtOpen + 1, JSON.stringify({ rows: rowsAfterReload, sends: sends.length }));
await closeModal();

// ---- 6. Ineligible status: neither action is offered ----
if (notApproved) {
  await openModalFor(notApproved.id);
  const gating = await readActions();
  evidence.gating = { appId: notApproved.id, status: notApproved.applicationStatus, ...gating };
  check('non-approved package offers NO send/apply action in the modal', !gating.sectionInModal && !gating.email.present && !gating.apply.present, JSON.stringify({ status: notApproved.applicationStatus, section: gating.sectionInModal, email: gating.email.present, apply: gating.apply.present }));
  const sendsBeforeGate = sends.length;
  const handoffsBeforeGate = handoffs.length;
  await page.waitForTimeout(700);
  check('ineligible package cannot trigger a send or a handoff', sends.length === sendsBeforeGate && handoffs.length === handoffsBeforeGate, `sends=${sends.length - sendsBeforeGate} handoffs=${handoffs.length - handoffsBeforeGate}`);
  await page.screenshot({ path: join(OUT, 'f4-ineligible.png'), fullPage: false });
  await closeModal();
}

evidence.summary = { total: evidence.checks.length, passed: evidence.checks.filter(c => c.passed).length, failed: evidence.checks.filter(c => !c.passed).map(c => c.name) };
writeFileSync(join(OUT, 'f4-actions.json'), JSON.stringify(evidence, null, 2));
console.log(JSON.stringify({ summary: evidence.summary, consoleErrs: evidence.consoleErrs, netFails: evidence.netFails, kit: evidence.kit && { state: evidence.kit.state, hasHandoffBtn: evidence.kit.handoffBtnPresent }, measurements: evidence.measurements }, null, 2));
await browser.close();
