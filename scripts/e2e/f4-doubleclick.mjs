import { chromium } from 'playwright';
const BASE = 'http://127.0.0.1:8080';
const browser = await chromium.launch();
const ctx = await browser.newContext();
await ctx.addInitScript(() => localStorage.setItem('agentplatform:candidateId', '3'));
const page = await ctx.newPage();
const sends = [];
page.on('request', r => { if (r.url().includes('/email/send')) sends.push(r.postData()); });
await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('.application-card');
const approved = (await (await fetch(`${BASE}/api/v1/applications/candidate/3`)).json()).find(a => a.applicationStatus === 'APPROVED_FOR_APPLICATION');
await page.click(`.application-card[data-id="${approved.id}"] .application-view-btn`);
await page.waitForSelector('#reviewActionsSection [data-review-email]');
await page.click('#reviewActionsSection [data-review-email]');
await page.waitForSelector('[data-confirm-input]');
await page.fill('[data-confirm-input]', 'hiring@example.com');
// Triple-click the confirm button: a duplicate send must not be possible.
const t0 = Date.now();
await Promise.all([
  page.click('[data-confirm-accept]', { force: true }),
  page.click('[data-confirm-accept]', { force: true }),
  page.click('[data-confirm-accept]', { force: true })
]);
await page.waitForTimeout(2500);
const pending = await page.evaluate(() => {
  const b = document.querySelector('[data-review-email]');
  return b ? { disabled: b.disabled, label: b.textContent.trim() } : null;
});
console.log(JSON.stringify({ sends: sends.length, bodies: sends, pendingAfter: pending, elapsed: Date.now() - t0 }, null, 2));
await browser.close();
