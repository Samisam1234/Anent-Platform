// Live PDF upload through the real UI (the one remaining NOT RUN browser item).
// Builds a real one-page PDF with a text layer, uploads it via the real file input,
// and reports exactly what the platform parsed back.
import { chromium } from 'playwright'
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const BASE = 'http://127.0.0.1:8080'
const DIR = join(tmpdir(), 'agent-platform-e2e', 'ui-pdf')
fs.mkdirSync(DIR, { recursive: true })

// minimal but structurally valid PDF (correct xref offsets)
const lines = [
  'Name: Jordan Sample',
  'Email: jordan.sample@example.com',
  'Skills: Java, Spring Boot, PostgreSQL, REST API, SQL',
  'Role: Backend Engineer',
  'Education: B.Tech Computer Science',
]
const content = 'BT /F1 12 Tf 72 720 Td 16 TL\n' + lines.map((l, i) => (i ? '0 -18 Td ' : '') + `(${l.replace(/([()\\])/g, '\\$1')}) Tj`).join('\n') + '\nET\n'
const objs = [
  '<< /Type /Catalog /Pages 2 0 R >>',
  '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
  '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
  '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  `<< /Length ${content.length} >>\nstream\n${content}endstream`,
]
let pdf = '%PDF-1.4\n'
const offsets = []
objs.forEach((body, i) => { offsets.push(pdf.length); pdf += `${i + 1} 0 obj\n${body}\nendobj\n` })
const xref = pdf.length
pdf += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n` + offsets.map(o => String(o).padStart(10, '0') + ' 00000 n \n').join('')
pdf += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
const pdfPath = `${DIR}/live-upload-sample.pdf`
fs.writeFileSync(pdfPath, Buffer.from(pdf, 'latin1'))

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
page.setDefaultTimeout(90000)
const consoleErrs = [], netFails = [], api = []
page.on('console', m => { if (m.type() === 'error') consoleErrs.push(m.text().slice(0, 200)) })
page.on('requestfailed', r => netFails.push({ url: r.url().slice(0, 110), err: r.failure() && r.failure().errorText }))
page.on('response', r => { if (r.url().includes('/api/')) api.push({ url: r.url().replace(BASE, ''), status: r.status() }) })

await page.goto(`${BASE}/resume.html`, { waitUntil: 'domcontentloaded' })
await page.setInputFiles('#resumeFileInput', pdfPath)
const fileName = await page.evaluate(() => document.querySelector('#resumeFileInput').files[0]?.name)
const uploadEnabled = await page.evaluate(() => !document.getElementById('uploadBtn').disabled)
await page.screenshot({ path: `${DIR}/01-pdf-selected-1440.png` })
await page.click('#uploadBtn')
await page.waitForFunction(() => { const e = document.getElementById('resumeProfileSection'); return e && !e.hidden && e.getClientRects().length > 0 }, null, { timeout: 240000 })
await page.waitForTimeout(1500)

const parsed = await page.evaluate(() => {
  const text = document.getElementById('resumeProfileSection').innerText.replace(/\s+/g, ' ').trim()
  return { sectionText: text.slice(0, 400), candidateId: localStorage.getItem('agentplatform:candidateId'), candidateName: localStorage.getItem('agentplatform:candidateName') }
})
await page.screenshot({ path: `${DIR}/02-pdf-parsed-1440.png` })

// the parsed profile must also work in matches (the PDF is the only profile source now)
await page.goto(`${BASE}/matches.html`, { waitUntil: 'domcontentloaded' })
const matched = await page.waitForFunction(() => document.querySelectorAll('#matchesCardsGrid > *').length > 0, null, { timeout: 240000 }).then(() => true).catch(() => false)
const matchState = await page.evaluate(() => ({ cards: document.querySelectorAll('#matchesCardsGrid > *').length, chip: (document.querySelector('#matchesActiveSummary .summary-chip') || {}).textContent?.trim() }))
await page.screenshot({ path: `${DIR}/03-pdf-profile-matches-1440.png` })

const result = { pdf: { path: pdfPath, bytes: fs.statSync(pdfPath).size, textLines: lines.length }, fileName, uploadEnabled, parsed, matched, matchState, api, consoleErrs, netFails }
fs.writeFileSync(`${DIR}/pdf-upload.json`, JSON.stringify(result, null, 2))
console.log(JSON.stringify(result, null, 2))
await browser.close()
