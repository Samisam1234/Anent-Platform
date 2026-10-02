// Step 1 diagnosis: replay the EXACT JobSearchService pipeline over REAL openings-MCP
// search results, stage by stage, mirroring the Java logic 1:1:
//   hasMinimalQuality -> isTrackRelevant(empty tracks) -> NegativeJobFilter(GLOBAL only)
//   -> JobRelevanceScorer(threshold 2.5) -> location/source/experience/type/date (all null)
// Read-only: nothing is intercepted, injected, or written back to the app.
import fs from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

const DIR = join(tmpdir(), 'agent-platform-e2e', 'diag')
const TITLE_W = 3.0, SKILL_W = 2.5, DESC_W = 1.0, BONUS = 0.5, THRESHOLD = 2.5
const KEYWORD = process.argv[2] || 'java'
const GLOBAL_EXCLUSIONS = ['service desk', 'helpdesk', 'help desk', 'sales', 'marketing',
  'unpaid', 'unpaid internship', 'internship without stipend', 'no stipend', 'volunteer']

const wb = (k) => new RegExp(`(?<![A-Za-z0-9])${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![A-Za-z0-9])`, 'i')
const has = (hay, kw) => !!hay && wb(kw).test(hay)
const countAll = (hay, kw) => (hay && hay.match(new RegExp(wb(kw).source, 'gi')) || []).length

function sse(file) {
  const raw = fs.readFileSync(file, 'utf8')
  const line = raw.split('\n').find(l => l.startsWith('data: '))
  try { return JSON.parse(line.slice(6)) } catch { return null }
}

// Mirror of OpeningsMcpJobSourceProvider.mapItem + JobRelevanceScorer + NegativeJobFilter.
function mapItem(tool, item) {
  const rawId = item.id ?? item.job_id ?? null
  const title = item.title
  const company = [item.company, item.company_name].find(v => v && String(v).trim()) || null
  const location = item.location ?? null
  let desc = ''
  if (Array.isArray(item.minimum_qualifications)) {
    desc += 'Minimum Qualifications:\n' + item.minimum_qualifications.map(q => '- ' + String(q)).join('\n') + '\n'
  }
  if (item.description && String(item.description).trim()) { if (desc) desc += '\n'; desc += item.description }
  const description = desc.length ? desc : null
  const experienceRequirement = item.experience_level ?? null
  const employmentType = null, postingDate = null   // provider passes these as null
  const requiredSkills = [], preferredSkills = []   // provider passes empty lists
  const url = item.url ?? null
  return {
    tool,
    id: 'openings-' + tool + '-' + (rawId == null ? Math.abs(JSON.stringify(item).split('').reduce((a, c) => ((a << 5) - a + c.charCodeAt(0)) | 0, 7)) : rawId),
    upstreamId: rawId, title, company, location, description,
    experienceRequirement, employmentType, postingDate,
    requiredSkills, preferredSkills, url,
  }
}

function relevanceScore(j) {
  const title = (j.title || '').toLowerCase()
  const desc = (j.description || '').toLowerCase()
  const skills = [...j.requiredSkills, ...j.preferredSkills]
  let best = 0
  if (has(title, KEYWORD)) best = TITLE_W
  else if (skills.some(s => has(String(s), KEYWORD))) best = SKILL_W
  else if (has(desc, KEYWORD)) best = DESC_W * countAll(desc, KEYWORD)
  const isPhrase = KEYWORD.trim().includes(' ')
  const total = best > 0 ? best + (isPhrase ? BONUS : 0) : 0
  return { score: Math.round(total * 100) / 100, titleHit: has(title, KEYWORD), skillHit: false, descHits: countAll(desc, KEYWORD) }
}

function exclusionReason(j) {
  const title = (j.title || '').toLowerCase()
  return GLOBAL_EXCLUSIONS.find(p => has(title, p)) || null
}

const rows = []
for (const f of fs.readdirSync(DIR).filter(n => n.endsWith('.out'))) {
  const tool = f.replace('.out', '')
  const parsed = sse(`${DIR}/${f}`)
  const sc = parsed && parsed.result && parsed.result.structuredContent
  const data = sc && sc.data ? (Array.isArray(sc.data) ? sc.data : [sc.data]) : []
  for (const item of data) { if (item && item.title) rows.push(mapItem(tool, item)) }
}

// stage-by-stage, exactly as JobSearchService chains them
const stages = { raw: rows.length, droppedQuality: [], droppedNegative: [], droppedRelevance: [], kept: [] }
for (const j of rows) {
  if (!j.id || !j.title) { stages.droppedQuality.push(j); continue }   // hasMinimalQuality
  const neg = exclusionReason(j)
  if (neg) { stages.droppedNegative.push({ ...j, neg }); continue }     // negative rules (empty tracks)
  const { score, titleHit, descHits } = relevanceScore(j)
  if (score < THRESHOLD) { stages.droppedRelevance.push({ ...j, score, titleHit, descHits }); continue }
  stages.kept.push({ ...j, score, titleHit, descHits })
}

console.log(`keyword="${KEYWORD}"   raw MCP rows: ${rows.length}`)
console.log(`  1 hasMinimalQuality        dropped: ${stages.droppedQuality.length}`)
console.log(`  2 negative rules           dropped: ${stages.droppedNegative.length}` +
  (stages.droppedNegative.length ? ` (${[...new Set(stages.droppedNegative.map(r => r.neg))].join(', ')})` : ''))
console.log(`  3 relevance (< ${THRESHOLD})     dropped: ${stages.droppedRelevance.length}`)
console.log(`  4 location/source/exp/type/date (all null in the request) -> dropped: 0`)
console.log(`  => would be returned: ${stages.kept.length}`)
if (stages.kept.length) stages.kept.forEach(r => console.log(
  `     KEPT [${r.tool}] id=${r.id}\n       "${r.title}" loc=${r.location} score=${r.score} titleHit=${r.titleHit} descHits=${r.descHits}`))
const byTool = {}
rows.forEach(r => { byTool[r.tool] = (byTool[r.tool] || 0) + 1 })
console.log(`  raw per tool: ${Object.entries(byTool).map(([k, v]) => `${k}=${v}`).join(' ')}`)
fs.writeFileSync(`${DIR}/stage-${KEYWORD}.json`, JSON.stringify(stages, null, 2))
