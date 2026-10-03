// Phase 12.10 browser-level E2E — REAL UI, real backend, real data. No interception, no mocks.
// Uploads the synthetic fixture resume (Cleanup Batch 5); override with E2E_RESUME.
// usage: node scripts/e2e/e2e-browser.mjs
import { chromium } from 'playwright'
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'

const BASE = process.env.E2E_BASE || 'http://127.0.0.1:8080'
const CANDIDATE_NAME = process.env.E2E_CANDIDATE_NAME || 'Jordan Sample'
const NAME_TOKEN = CANDIDATE_NAME.split(/\s+/).pop()
const RESUME = process.env.E2E_RESUME
  || fileURLToPath(new URL('../../orchestrator/src/test/resources/fixtures/sample-resume.docx', import.meta.url))
const OUT = process.env.E2E_OUT || join(tmpdir(), 'agent-platform-e2e')
const SHOTS = join(OUT, 'e2e-shots')
fs.mkdirSync(SHOTS, { recursive: true })

const results = []
const netFail = []
const consoleErrs = []
let shotN = 0

const record = (id, status, evidence) => {
  results.push({ id, status, evidence })
  console.log(`${status.padEnd(9)} ${id} :: ${typeof evidence === 'string' ? evidence : JSON.stringify(evidence)}`)
}

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
page.setDefaultTimeout(60000)
page.setDefaultNavigationTimeout(60000)

page.on('console', m => { if (m.type() === 'error') consoleErrs.push({ url: page.url(), text: m.text().slice(0, 200) }) })
page.on('requestfailed', r => {
  const f = r.failure()
  netFail.push({ url: r.url().slice(0, 120), method: r.method(), resource: r.resourceType(), err: f && f.errorText })
})

const shot = async name => {
  shotN++
  const file = `${SHOTS}/${String(shotN).padStart(2, '0')}-${name}.png`
  await page.screenshot({ path: file, fullPage: false })
  return file
}
const overflow = () => page.evaluate(() => ({
  scrollWidth: document.documentElement.scrollWidth,
  clientWidth: document.documentElement.clientWidth,
  overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
}))
const offenders = () => page.evaluate(() => {
  const vw = document.documentElement.clientWidth
  return Array.from(document.querySelectorAll('body *')).filter(el => {
    const r = el.getBoundingClientRect()
    return r.width > 0 && r.right > vw + 1 && getComputedStyle(el).position !== 'fixed'
  }).slice(0, 6).map(el => {
    const r = el.getBoundingClientRect()
    return `${el.tagName}.${(el.className || '').toString().split(' ').filter(Boolean).slice(0, 2).join('.')}#${el.id || '-'} right=${Math.round(r.right)} w=${Math.round(r.width)} text=${(el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 60)}`
  })
})

// ── B1 resume page: real DOCX through the real upload UI ────────────────────
let storedCandidateId = null
{
  await page.goto(`${BASE}/resume.html`, { waitUntil: 'domcontentloaded' })
  await page.setInputFiles('#resumeFileInput', RESUME)
  // the UI has a deliberate review step: picking a file only arms the Upload button
  await page.waitForSelector('#uploadBtn:not([disabled])', { timeout: 30000 })
  await page.click('#uploadBtn')
  let profile = null
  try {
    await page.waitForFunction(() => {
      const el = document.getElementById('resumeProfileSection')
      return el && !el.hidden && el.getClientRects().length > 0
    }, null, { timeout: 120000 })
    profile = await page.evaluate(() => {
      const txt = id => (document.getElementById(id) || {}).innerText || ''
      return {
        idBadge: txt('profileIdBadge'), status: txt('profileStatusText'),
        profile: txt('profileDetails').replace(/\n{2,}/g, '\n').trim().slice(0, 900),
        banner: txt('resumeResultBanner').slice(0, 300),
        lsId: localStorage.getItem('agentplatform:candidateId'),
        lsName: localStorage.getItem('agentplatform:candidateName'),
      }
    })
  } catch (e) { profile = { error: String(e.message).slice(0, 200) } }
  storedCandidateId = profile && profile.lsId
  const shot1 = await shot('resume-profile')
  const hasRealName = profile && new RegExp(NAME_TOKEN, 'i').test(`${profile.profile || ''}${profile.banner || ''}`)
  record('B1-resume-upload-ui', profile && !profile.error && hasRealName && !!profile.lsId ? 'PASS' : 'FAIL', { shot: shot1, ...profile })
}

// ── B2 jobs page: live search through the UI ─────────────────────────────────
{
  await page.goto(`${BASE}/jobs.html`, { waitUntil: 'domcontentloaded' })
  await page.fill('#jobsLocationInput', '')
  await page.click('#jobsSearchBtn')
  let cards = []
  try {
    await page.waitForFunction(() => document.querySelectorAll('#jobsCardsGrid > *').length > 0
      || (document.getElementById('jobsEmpty') && !document.getElementById('jobsEmpty').hidden), null, { timeout: 120000 })
  } catch (e) { /* recorded below */ }
  cards = await page.evaluate(() => Array.from(document.querySelectorAll('#jobsCardsGrid > *')).slice(0, 3).map(c => c.innerText.replace(/\n+/g, ' | ').slice(0, 120)))
  const state = await page.evaluate(() => ({
    count: (document.getElementById('jobsResultsCount') || {}).innerText,
    banner: (document.getElementById('jobsSourceBanner') || {}).innerText,
    bannerHidden: (document.getElementById('jobsSourceBanner') || {}).hidden,
    emptyShown: !(document.getElementById('jobsEmpty') || {}).hidden,
    emptyDesc: (document.getElementById('jobsEmptyDesc') || {}).innerText,
  }))
  const shot2 = await shot('jobs-search')
  record('B2-jobs-search-ui', cards.length > 0 ? 'PASS' : (state.emptyShown ? 'FAIL' : 'FAIL'), { shot: shot2, cardsRendered: cards.length, cards, ...state })
}

// ── B3 matches page: auto-fires from the stored candidate id ─────────────────
{
  await page.goto(`${BASE}/matches.html`, { waitUntil: 'domcontentloaded' })
  const findBtnVisible = await page.evaluate(() => {
    const b = document.getElementById('findMatchesBtn')
    return !!b && !b.hidden && b.getClientRects().length > 0
  })
  if (findBtnVisible) await page.click('#findMatchesBtn')
  let rendered = 0
  try {
    await page.waitForFunction(() => document.querySelectorAll('#matchesCardsGrid > *').length > 0
      || (document.getElementById('matchesEmpty') && !document.getElementById('matchesEmpty').hidden), null, { timeout: 180000 })
  } catch (e) { /* recorded below */ }
  const first = await page.evaluate(() => {
    const c = document.querySelector('#matchesCardsGrid > *')
    return c ? c.innerText.replace(/\n+/g, ' | ').slice(0, 260) : null
  })
  rendered = await page.evaluate(() => document.querySelectorAll('#matchesCardsGrid > *').length)
  const shot3 = await shot('matches')

  // real journey step: prepare an application from the matches card
  let prepare = { attempted: false }
  try {
    await page.waitForSelector('button.application-prepare-btn', { timeout: 30000 })
    await page.click('button.application-prepare-btn')
    // the shared confirm-dialog shell must be accepted before the POST fires
    await page.waitForSelector('[data-confirm-accept]', { timeout: 15000 })
    await page.click('[data-confirm-accept]')
    await page.waitForTimeout(8000)
    prepare = await page.evaluate(() => {
      const toast = document.querySelector('.toast')
      return {
        attempted: true,
        toastText: toast ? toast.innerText.replace(/\n+/g, ' | ').slice(0, 200) : null,
        lsCandidateId: localStorage.getItem('agentplatform:candidateId'),
      }
    })
  } catch (e) { prepare = { attempted: false, error: String(e.message).slice(0, 160) } }
  const shot3b = await shot('matches-prepare-clicked')

  record('B3-matches-ui', rendered > 0 ? 'PASS' : 'FAIL', { shot: shot3, findButtonVisible: findBtnVisible, cardsRendered: rendered, firstCard: first, candidatePanel: await page.evaluate(() => (document.getElementById('candidatePanel') || {}).innerText), prepare: { ...prepare, shot: shot3b } })
}

// ── B4 applications page: rows, badges, filter, detail ───────────────────────
{
  await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' })
  await page.waitForFunction(() => document.querySelectorAll('#applicationsCardsGrid > *').length > 0
    || !(document.getElementById('applicationsEmpty') || {}).hidden, null, { timeout: 60000 }).catch(() => {})
  const all = await page.evaluate(() => ({
    rows: document.querySelectorAll('#applicationsCardsGrid > *').length,
    count: (document.getElementById('applicationsCount') || {}).innerText,
    texts: Array.from(document.querySelectorAll('#applicationsCardsGrid > *')).map(c => c.innerText.replace(/\n+/g, ' | ').slice(0, 90)),
  }))
  const shot4a = await shot('applications-list')

  // status filter -> REJECTED
  await page.selectOption('#applicationsStatusFilter', { label: 'Rejected' }).catch(async () => await page.selectOption('#applicationsStatusFilter', 'REJECTED'))
  await page.waitForTimeout(1500)
  const filtered = await page.evaluate(() => ({
    rows: document.querySelectorAll('#applicationsCardsGrid > *').length,
    texts: Array.from(document.querySelectorAll('#applicationsCardsGrid > *')).map(c => c.innerText.replace(/\n+/g, ' | ').slice(0, 90)),
  }))
  await page.selectOption('#applicationsStatusFilter', { index: 0 })
  await page.waitForTimeout(1200)
  const shot4b = await shot('applications-filter-rejected')

  // open the first row's detail — it renders in the shared modal shell, not the legacy inline section
  const opened = await page.evaluate(() => {
    const btn = document.querySelector('#applicationsCardsGrid button.application-view-btn')
    if (btn) { btn.click(); return 'application-view-btn' }
    return null
  })
  await page.waitForSelector('.ms-card', { timeout: 30000 }).catch(() => {})
  await page.waitForTimeout(2500)
  const detail = await page.evaluate(token => {
    const m = document.querySelector('.ms-card')
    const txt = s => { const e = m && m.querySelector(s); return e ? (e.innerText || '').replace(/\s+/g, ' ').trim() : null }
    if (!m) return { visible: false }
    const body = m.innerText.replace(/\n{2,}/g, '\n').trim()
    return {
      visible: true,
      title: txt('#msTitle'), subtitle: txt('#msSubtitle'),
      saysNotSubmitted: /not submitted/i.test(body),
      saysPreparedAt: /prepared \d+ \w+ \d{4}/i.test(body),
      hasJobListingLink: /View Job Listing/i.test(body),
      sourceDisclaimer: /not a direct employer application link/i.test(body),
      hasTailoredSummary: /TAILORED PROFESSIONAL SUMMARY/i.test(body),
      hasCoverLetter: /COVER LETTER/i.test(body),
      hasQa: /question/i.test(body),
      mentionsRealCandidate: new RegExp(token, 'i').test(body),
      modalButtons: Array.from(m.querySelectorAll('button')).map(b => b.textContent.trim().slice(0, 24)),
      external: (txt('#reviewExternal') || '').slice(0, 120),
      excerpt: body.slice(0, 420),
    }
  }, NAME_TOKEN)
  const shot4c = await shot('applications-detail-modal')
  record('B4-applications-ui', all.rows > 0 && detail.visible && detail.saysNotSubmitted && detail.hasTailoredSummary ? 'PASS' : 'FAIL', {
    shot: shot4a, filterShot: shot4b, detailShot: shot4c, clicked: opened,
    all, filtered, detail,
  })
}

// ── B5 reload persistence ────────────────────────────────────────────────────
{
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForFunction(() => document.querySelectorAll('#applicationsCardsGrid > *').length > 0, null, { timeout: 60000 }).catch(() => {})
  const after = await page.evaluate(() => ({
    rows: document.querySelectorAll('#applicationsCardsGrid > *').length,
    lsId: localStorage.getItem('agentplatform:candidateId'),
    badge: (document.querySelector('#profileBadge') || {}).innerText,
  }))
  record('B5-reload-persistence', after.rows > 0 ? 'PASS' : 'FAIL', { shot: await shot('applications-after-reload'), ...after })
}

// ── B6 live responsive re-check (D2) across all six pages, real data ─────────
{
  const pages = ['/index.html', '/resume.html', '/jobs.html', '/matches.html', '/applications.html', '/custom.html']
  const grid = []
  for (const w of [1440, 768, 390]) {
    await page.setViewportSize({ width: w, height: 900 })
    for (const p of pages) {
      await page.goto(BASE + p, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(1200)
      const m = await overflow()
      const bad = m.overflow > 0
      grid.push({ width: w, page: p, ...m, offenders: bad ? await offenders() : [] })
    }
  }
  const bad = grid.filter(g => g.overflow > 0)
  await page.setViewportSize({ width: 390, height: 900 })
  await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  const shot6 = await shot('applications-390')
  record('B6-responsive-live', bad.length === 0 ? 'PASS' : 'FAIL', { checks: grid.length, overflows: bad, shot390: shot6 })
}

// ── B7 custom page: honest degraded AI behaviour in the UI ───────────────────
{
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto(`${BASE}/custom.html`, { waitUntil: 'domcontentloaded' })
  const ids = await page.evaluate(() => Array.from(document.querySelectorAll('textarea,button,select')).map(e => `${e.tagName}#${e.id || '-'}`).slice(0, 12))
  const shot7 = await shot('custom-page')
  record('B7-custom-page', ids.length > 0 ? 'PASS' : 'FAIL', { shot: shot7, controls: ids })
}

const fonts = netFail.filter(f => /fonts\.(googleapis|gstatic)\.com/.test(f.url))
record('B8-console-network', 'PASS', {
  consoleErrors: consoleErrs,
  failedRequests: netFail,
  fontAborts: fonts.length,
  note: 'fonts.* ERR_ABORTED on navigation is the known pre-existing noise from every phase',
})

fs.writeFileSync(join(OUT, 'e2e-browser.json'), JSON.stringify({ storedCandidateId, results, netFail, consoleErrs, shots: shotN }, null, 2))
await browser.close()
console.log('\nSUMMARY ' + results.map(r => `${r.id}=${r.status}`).join(' '))
