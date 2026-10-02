// Per-stage diagnosis: score REAL openings-MCP results for the keyword "java" using the
// exact rules of JobRelevanceScorer (title 3.0, declared skill 2.5, description 1.0,
// multi-word bonus 0.5, threshold 2.5, word-boundary match).
// Read-only: nothing is intercepted, injected or written back to the app.
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const DIR = join(tmpdir(), 'agent-platform-e2e', 'diag')
const TITLE_W = 3.0, SKILL_W = 2.5, DESC_W = 1.0, BONUS = 0.5, THRESHOLD = 2.5

function sse(file) {
  const raw = fs.readFileSync(file, 'utf8')
  const line = raw.split('\n').find(l => l.startsWith('data: '))
  if (!line) return null
  try { return JSON.parse(line.slice(6)) } catch { return null }
}
const wordRe = (k) => {
  const esc = k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const b = `(?<![A-Za-z0-9])${esc}(?![A-Za-z0-9])`
  return new RegExp(b, 'i')
}
function hits(hay, kw) { return hay && wordRe(kw).test(hay) }

const rows = []
for (const f of fs.readdirSync(DIR).filter(n => n.endsWith('.out'))) {
  const tool = f.replace('.out', '')
  const parsed = sse(`${DIR}/${f}`)
  const sc = parsed && parsed.result && parsed.result.structuredContent
  const data = sc && sc.data ? (Array.isArray(sc.data) ? sc.data : [sc.data]) : []
  for (const item of data) {
    if (!item || !item.title) continue
    const title = String(item.title)
    const quals = [].concat(item.minimum_qualifications || item.qualifications || [])
      .map(q => (typeof q === 'string' ? q : JSON.stringify(q))).join(' ')
    const desc = [item.description, quals, [].concat(item.responsibilities || []).join(' ')]
      .filter(Boolean).join(' ').toLowerCase()
    const skills = [].concat(item.skills || []).map(String)
    const loc = item.location || item.city || (item.locations && item.locations[0] && item.locations[0].name) || null
    const date = item.posting_date || item.posted_date || item.date_posted || item.updated_at || null
    const url = item.url || item.apply_url || null
    const id = item.id ?? item.job_id ?? null
    const nDesc = (desc.match(new RegExp(wordRe('java').source, 'gi')) || []).length
    const best = hits(title, 'java') ? TITLE_W : (skills.some(s => hits(s, 'java')) ? SKILL_W : 0)
    const descOnly = best === 0 && nDesc > 0 ? nDesc * DESC_W : 0
    const score = best > 0 ? best : descOnly
    rows.push({
      tool, id, title, location: loc, date, url,
      hasApplyUrl: !!item.apply_url,
      titleHit: hits(title, 'java'),
      skillHit: skills.some(s => hits(s, 'java')),
      descJavaMentions: nDesc,
      score, relevant: score >= THRESHOLD,
    })
  }
}

console.log(`MCP raw rows for keyword "java": ${rows.length}`)
console.log(`  title matches:   ${rows.filter(r => r.titleHit).length}`)
console.log(`  skill matches:   ${rows.filter(r => r.skillHit).length}`)
console.log(`  description-only rows: ${rows.filter(r => r.score > 0 && r.score < THRESHOLD).length} (max ${Math.max(0, ...rows.map(r => r.score))} < threshold ${THRESHOLD})`)
console.log(`  rows that PASS the relevance rule: ${rows.filter(r => r.relevant).length}`)
console.log('')
console.log('sample of removed rows:')
rows.slice(0, 8).forEach(r => console.log(
  `  [${r.tool}] "${r.title}"\n    id=${r.id} loc=${r.location} date=${r.date} descJava=${r.descJavaMentions} score=${r.score} url=${r.url}`))
fs.writeFileSync(`${DIR}/java-score-analysis.json`, JSON.stringify(rows, null, 2))
