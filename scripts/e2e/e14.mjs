// E14 live acceptance: 6 pages x 3 widths with REAL data (no interception, no fixtures).
// Records offenders, screenshots, console errors and failed requests.
import { chromium } from 'playwright'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const SHOTS = join(tmpdir(), 'agent-platform-e2e', 'e14-' + (process.argv[2] || 'run'))
const CANDIDATE_ID = process.argv[3] ? Number(process.argv[3]) : null
const CANDIDATE_NAME = (process.env.E2E_CANDIDATE_NAME || 'Jordan Sample')
fs.mkdirSync(SHOTS, { recursive: true })

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
page.setDefaultTimeout(60000)
page.setDefaultNavigationTimeout(60000)

const consoleErrs = [], netFails = [], apiCalls = []
page.on('console', m => { if (m.type() === 'error') consoleErrs.push({ url: page.url(), text: m.text().slice(0, 200) }) })
page.on('requestfailed', r => { const f = r.failure(); netFails.push({ url: r.url().slice(0, 110), resource: r.resourceType(), err: f && f.errorText }) })
page.on('response', r => { if (r.url().includes('/api/')) apiCalls.push({ url: r.url().replace(BASE, ''), status: r.status() }) })

if (CANDIDATE_ID) {
  await page.addInitScript(([id, name]) => {
    localStorage.setItem('agentplatform:candidateId', String(id))
    localStorage.setItem('agentplatform:candidateName', name)
  }, [CANDIDATE_ID, CANDIDATE_NAME])
}

const measure = () => page.evaluate(() => {
  const de = document.documentElement
  const vw = de.clientWidth
  const offenders = Array.from(document.querySelectorAll('body *')).filter(el => {
    if (getComputedStyle(el).position === 'fixed') return false
    const r = el.getBoundingClientRect()
    return r.width > 0 && r.right > vw + 1
  }).slice(0, 6).map(el => {
    const r = el.getBoundingClientRect()
    return `${el.tagName}.${(el.className || '').toString().split(' ').filter(Boolean).slice(0, 2).join('.')}#${el.id || '-'} right=${Math.round(r.right)} w=${Math.round(r.width)} scrollW=${el.scrollWidth} text=${(el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 55)}`
  })
  return { scrollWidth: de.scrollWidth, clientWidth: vw, overflow: de.scrollWidth - vw, offenders }
})

const chipInfo = () => page.evaluate(() => {
  const out = []
  for (const id of ['jobsActiveSummary', 'matchesActiveSummary']) {
    const el = document.getElementById(id)
    if (!el) { continue }
    const cs = getComputedStyle(el.querySelector('.summary-chip') || el)
    out.push({ id, visible: !!el.getClientRects().length, chips: el.querySelectorAll('.summary-chip').length, chipWhiteSpace: cs.whiteSpace, chipRect: (() => { const c = el.querySelector('.summary-chip'); if (!c) return null; const r = c.getBoundingClientRect(); return { right: Math.round(r.right), w: Math.round(r.width) } })() })
  }
  return out
})

// 1) seed live data: upload the real resume through the real UI (only when no candidate id given)
let live = { seeded: false }
if (!CANDIDATE_ID) {
  await page.goto(`${BASE}/resume.html`, { waitUntil: 'domcontentloaded' })
  await page.setInputFiles('#resumeFileInput', (process.env.E2E_RESUME || fileURLToPath(new URL('../../orchestrator/src/test/resources/fixtures/sample-resume.docx', import.meta.url))))
  await page.waitForSelector('#uploadBtn:not([disabled])')
  await page.click('#uploadBtn')
  await page.waitForFunction(() => { const e = document.getElementById('resumeProfileSection'); return e && !e.hidden && e.getClientRects().length > 0 }, null, { timeout: 120000 })
  live = { seeded: true, candidateId: await page.evaluate(() => localStorage.getItem('agentplatform:candidateId')) }
} else {
  live = { seeded: false, candidateId: CANDIDATE_ID }
}

// 2) run real searches so live content is on screen before measuring
const dataState = {}
{
  await page.goto(`${BASE}/jobs.html`, { waitUntil: 'domcontentloaded' })
  await page.click('#jobsSearchBtn')
  await page.waitForFunction(() => document.querySelectorAll('#jobsCardsGrid > *').length > 0, null, { timeout: 180000 }).catch(() => {})
  dataState.jobs = await page.evaluate(() => ({ cards: document.querySelectorAll('#jobsCardsGrid > *').length, chipText: (document.querySelector('#jobsActiveSummary .summary-chip') || {}).textContent }))
  await page.goto(`${BASE}/matches.html`, { waitUntil: 'domcontentloaded' })
  await page.waitForFunction(() => document.querySelectorAll('#matchesCardsGrid > *').length > 0, null, { timeout: 240000 }).catch(() => {})
  dataState.matches = await page.evaluate(() => ({ cards: document.querySelectorAll('#matchesCardsGrid > *').length, chipText: (document.querySelector('#matchesActiveSummary .summary-chip') || {}).textContent }))
}

// 3) E14 matrix
const grid = []
for (const w of [1440, 768, 390]) {
  await page.setViewportSize({ width: w, height: 900 })
  for (const p of ['/index.html', '/resume.html', '/jobs.html', '/matches.html', '/applications.html', '/custom.html']) {
    await page.goto(BASE + p, { waitUntil: 'domcontentloaded' })
    if (p === '/matches.html') {
      await page.waitForFunction(() => document.querySelectorAll('#matchesCardsGrid > *').length > 0
        || (document.getElementById('matchesActiveSummary .summary-chip')), null, { timeout: 240000 }).catch(() => {})
    }
    if (p === '/jobs.html') {
      await page.waitForFunction(() => document.querySelectorAll('#jobsCardsGrid > *').length > 0
        || document.querySelector('#jobsActiveSummary .summary-chip'), null, { timeout: 240000 }).catch(() => {})
    }
    await page.waitForTimeout(1500)
    const m = await measure()
    const chips = await page.evaluate(() => {
      const out = []
      for (const id of ['jobsActiveSummary', 'matchesActiveSummary']) {
        const el = document.getElementById(id)
        const c = el && el.querySelector('.summary-chip')
        if (!c) continue
        const r = c.getBoundingClientRect()
        out.push({ id, text: c.textContent.trim().slice(0, 48), whiteSpace: getComputedStyle(c).whiteSpace, right: Math.round(r.right), w: Math.round(r.width), h: Math.round(r.height), fits: r.right <= document.documentElement.clientWidth + 1 })
      }
      return out
    })
    grid.push({ width: w, page: p, ...m, chips })
    if (m.overflow > 0) {
      await page.screenshot({ path: `${SHOTS}/OVERFLOW-${w}-${p.replace(/\W/g, '')}.png`, fullPage: false })
    }
  }
}

// 4) summary chips + applications detail modal at 390 and 768
await page.setViewportSize({ width: 390, height: 900 })
await page.goto(`${BASE}/jobs.html`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1500)
const chips390 = await chipInfo()
const jobsShot = `${SHOTS}/jobs-390.png`
await page.screenshot({ path: jobsShot })

await page.goto(`${BASE}/matches.html`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1500)
const chipsMatch390 = await chipInfo()
const matchesShot = `${SHOTS}/matches-390.png`
await page.screenshot({ path: matchesShot })

await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' })
let modal = { opened: false }
try {
  await page.waitForSelector('#applicationsCardsGrid button.application-view-btn', { timeout: 60000 })
  await page.click('#applicationsCardsGrid button.application-view-btn')
  await page.waitForSelector('.ms-card', { timeout: 30000 })
  await page.waitForTimeout(2000)
  modal = await page.evaluate(() => {
    const m = document.querySelector('.ms-card')
    const r = m.getBoundingClientRect()
    return { opened: true, right: Math.round(r.right), w: Math.round(r.width), vw: document.documentElement.clientWidth, internalScrollX: m.scrollWidth - m.clientWidth, saysNotSubmitted: /not submitted/i.test(m.innerText) }
  })
} catch (e) { modal = { opened: false, error: String(e.message).slice(0, 160) } }
const modalShot = `${SHOTS}/applications-modal-390.png`
await page.screenshot({ path: modalShot })
await page.setViewportSize({ width: 768, height: 900 })
await page.waitForTimeout(800)
const modal768 = await page.evaluate(() => {
  const m = document.querySelector('.ms-card')
  if (!m) return null
  const r = m.getBoundingClientRect()
  return { right: Math.round(r.right), w: Math.round(r.width), vw: document.documentElement.clientWidth, internalScrollX: m.scrollWidth - m.clientWidth }
})
const modalShot768 = `${SHOTS}/applications-modal-768.png`
await page.screenshot({ path: modalShot768 })

const bad = grid.filter(g => g.overflow > 0)
const out = { tag: process.argv[2] || 'run', live, dataState, checks: grid.length, overflows: bad, grid, chips390, chipsMatch390, modal390: modal, modal768, shots: { jobsShot, matchesShot, modalShot, modalShot768 }, consoleErrs, netFails, apiCalls: apiCalls.slice(0, 20) }
fs.writeFileSync(`${SHOTS}/e14.json`, JSON.stringify(out, null, 2))
console.log(JSON.stringify({ tag: out.tag, checks: out.checks, overflowCount: bad.length, overflows: bad, dataState, chips390, chipsMatch390, modal390: modal, modal768, consoleErrs, netFails }, null, 2))
await browser.close()
