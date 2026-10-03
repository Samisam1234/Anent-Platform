import { chromium } from 'playwright';
const BASE = 'http://127.0.0.1:8080';
const browser = await chromium.launch();
const ctx = await browser.newContext();
await ctx.addInitScript(() => localStorage.setItem('agentplatform:candidateId', '3'));
const page = await ctx.newPage();
const handoffs = [];
page.on('request', r => { if (/handoff/.test(r.url())) handoffs.push(r.postData()); });
await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('.application-card');
const app = (await (await fetch(`${BASE}/api/v1/applications/candidate/3`)).json()).find(a => a.applicationStatus === 'APPROVED_FOR_APPLICATION');
await page.click(`.application-card[data-id="${app.id}"] .application-view-btn`);
await page.waitForSelector('#reviewActionsSection [data-review-apply]');
await page.click('#reviewActionsSection [data-review-apply]');
await page.waitForSelector('.apply-kit');
await page.waitForTimeout(600);
const kit = await page.evaluate(() => ({
  kicker: document.querySelector('#msKicker')?.textContent?.trim(),
  state: document.querySelector('.apply-kit')?.dataset.applyState,
  title: document.querySelector('.apply-kit-state-title')?.textContent?.trim(),
  body: document.querySelector('.apply-kit-state p')?.textContent?.replace(/\s+/g,' ').trim(),
  note: document.querySelector('.apply-kit .agent-note')?.textContent?.replace(/\s+/g,' ').trim(),
  handoffBtn: !!document.querySelector('#applyKitHandoffBtn'),
  kitFields: document.querySelectorAll('[data-kit-field]').length,
  footerButtons: [...document.querySelectorAll('.ms-footer button')].map(b => b.textContent.trim())
}));
console.log(JSON.stringify({ kit, handoffs: handoffs.length }, null, 2));
await browser.close();
