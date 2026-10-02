// Live UI-level approve / email / handoff / timeline on the real application
// (candidate 3, application 2). Real clicks, real endpoints, no interception.
import { chromium } from 'playwright'
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const DIR = join(tmpdir(), 'agent-platform-e2e', 'ui-transitions')
fs.mkdirSync(DIR, { recursive: true })

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, permissions: ['clipboard-read', 'clipboard-write'] })
const page = await ctx.newPage()
page.setDefaultTimeout(60000)
const consoleErrs = [], netFails = [], apiCalls = []
page.on('console', m => { if (m.type() === 'error') consoleErrs.push(m.text().slice(0, 200)) })
page.on('requestfailed', r => netFails.push({ url: r.url().slice(0, 110), err: r.failure() && r.failure().errorText }))
page.on('response', r => { if (r.url().includes('/api/')) apiCalls.push({ url: r.url().replace(BASE, ''), status: r.status() }) })
await page.addInitScript(([id, name]) => {
  localStorage.setItem('agentplatform:candidateId', String(id))
  localStorage.setItem('agentplatform:candidateName', name)
}, [3, (process.env.E2E_CANDIDATE_NAME || 'Jordan Sample')])

const out = {}
const toastText = () => page.evaluate(() => Array.from(document.querySelectorAll('#toastContainer .toast, .toast')).map(t => t.textContent.trim()).slice(-4))
const cardStatus = () => page.evaluate(() => {
  const b = Array.from(document.querySelectorAll('#applicationsCardsGrid .application-card, #applicationsCardsGrid > *')).find(el => /Approved|Prepared|Not pursuing|Generated|Approved/i.test(el.innerText))
  return b ? b.innerText.replace(/\s+/g, ' ').slice(0, 140) : null
})

await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('#applicationsCardsGrid button.application-view-btn', { timeout: 120000 })
out.listBefore = await cardStatus()

// no UI affordance for reject?
out.rejectUiControl = await page.evaluate(() => Array.from(document.querySelectorAll('button,a,[role=button]')).map(b => b.textContent.trim()).filter(t => /reject|not pursuing|discard/i.test(t)))

// 1) approve through the styled confirm dialog (card button: the shared modal footer
//    only carries "Edit package")
await page.click('#applicationsCardsGrid button.application-approve-btn')
await page.waitForSelector('.ms-overlay [data-confirm-accept]')
out.confirmDialog = await page.evaluate(() => {
  const o = document.querySelector('.ms-overlay')
  return { title: o.querySelector('.ms-title').textContent.trim(), message: o.querySelector('.confirm-message').textContent.trim(), warning: (o.querySelector('.confirm-warning') || {}).textContent, nativeDialog: false }
})
await page.screenshot({ path: `${DIR}/02-approve-confirm-1440.png` })
await page.click('.ms-overlay [data-confirm-accept]')
await page.waitForFunction(() => /approved/i.test(document.querySelector('#toastContainer')?.innerText || ''), null, { timeout: 60000 })
out.approveToast = await toastText()
await page.waitForTimeout(2500)
out.listAfterApprove = await cardStatus()
await page.screenshot({ path: `${DIR}/03-list-approved-1440.png` })

// 2) email + handoff reachability in the shared modal detail view
await page.click('#applicationsCardsGrid button.application-view-btn')
await page.waitForSelector('.ms-card')
await page.waitForTimeout(1500)
out.actionReachability = await page.evaluate(() => {
  const probe = id => { const el = document.getElementById(id); if (!el) return { exists: false }; const r = el.getBoundingClientRect(); const cs = getComputedStyle(el); return { exists: true, hidden: el.hidden, display: cs.display, visibleInViewport: cs.display !== 'none' && cs.visibility !== 'hidden' && r.width > 0 && r.height > 0, inModal: !!el.closest('.ms-card'), inHiddenLegacySection: !!el.closest('#applicationDetailSection') } }
  const legacy = document.getElementById('applicationDetailSection')
  return { approve: probe('approveApplicationBtn'), sendEmail: probe('sendEmailBtn'), assistedApply: probe('assistedApplyBtn'), legacySectionHidden: legacy ? (legacy.hidden || getComputedStyle(legacy).display === 'none') : null, modalFooterButtons: Array.from(document.querySelectorAll('.ms-footer button')).map(b => b.textContent.trim()) }
})
await page.screenshot({ path: `${DIR}/04-detail-approved-1440.png` })

// 3) assisted apply kit reachability (gated behind the same approval)
out.kitReachable = await page.evaluate(() => {
  const cards = Array.from(document.querySelectorAll('#applicationsCardsGrid button')).map(b => b.textContent.trim())
  return { cardButtons: cards }
})


// 4) timeline now has persisted events
await page.reload({ waitUntil: 'domcontentloaded' })
await page.waitForSelector('#applicationsCardsGrid button.application-view-btn', { timeout: 120000 })
await page.click('#applicationsCardsGrid button.application-view-btn')
await page.waitForSelector('#applicationDetailTimelineSection')
await page.waitForTimeout(2000)
out.timeline = await page.evaluate(() => {
  const s = document.getElementById('applicationDetailTimelineSection')
  const items = Array.from(document.querySelectorAll('#applicationDetailTimeline li')).map(li => li.innerText.replace(/\s+/g, ' ').trim().slice(0, 80))
  return { sectionVisible: !!s.getClientRects().length, count: items.length, items }
})
await page.screenshot({ path: `${DIR}/09-timeline-1440.png` })

// timeline at 390
const measure = () => page.evaluate(() => {
  const de = document.documentElement, vw = de.clientWidth
  const offenders = Array.from(document.querySelectorAll('body *')).filter(el => { if (getComputedStyle(el).position === 'fixed') return false; const r = el.getBoundingClientRect(); return r.width > 0 && r.right > vw + 1 }).slice(0, 6).map(el => { const r = el.getBoundingClientRect(); return `${el.tagName}.${(el.className || '').toString().split(' ').filter(Boolean).slice(0, 2).join('.')} right=${Math.round(r.right)} w=${Math.round(r.width)} text=${(el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 40)}` })
  const m = document.querySelector('.ms-card')
  return { scrollWidth: de.scrollWidth, clientWidth: vw, overflow: de.scrollWidth - vw, offenders, modalInternalScrollX: m ? m.scrollWidth - m.clientWidth : null }
})
await page.setViewportSize({ width: 390, height: 844 })
await page.waitForTimeout(1500)
out.timeline390 = await measure()
await page.screenshot({ path: `${DIR}/10-timeline-390.png` })
await page.setViewportSize({ width: 768, height: 900 })
await page.waitForTimeout(1000)
out.timeline768 = await measure()
await page.screenshot({ path: `${DIR}/11-timeline-768.png` })

out.apiCalls = apiCalls
out.consoleErrs = consoleErrs
out.netFails = netFails
fs.writeFileSync(`${DIR}/ui-transitions.json`, JSON.stringify(out, null, 2))
console.log(JSON.stringify(out, null, 2))
await browser.close()
