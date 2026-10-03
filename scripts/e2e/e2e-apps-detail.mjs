// Targeted: open the applications detail panel through the real UI and watch it load.
import { chromium } from 'playwright'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
// fresh context has empty localStorage — seed the candidate the way the resume page would
const CANDIDATE_ID = 2
await page.addInitScript(id => {
  localStorage.setItem('agentplatform:candidateId', String(id))
  localStorage.setItem('agentplatform:candidateName', (process.env.E2E_CANDIDATE_NAME || 'Jordan Sample'))
}, CANDIDATE_ID)
const api = []
page.on('response', r => { if (r.url().includes('/api/')) api.push({ url: r.url().replace(BASE, ''), status: r.status() }) })
const errs = []
page.on('pageerror', e => errs.push(String(e.message).slice(0, 200)))
page.on('console', m => { if (m.type() === 'error') errs.push('console: ' + m.text().slice(0, 200)) })

await page.goto(`${BASE}/applications.html`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('#applicationsCardsGrid button.application-view-btn', { timeout: 60000 })
const cardText = await page.evaluate(() => document.querySelector('#applicationsCardsGrid > *').innerText.replace(/\n+/g, ' | ').slice(0, 200))

await page.click('#applicationsCardsGrid button.application-view-btn')
await page.waitForTimeout(2500)
const modalInfo = await page.evaluate(() => {
  const m = document.querySelector('.ms-card')
  const sec = document.getElementById('applicationDetailSection')
  return {
    modalText: m ? m.innerText.replace(/\n{2,}/g, '\n').trim().slice(0, 900) : null,
    modalButtons: m ? Array.from(m.querySelectorAll('button')).map(b => `${b.textContent.trim().slice(0, 28)} [${(b.className || '').toString().slice(0, 40)}]`) : null,
    modalIds: m ? Array.from(m.querySelectorAll('[id]')).map(e => e.id).slice(0, 20) : null,
    inlineSectionExists: !!sec,
    inlineSectionHidden: sec ? sec.hidden : null,
    inlineSectionText: sec ? sec.innerText.replace(/\n{2,}/g, '\n').trim().slice(0, 500) : null,
    cardClickTargets: Array.from(document.querySelectorAll('#applicationsCardsGrid > * [data-open], #applicationsCardsGrid > * [class*="view"], #applicationsCardsGrid > *')).slice(0, 6)
      .map(e => `${e.tagName}.${(e.className || '').toString().split(' ').filter(Boolean).slice(0, 2).join('.')} data-open=${e.getAttribute('data-open')} view=${e.getAttribute('data-view-details')}`),
  }
})
const timeline = []
for (let i = 0; i < 8; i++) {
  await page.waitForTimeout(1500)
  const s = await page.evaluate(() => {
    const vis = el => !!el && !el.hidden && el.getClientRects().length > 0
    const sec = document.getElementById('applicationDetailSection')
    const modal = document.querySelector('.modal, .modal-overlay, [role="dialog"], #applicationDetailModal')
    const txt = id => ((document.getElementById(id) || {}).innerText || '').replace(/\s+/g, ' ').trim()
    return {
      detailSectionVisible: vis(sec),
      detailSectionClasses: sec ? sec.className : null,
      detailSectionParentHidden: sec && sec.parentElement ? sec.parentElement.hidden : null,
      modalPresent: !!modal,
      modalVisible: vis(modal),
      modalClass: modal ? modal.className : null,
      title: txt('applicationDetailTitle'), company: txt('applicationDetailCompany'),
      status: txt('applicationDetailStatus'), matchScore: txt('applicationDetailMatchScore'),
      recommendation: txt('applicationDetailRecommendation'),
      strengths: txt('applicationDetailStrengths').slice(0, 100), gaps: txt('applicationDetailGaps').slice(0, 100),
      summary: txt('applicationDetailSummary').slice(0, 100), coverLetter: txt('applicationDetailCoverLetter').slice(0, 100),
      answers: txt('applicationDetailAnswers').slice(0, 100), highlights: txt('applicationDetailHighlights').slice(0, 100),
      timeline: txt('applicationDetailTimeline').slice(0, 200),
      timelineSectionVisible: vis(document.getElementById('applicationDetailTimelineSection')),
      external: txt('applicationDetailExternal').slice(0, 80),
    }
  })
  timeline.push(s)
  if (s.detailSectionVisible || s.modalVisible) break
}
await page.screenshot({ path: join(tmpdir(), 'agent-platform-e2e', 'e2e-shots', '07b-applications-detail.png') })
console.log(JSON.stringify({ cardText, api, errs, modalInfo, timeline }, null, 2))
await browser.close()
