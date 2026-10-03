// Probe: where do the application events actually render in the live UI?
// (candidate 3, application 2 = APPROVED_FOR_APPLICATION with approvedAt set)
import { chromium } from 'playwright'
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const DIR = join(tmpdir(), 'agent-platform-e2e', 'ui-transitions')
fs.mkdirSync(DIR, { recursive: true })

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
page.setDefaultTimeout(60000)
const consoleErrs = [], netFails = []
page.on('console', m => { if (m.type() === 'error') consoleErrs.push(m.text().slice(0, 160)) })
page.on('requestfailed', r => netFails.push({ url: r.url().slice(0, 100), err: r.failure() && r.failure().errorText }))
await page.addInitScript(([id, name]) => {
  localStorage.setItem('agentplatform:candidateId', String(id))
  localStorage.setItem('agentplatform:candidateName', name)
}, [3, (process.env.E2E_CANDIDATE_NAME || 'Jordan Sample')])

const out = {}
await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('#applicationsCardsGrid button.application-view-btn', { timeout: 120000 })
out.cardState = await page.evaluate(() => {
  const c = document.querySelector('#applicationsCardsGrid > *')
  return c ? c.innerText.replace(/\s+/g, ' ').slice(0, 160) : null
})
await page.click('#applicationsCardsGrid button.application-view-btn')
await page.waitForSelector('.ms-card', { timeout: 60000 })
await page.waitForTimeout(3000)

out.modal = await page.evaluate(() => {
  const m = document.querySelector('.ms-card')
  const timelineInModal = m.querySelectorAll('.application-timeline, #applicationDetailTimeline, #applicationDetailTimelineSection').length
  return { open: !!m, timelineElementsInsideModal: timelineInModal, footerButtons: Array.from(m.querySelectorAll('button')).map(b => b.textContent.trim()), bodyHasTimelineHeading: /timeline/i.test(m.innerText) }
})

out.legacySection = await page.evaluate(() => {
  const sec = document.getElementById('applicationDetailSection')
  const tl = document.getElementById('applicationDetailTimelineSection')
  const list = document.getElementById('applicationDetailTimeline')
  const vis = el => { if (!el) return 'missing'; const cs = getComputedStyle(el); const r = el.getBoundingClientRect(); return { hiddenAttr: el.hidden, display: cs.display, visibility: cs.visibility, box: `${Math.round(r.width)}x${Math.round(r.height)}@${Math.round(r.left)},${Math.round(r.top)}`, painted: r.width > 0 && r.height > 0 } }
  return {
    detailSection: vis(sec),
    timelineSection: vis(tl),
    rowCount: list ? list.children.length : null,
    rows: list ? Array.from(list.children).map(li => li.innerText.replace(/\s+/g, ' ').trim().slice(0, 90)) : null,
    buttonsInLegacySection: sec ? Array.from(sec.querySelectorAll('button')).map(b => ({ id: b.id, text: b.textContent.trim(), hidden: b.hidden })) : null
  }
})

// scroll down to see whether a legacy detail panel is painted below the list
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
await page.waitForTimeout(800)
await page.screenshot({ path: `${DIR}/12-timeline-probe-bottom-1440.png` })
out.afterScroll = await page.evaluate(() => ({ scrollY: window.scrollY, docHeight: document.body.scrollHeight }))

const measure = () => page.evaluate(() => {
  const de = document.documentElement, vw = de.clientWidth
  const offenders = Array.from(document.querySelectorAll('body *')).filter(el => { if (getComputedStyle(el).position === 'fixed') return false; const r = el.getBoundingClientRect(); return r.width > 0 && r.right > vw + 1 }).slice(0, 6).map(el => { const r = el.getBoundingClientRect(); return `${el.tagName}.${(el.className || '').toString().split(' ').filter(Boolean).slice(0, 2).join('.')} right=${Math.round(r.right)} w=${Math.round(r.width)} text=${(el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 40)}` })
  const m = document.querySelector('.ms-card')
  return { scrollWidth: de.scrollWidth, clientWidth: vw, overflow: de.scrollWidth - vw, offenders, modalInternalScrollX: m ? m.scrollWidth - m.clientWidth : null }
})
for (const w of [1440, 768, 390]) {
  await page.setViewportSize({ width: w, height: 900 })
  await page.waitForTimeout(1200)
  out['measure' + w] = await measure()
  await page.screenshot({ path: `${DIR}/13-detail-${w}.png` })
}

out.consoleErrs = consoleErrs
out.netFails = netFails
fs.writeFileSync(`${DIR}/timeline-probe.json`, JSON.stringify(out, null, 2))
console.log(JSON.stringify(out, null, 2))
await browser.close()
