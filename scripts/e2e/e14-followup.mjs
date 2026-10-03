// Focused follow-up: (1) matches active-summary chip measured at 390 with live text,
// (2) before/after screenshots of the D1 defect on the same live page.
import { chromium } from 'playwright'
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const DIR = join(tmpdir(), 'agent-platform-e2e', 'e14-after')
const CANDIDATE_ID = 3
const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 390, height: 900 } })
const page = await ctx.newPage()
page.setDefaultTimeout(60000)
const consoleErrs = [], netFails = []
page.on('console', m => { if (m.type() === 'error') consoleErrs.push(m.text().slice(0, 160)) })
page.on('requestfailed', r => netFails.push({ url: r.url().slice(0, 100), err: r.failure() && r.failure().errorText }))
await page.addInitScript(([id, name]) => {
  localStorage.setItem('agentplatform:candidateId', String(id))
  localStorage.setItem('agentplatform:candidateName', name)
}, [CANDIDATE_ID, (process.env.E2E_CANDIDATE_NAME || 'Jordan Sample')])

const measure = () => page.evaluate(() => {
  const de = document.documentElement
  const vw = de.clientWidth
  const offenders = Array.from(document.querySelectorAll('body *')).filter(el => {
    if (getComputedStyle(el).position === 'fixed') return false
    const r = el.getBoundingClientRect()
    return r.width > 0 && r.right > vw + 1
  }).slice(0, 6).map(el => { const r = el.getBoundingClientRect(); return `${el.tagName}.${(el.className || '').toString().split(' ').filter(Boolean).join('.')} right=${Math.round(r.right)} w=${Math.round(r.width)} text=${(el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 50)}` })
  return { scrollWidth: de.scrollWidth, clientWidth: vw, overflow: de.scrollWidth - vw, offenders }
})
const PRE = '.results-header{flex-wrap:nowrap!important}.active-query-summary{min-width:auto!important;max-width:none!important}.summary-chip{white-space:nowrap!important;overflow-wrap:normal!important;max-width:none!important}'
const POST = '.results-header{flex-wrap:wrap!important}.active-query-summary{min-width:0!important;max-width:100%!important}.summary-chip{white-space:normal!important;overflow-wrap:anywhere!important;max-width:100%!important}'

const out = {}

// (1) matches active-summary chip at 390 with live profile text
await page.goto(`${BASE}/matches.html`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('#matchesActiveSummary .summary-chip', { timeout: 240000 })
await page.waitForTimeout(1200)
out.matches390 = await page.evaluate(() => {
  const el = document.getElementById('matchesActiveSummary')
  const c = el.querySelector('.summary-chip')
  const r = c.getBoundingClientRect()
  return { chips: el.querySelectorAll('.summary-chip').length, chipText: c.textContent.trim(), whiteSpace: getComputedStyle(c).whiteSpace, chipRight: Math.round(r.right), chipWidth: Math.round(r.width), chipHeight: Math.round(r.height), viewport: document.documentElement.clientWidth }
})
out.matches390Measure = await measure()
await page.screenshot({ path: `${DIR}/matches-active-summary-390.png` })

// (2) before/after on the same live jobs page
await page.goto(`${BASE}/jobs.html`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('#jobsActiveSummary .summary-chip', { timeout: 240000 })
await page.waitForTimeout(800)
await page.addStyleTag({ content: PRE })
out.before = await measure()
await page.screenshot({ path: `${DIR}/d1-BEFORE-jobs-390.png` })
await page.addStyleTag({ content: POST })
out.after = await measure()
await page.screenshot({ path: `${DIR}/d1-AFTER-jobs-390.png` })

out.consoleErrs = consoleErrs
out.netFails = netFails
fs.writeFileSync(`${DIR}/e14-followup.json`, JSON.stringify(out, null, 2))
console.log(JSON.stringify(out, null, 2))
await browser.close()
