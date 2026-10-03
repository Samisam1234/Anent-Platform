// Focused diagnostic: why does the resume upload UI not reveal the profile?
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const RESUME = (process.env.E2E_RESUME || fileURLToPath(new URL('../../orchestrator/src/test/resources/fixtures/sample-resume.docx', import.meta.url)))
const SHOT = join(tmpdir(), 'agent-platform-e2e', 'e2e-shots', '01b-resume-diag.png')

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const log = { requests: [], responses: [], console: [], pageErrors: [], timeline: [] }
const t0 = Date.now()
const at = () => ((Date.now() - t0) / 1000).toFixed(1) + 's'

page.on('request', r => { if (r.url().includes('/api/')) log.requests.push({ t: at(), method: r.method(), url: r.url().replace(BASE, ''), postData: (r.postData() || '').slice(0, 120) }) })
page.on('response', async r => { if (r.url().includes('/api/')) log.responses.push({ t: at(), status: r.status(), url: r.url().replace(BASE, '') }) })
page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') log.console.push({ t: at(), type: m.type(), text: m.text().slice(0, 300) }) })
page.on('pageerror', e => log.pageErrors.push({ t: at(), text: String(e.message).slice(0, 300) }))

await page.goto(`${BASE}/resume.html`, { waitUntil: 'domcontentloaded' })
const initial = await page.evaluate(() => ({
  hasInput: !!document.getElementById('resumeFileInput'),
  inputHidden: (document.getElementById('resumeFileInput') || {}).hidden,
  dropZone: !!document.getElementById('resumeDropZone'),
  uploadBtn: !!document.getElementById('uploadBtn'),
  listenersBound: typeof window.__resumeReady,
}))
log.timeline.push({ t: '0s', step: 'loaded', initial })

await page.setInputFiles('#resumeFileInput', RESUME)
log.timeline.push({ t: at(), step: 'setInputFiles' })

for (let i = 0; i < 24; i++) {
  await page.waitForTimeout(3000)
  const state = await page.evaluate(() => {
    const vis = id => { const e = document.getElementById(id); return !!e && !e.hidden && e.getClientRects().length > 0 }
    const txt = id => ((document.getElementById(id) || {}).innerText || '').replace(/\s+/g, ' ').trim()
    return {
      uploadPanel: vis('resumeUploadPanel'), analyzing: vis('resumeAnalyzingPanel'), profileSection: vis('resumeProfileSection'),
      errorVisible: vis('resumeErrorAlert'), errorTitle: txt('resumeErrorTitle'), errorMessage: txt('resumeErrorMessage').slice(0, 200),
      analyzingSub: txt('analyzingSub'), analyzingStages: txt('analyzingStages').slice(0, 200),
      fileName: txt('resumeFileName'), retryBtn: vis('retryBtn'),
    }
  })
  log.timeline.push({ t: at(), step: 'poll', state })
  if (state.profileSection || state.errorVisible) break
}

await page.screenshot({ path: SHOT })
console.log(JSON.stringify({ log, shot: SHOT }, null, 2))
await browser.close()
