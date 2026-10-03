// Phase 11 live acceptance: MCP-backed employer handoff through the REAL UI.
// Matches page (real MCP search) -> Prepare -> Approve -> Assisted Apply -> Review
// -> acknowledgement -> same-origin handoff POST -> employer destination opens in a real tab.
// No route interception, no fixtures, no synthetic URLs.
// usage: node phase11-mcp-handoff.mjs <candidateId>
import { chromium } from 'playwright'
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const CANDIDATE_ID = Number(process.argv[2] || 1)
const DIR = join(tmpdir(), 'agent-platform-e2e', 'phase11-handoff')
fs.mkdirSync(DIR, { recursive: true })

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
page.setDefaultTimeout(120000)
const consoleErrs = [], netFails = [], api = []
page.on('console', m => { if (m.type() === 'error') consoleErrs.push({ url: page.url(), text: m.text().slice(0, 200) }) })
page.on('requestfailed', r => netFails.push({ url: r.url().slice(0, 110), err: r.failure() && r.failure().errorText }))
page.on('response', r => { if (r.url().includes('/api/')) api.push({ url: r.url().replace(BASE, ''), status: r.status() }) })
page.on('popup', p => console.log('popup opened:', p.url()))

await page.addInitScript(([id]) => {
  localStorage.setItem('agentplatform:candidateId', String(id))
  localStorage.setItem('agentplatform:candidateName', 'Live Phase 11')
}, [CANDIDATE_ID])

const out = { candidateId: CANDIDATE_ID, steps: [] }
const step = (name, data) => { out.steps.push({ name, ...data }); console.log(`-- ${name}: ${JSON.stringify(data)}`) }

// ── 1. Matches page fires a real search (MCP is one of the live sources) ──
await page.goto(`${BASE}/matches.html`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('#matchesCardsGrid button.application-prepare-btn', { timeout: 240000 })
await page.waitForTimeout(1500)

const mcpCard = await page.evaluate(() => {
  const cards = Array.from(document.querySelectorAll('#matchesCardsGrid .job-card, #matchesCardsGrid [data-id]'))
  for (const c of cards) {
    const id = c.getAttribute('data-id')
    if (id && id.startsWith('openings-')) {
      const apply = c.querySelector('a.btn-apply-external, a.btn-source-listing, button.is-disabled')
      return {
        jobId: id,
        title: (c.querySelector('.job-card-title, h3') || {}).textContent?.trim() || null,
        linkLabel: apply ? apply.textContent.trim() : null,
        linkHref: apply ? apply.getAttribute('href') : null,
        note: (c.querySelector('.external-note') || {}).textContent?.trim() || null,
      }
    }
  }
  return null
})
step('mcp card rendered', mcpCard)
if (!mcpCard) { console.log('NO MCP CARD'); await browser.close(); process.exit(1) }
await page.screenshot({ path: `${DIR}/01-mcp-match-card.png` })

// ── 2. Prepare the application package for that exact MCP job ──
await page.click(`#matchesCardsGrid button.application-prepare-btn[data-job-id="${mcpCard.jobId}"]`)
await page.waitForSelector('.ms-card', { timeout: 60000 })
await page.waitForTimeout(800)
const confirmBtn = await page.$('.ms-card .btn-primary')
if (confirmBtn) await confirmBtn.click()
await page.waitForSelector('.ms-card', { timeout: 240000 })
await page.waitForFunction(() => {
  const el = document.querySelector('.ms-card')
  return el && /prepared|review|resume summary/i.test(el.innerText)
}, null, { timeout: 240000 })
await page.waitForTimeout(1000)
const preparedModal = await page.evaluate(() => {
  const m = document.querySelector('.ms-card')
  return { text: m.innerText.replace(/\s+/g, ' ').slice(0, 260), hasApprove: !!m.querySelector('[data-review-approve], #approveApplicationBtn, button') }
})
step('prepared review modal', preparedModal)
await page.screenshot({ path: `${DIR}/02-prepared-review.png` })

// close the modal so the Applications page can be used for approval + handoff
await page.keyboard.press('Escape')
await page.waitForTimeout(500)

// ── 3. Approve the package (server-side approval gates the Apply Kit) ──
await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('#applicationsCardsGrid button.application-approve-btn', { timeout: 120000 })
const approveBtn = await page.$('#applicationsCardsGrid button.application-approve-btn:not([disabled])')
const approvedCard = await page.evaluate(() => {
  const c = document.querySelector('#applicationsCardsGrid .application-card')
  return c ? { text: c.innerText.replace(/\s+/g, ' ').slice(0, 140) } : null
})
step('application card', approvedCard)
if (approveBtn) await approveBtn.click()
await page.waitForSelector('.ms-card', { timeout: 30000 })
await page.waitForTimeout(600)
const approveConfirm = await page.$('.ms-card .btn-primary')
if (approveConfirm) await approveConfirm.click()
await page.waitForFunction(() => /Approved/i.test(document.body.innerText), null, { timeout: 60000 })
await page.waitForTimeout(1200)
step('approved', { approvedButtonDisabled: await page.$eval('#applicationsCardsGrid button.application-approve-btn', b => b.disabled).catch(() => null) })
await page.screenshot({ path: `${DIR}/03-approved.png` })

// ── 4. Open the review modal for that application, then Assisted Apply ──
await page.click(`#applicationsCardsGrid button.application-view-btn[data-id="${await page.$eval('#applicationsCardsGrid .application-card', c => c.getAttribute('data-id') || c.querySelector('.application-view-btn').getAttribute('data-id'))}"]`)
await page.waitForSelector('.ms-card', { timeout: 60000 })
await page.waitForTimeout(1200)
const reviewShot = await page.evaluate(() => {
  const m = document.querySelector('.ms-card')
  return { hasAssistBtn: !!m.querySelector('[data-review-apply]'), text: m.innerText.replace(/\s+/g, ' ').slice(0, 200) }
})
step('review modal', reviewShot)
await page.screenshot({ path: `${DIR}/04-review-modal.png` })

const assist = await page.$('.ms-card [data-review-apply]')
if (!assist) { console.log('NO ASSISTED APPLY BUTTON'); await browser.close(); process.exit(1) }
await assist.click()
await page.waitForSelector('.apply-kit', { timeout: 60000 })
await page.waitForTimeout(800)

const kitPrepare = await page.evaluate(() => {
  const k = document.querySelector('.apply-kit')
  return {
    state: k.getAttribute('data-apply-state'),
    url: (k.querySelector('[data-apply-url]') || {}).textContent?.trim(),
    from: (k.querySelector('.external-note') || {}).textContent?.trim(),
    badge: (k.querySelector('.external-badge') || {}).textContent?.trim(),
    fields: Array.from(k.querySelectorAll('[data-kit-field]')).length,
  }
})
step('apply kit prepare', kitPrepare)
await page.screenshot({ path: `${DIR}/05-apply-kit-prepare.png` })

// ── 5. Review -> explicit acknowledgement -> handoff ──
await page.click('#applyKitReviewBtn')
await page.waitForSelector('#applyKitAck', { timeout: 30000 })
await page.waitForTimeout(600)
const disabledBeforeAck = await page.$eval('#applyKitHandoffBtn', b => b.disabled)
await page.check('#applyKitAck')
await page.waitForTimeout(400)
const disabledAfterAck = await page.$eval('#applyKitHandoffBtn', b => b.disabled)
step('handoff button gating', { disabledBeforeAck, disabledAfterAck })
await page.screenshot({ path: `${DIR}/06-apply-kit-review.png` })

// employer destination opens in a real new tab
const [employerTab] = await Promise.all([
  ctx.waitForEvent('page', { timeout: 60000 }),
  page.click('#applyKitHandoffBtn'),
])
await employerTab.waitForLoadState('domcontentloaded').catch(() => {})
const employerUrl = employerTab.url()
const employerTitle = await employerTab.title().catch(() => null)
await employerTab.screenshot({ path: `${DIR}/07-employer-destination.png` }).catch(e => console.log('shot failed', e.message))
step('employer destination', { employerUrl, employerTitle })

// ── 6. Server must have recorded the handoff (same-origin POST persisted) ──
await page.waitForTimeout(1500)
const appId = await page.$eval('#applicationsCardsGrid .application-card', c => c.getAttribute('data-id')).catch(() => null)
let recorded = null
if (appId) {
  const r = await fetch(`${BASE}/api/v1/applications/${appId}`)
  if (r.ok) {
    const a = await r.json()
    recorded = { id: a.id, status: a.applicationStatus, employerUrl: a.employerUrl, employerOpenedAt: a.employerOpenedAt, approvedAt: a.approvedAt, updatedAt: a.updatedAt }
  }
}
step('server handoff record', recorded)

// ── 7. Responsive check of the Apply Kit review surface ──
await page.bringToFront()
for (const w of [1440, 768, 390]) {
  await page.setViewportSize({ width: w, height: 900 })
  await page.waitForTimeout(900)
  const m = await page.evaluate(() => {
    const de = document.documentElement, vw = de.clientWidth
    const offenders = Array.from(document.querySelectorAll('.ms-card *')).filter(el => {
      const r = el.getBoundingClientRect()
      return r.width > 0 && r.right > vw + 1
    }).slice(0, 4).map(el => `${el.tagName}.${(el.className || '').toString().split(' ').filter(Boolean).slice(0, 2).join('.')} right=${Math.round(el.getBoundingClientRect().right)}`)
    const card = document.querySelector('.ms-card')
    return { overflowX: de.scrollWidth - vw, offenders, cardInternalScrollX: card ? card.scrollWidth - card.clientWidth : null }
  })
  step('responsive w' + w, m)
  await page.screenshot({ path: `${DIR}/08-review-${w}.png` })
}

out.api = api
out.handoffRequests = api.filter(a => /handoff/.test(a.url))
out.consoleErrs = consoleErrs
out.netFails = netFails
fs.writeFileSync(`${DIR}/phase11-handoff.json`, JSON.stringify(out, null, 2))
console.log(JSON.stringify({ api: api.filter(a => /handoff|jobs\//.test(a.url)), handoffRequests: out.handoffRequests, consoleErrs, netFails }, null, 2))
await browser.close()
