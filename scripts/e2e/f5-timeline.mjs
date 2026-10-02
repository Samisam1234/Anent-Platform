// F5 live acceptance: the application event timeline in the VISIBLE review modal.
// Real app, real persisted events, no interception, no fixtures.
// usage: node f5-timeline.mjs <tag> <candidateId>
import { chromium } from 'playwright'
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const TAG = process.argv[2] || 'run'
const CANDIDATE_ID = Number(process.argv[3] || 3)
const DIR = join(tmpdir(), 'agent-platform-e2e', `f5-${TAG}`)
fs.mkdirSync(DIR, { recursive: true })

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
page.setDefaultTimeout(60000)
const consoleErrs = [], netFails = []
page.on('console', m => { if (m.type() === 'error') consoleErrs.push({ url: page.url(), text: m.text().slice(0, 200) }) })
page.on('requestfailed', r => netFails.push({ url: r.url().slice(0, 110), resource: r.resourceType(), err: r.failure() && r.failure().errorText }))

await page.addInitScript(([id]) => localStorage.setItem('agentplatform:candidateId', String(id)), [CANDIDATE_ID])

// exact server state used, read straight from the API
const appState = await (await fetch(`${BASE}/api/v1/applications/candidate/${CANDIDATE_ID}`)).json().then(l => l[0])

await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('#applicationsCardsGrid button.application-view-btn', { timeout: 120000 })
await page.click('#applicationsCardsGrid button.application-view-btn')
await page.waitForSelector('.ms-card', { timeout: 60000 })
await page.waitForTimeout(2000)

const readTimeline = () => page.evaluate(() => {
  const modal = document.querySelector('.ms-card')
  const section = document.getElementById('reviewTimelineSection')
  const rows = Array.from(document.querySelectorAll('#reviewTimeline .application-timeline-item')).map(li => ({
    label: (li.querySelector('.application-timeline-label') || {}).textContent,
    when: (li.querySelector('.application-timeline-when') || {}).textContent,
    detail: (li.querySelector('.application-timeline-detail') || {}).textContent?.trim() || null,
    href: (li.querySelector('.application-timeline-detail a') || {}).getAttribute?.('href') || null,
  }))
  const cs = section ? getComputedStyle(section) : null
  const r = section ? section.getBoundingClientRect() : null
  return {
    sectionInsideModal: !!(section && modal && modal.contains(section)),
    sectionPainted: !!(section && r && r.width > 0 && r.height > 0 && cs.display !== 'none' && cs.visibility !== 'hidden'),
    headingText: section ? section.querySelector('.modal-section-title').textContent.trim() : null,
    rowCount: rows.length,
    rows,
    legacyTimelineNodesInPage: document.querySelectorAll('#applicationDetailTimeline, #applicationDetailTimelineSection').length,
    modalHeadingOrder: Array.from(modal.querySelectorAll('.modal-section-title')).map(h => h.textContent.trim()),
    disclaimerPresent: /has not submitted it and cannot submit it/i.test(modal.innerText),
  }
})

const measure = () => page.evaluate(() => {
  const de = document.documentElement, vw = de.clientWidth
  const offenders = Array.from(document.querySelectorAll('body *')).filter(el => {
    if (getComputedStyle(el).position === 'fixed') return false
    const r = el.getBoundingClientRect()
    return r.width > 0 && r.right > vw + 1
  }).slice(0, 6).map(el => { const r = el.getBoundingClientRect(); return `${el.tagName}.${(el.className || '').toString().split(' ').filter(Boolean).slice(0, 2).join('.')} right=${Math.round(r.right)} w=${Math.round(r.width)} text=${(el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 40)}` })
  const m = document.querySelector('.ms-card')
  const tl = document.getElementById('reviewTimeline')
  return {
    scrollWidth: de.scrollWidth, clientWidth: vw, overflow: de.scrollWidth - vw, offenders,
    modalRight: m ? Math.round(m.getBoundingClientRect().right) : null,
    modalInternalScrollX: m ? m.scrollWidth - m.clientWidth : null,
    timelineOverflowX: tl ? tl.scrollWidth - tl.clientWidth : null,
  }
})

const out = { tag: TAG, candidateId: CANDIDATE_ID, appState: {
  id: appState.id, status: appState.applicationStatus, createdAt: appState.createdAt, updatedAt: appState.updatedAt,
  approvedAt: appState.approvedAt, emailSendAttemptedAt: appState.emailSendAttemptedAt,
  emailSendResult: appState.emailSendResult, employerOpenedAt: appState.employerOpenedAt, employerUrl: appState.employerUrl } }

out.timeline = await readTimeline()
out.measurements = {}
for (const w of [1440, 768, 390]) {
  await page.setViewportSize({ width: w, height: 900 })
  await page.waitForTimeout(1200)
  out.measurements['w' + w] = await measure()
  await page.screenshot({ path: `${DIR}/timeline-${w}.png` })
}

// expected order computed from the server state, compared with what the DOM shows
const ev = [
  { label: 'Prepared', at: appState.createdAt },
  { label: 'Last updated', at: appState.updatedAt },
  { label: 'Approved for application', at: appState.approvedAt },
  { label: 'Email send attempt', at: appState.emailSendAttemptedAt },
  { label: 'Employer site opened', at: appState.employerOpenedAt },
].filter(e => e.at && (!e.label.includes('updated') || new Date(e.at).getTime() !== new Date(appState.createdAt).getTime()))
  .sort((a, b) => new Date(a.at) - new Date(b.at))
out.expectedOrderFromServer = ev.map(e => e.label)
out.orderMatchesServer = JSON.stringify(out.expectedOrderFromServer) === JSON.stringify(out.timeline.rows.map(r => r.label))

out.consoleErrs = consoleErrs
out.netFails = netFails
fs.writeFileSync(`${DIR}/f5-timeline.json`, JSON.stringify(out, null, 2))
console.log(JSON.stringify(out, null, 2))
await browser.close()
