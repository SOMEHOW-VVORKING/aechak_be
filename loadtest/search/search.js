// 키워드 상품 검색 부하 시나리오. 비로그인 요청만 보낸다(검색은 게스트 허용).
//
// SCENARIO
//   smoke       1 VU로 DURATION(기본 30s) 동안 혼합 요청
//   rate        초당 REQ_RATE건(HTTP 요청 기준)으로 DURATION(기본 180s). ramp 단계와 steady가 쓴다
//   spike       REQ_RATE로 SPIKE_SECONDS(30) 몰아 보낸 뒤 PROBE_RATE(2)건씩 PROBE_SECONDS(90) 첫 페이지 탐침
//   sequential  1 VU로 검색어 x 정렬 x 페이지(1, 2)를 SEQ_REPS번씩 차례로
// MIX=mixed(기본)는 첫 페이지 60%, 필터 20%, 페이징 20%(첫 페이지와 다음 페이지 최대 둘)라 반복 하나가 요청 약 1.4건이다.
// MIX=first는 첫 페이지만 보낸다.
// 예) k6 run --tag testid=RUN -e SCENARIO=rate -e REQ_RATE=8 -e DURATION=45s -e SUMMARY_FILE=x.json search.js
import http from 'k6/http'
import exec from 'k6/execution'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080/api/v1'
const SCENARIO = __ENV.SCENARIO || 'smoke'
const MIX = __ENV.MIX || 'mixed'
const REQ_RATE = Number(__ENV.REQ_RATE || 2)
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '10s'
const SPIKE_SECONDS = Number(__ENV.SPIKE_SECONDS || 30)
const PROBE_RATE = Number(__ENV.PROBE_RATE || 2)
const PROBE_SECONDS = Number(__ENV.PROBE_SECONDS || 90)
const SEQ_REPS = Number(__ENV.SEQ_REPS || 20)
const SEQ_KEYWORDS = __ENV.SEQ_KEYWORDS || 'all'
const SEQ_ONLY = __ENV.SEQ_ONLY || ''
const REQS_PER_MIXED_ITER = 1.4
// 페이징 반복은 첫 페이지와 다음 페이지 최대 둘을 차례로 보낸다
const MAX_NEXT_PAGES = 2
const MAX_REQS_PER_MIXED_ITER = 1 + MAX_NEXT_PAGES

if (!['smoke', 'rate', 'spike', 'sequential'].includes(SCENARIO)) throw new Error(`unknown SCENARIO ${SCENARIO}`)
if (!['mixed', 'first'].includes(MIX)) throw new Error(`unknown MIX ${MIX}`)

function seconds(d) {
  const m = /^(\d+(?:\.\d+)?)(ms|s|m)$/.exec(d)
  if (!m) throw new Error(`bad duration ${d}`)
  return Number(m[1]) * { ms: 0.001, s: 1, m: 60 }[m[2]]
}

// 서버가 반복의 모든 요청을 타임아웃까지 붙잡아도 목표 부하를 계속 보낼 수 있는 VU 수
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(20, Math.ceil((REQ_RATE / reqsPerIter(MIX)) * maxReqsPerIter(MIX) * seconds(REQUEST_TIMEOUT) * 1.2)))

// 일치 비율은 seed.sql 머리 주석과 같다. high 47%, mid 6~10%, low 2.5~2.7%, rare 0.1%, none 0%
const KEYWORDS = [
  { kw: '강아지', w: 10, cls: 'high' },
  { kw: '사료', w: 20, cls: 'mid' },
  { kw: '연어', w: 15, cls: 'mid' },
  { kw: '간식', w: 10, cls: 'mid' },
  { kw: '관절', w: 10, cls: 'low' },
  { kw: '하네스', w: 10, cls: 'low' },
  { kw: '바잇미', w: 10, cls: 'low' },
  { kw: '한정판', w: 10, cls: 'rare' },
  { kw: '없는상품', w: 5, cls: 'none' },
]
const CLASSES = ['high', 'mid', 'low', 'rare', 'none']
// SEQ_KEYWORDS=class일 때 등급마다 하나
const CLASS_KEYWORDS = ['강아지', '사료', '관절', '한정판', '없는상품']
const SORTS = ['popular', 'price_asc', 'latest']
const MID_CATEGORIES = [11, 12, 13, 14, 15, 16, 21, 22, 23, 24, 25, 31, 32, 33]
const TOTAL_WEIGHT = KEYWORDS.reduce((sum, k) => sum + k.w, 0)
// 요약에서 하위 지표로 보이는 값. 여기에 없는 값은 Prometheus에만 남고 요약에는 other로 합쳐진다
const KNOWN_STATUS = ['0', '200', '400', '404', '429', '500', '502', '503', '504']
const KNOWN_CODES = ['timeout', 'net', 'na', '90000', '90001', '90002', '40102']

const respStatus = new Counter('resp_status')
const errorCode = new Counter('error_code')

// 반복 하나가 보내는 요청 수로 나눠 HTTP 요청률을 분당 반복 수로 바꾼다. 0.25 req/s도 반올림 오차가 5% 안이다
function iterRate(reqRate, mix) {
  return Math.max(1, Math.round((reqRate * 60) / reqsPerIter(mix)))
}

function reqsPerIter(mix) {
  return mix === 'mixed' ? REQS_PER_MIXED_ITER : 1
}

function maxReqsPerIter(mix) {
  return mix === 'mixed' ? MAX_REQS_PER_MIXED_ITER : 1
}

const SEQ_COMBOS = []
for (const k of KEYWORDS.filter(k => SEQ_KEYWORDS === 'all' || CLASS_KEYWORDS.includes(k.kw))) {
  for (const sort of SORTS) {
    if (!SEQ_ONLY || SEQ_ONLY === `${sort}-${k.cls}`) SEQ_COMBOS.push({ k, sort })
  }
}
if (SCENARIO === 'sequential' && SEQ_COMBOS.length === 0) throw new Error(`SEQ_ONLY ${SEQ_ONLY} matches nothing`)

function arrival(reqRate, mix, duration, extra) {
  return Object.assign({
    executor: 'constant-arrival-rate',
    rate: iterRate(reqRate, mix),
    timeUnit: '1m',
    duration,
    preAllocatedVUs: Math.min(MAX_VUS, Math.max(10, Math.ceil(reqRate * 2))),
    maxVUs: MAX_VUS,
  }, extra)
}

function buildScenarios() {
  switch (SCENARIO) {
    case 'smoke':
      return { smoke: { executor: 'constant-vus', vus: 1, duration: __ENV.DURATION || '30s' } }
    case 'rate':
      return { rate: arrival(REQ_RATE, MIX, __ENV.DURATION || '180s') }
    case 'spike':
      return {
        // burst 시작에 VU를 다 만들어 두지 않으면 첫 몇 초 반복이 dropped로 빠진다
        burst: arrival(REQ_RATE, MIX, `${SPIKE_SECONDS}s`, { tags: { phase: 'burst' }, preAllocatedVUs: MAX_VUS }),
        probe: {
          executor: 'constant-arrival-rate',
          rate: iterRate(PROBE_RATE, 'first'),
          timeUnit: '1m',
          startTime: `${SPIKE_SECONDS}s`,
          duration: `${PROBE_SECONDS}s`,
          // burst 직후 probe 응답이 타임아웃까지 늘어지므로 VU를 미리 다 만들어 둔다
          preAllocatedVUs: Math.max(5, Math.ceil(PROBE_RATE * seconds(REQUEST_TIMEOUT) * 1.2)),
          maxVUs: Math.max(5, Math.ceil(PROBE_RATE * seconds(REQUEST_TIMEOUT) * 1.2)),
          exec: 'probe',
          tags: { phase: 'probe' },
        },
      }
    case 'sequential':
      return {
        sequential: {
          executor: 'per-vu-iterations',
          vus: 1,
          iterations: SEQ_COMBOS.length * SEQ_REPS,
          maxDuration: '3h',
          exec: 'sequential',
        },
      }
  }
}

// 요약 JSON에 태그별 하위 지표가 남도록 거는 기록용 기준(항상 통과)
function recordOnly() {
  const t = {}
  const trend = s => (t[`http_req_duration{${s}}`] = ['max>=0'])
  const rate = s => (t[`http_req_failed{${s}}`] = ['rate>=0'])
  const count = (m, s) => (t[`${m}{${s}}`] = ['count>=0'])
  for (const kind of ['first', 'filtered', 'next', 'probe', 'seq']) {
    trend(`kind:${kind}`)
    rate(`kind:${kind}`)
    count('http_reqs', `kind:${kind}`)
  }
  for (const cls of CLASSES) trend(`kw_class:${cls}`)
  for (const phase of ['burst', 'probe']) {
    trend(`phase:${phase}`)
    rate(`phase:${phase}`)
    count('http_reqs', `phase:${phase}`)
  }
  for (const s of KNOWN_STATUS) count('resp_status', `status:${s}`)
  for (const c of KNOWN_CODES) count('error_code', `code:${c}`)
  if (SCENARIO === 'sequential') {
    for (const cls of CLASSES) for (const sort of SORTS) for (const page of ['1', '2']) {
      trend(`kind:seq,kw_class:${cls},sort:${sort},page:${page}`)
    }
  }
  return t
}

const thresholds = recordOnly()
// 요청 절반 이상이 실패하면 끝까지 기다리지 않고 종료 코드 99로 멈춘다. rate에서만 건다
if (SCENARIO === 'rate') {
  thresholds.http_req_failed = [{ threshold: 'rate<0.5', abortOnFail: true, delayAbortEval: '15s' }]
}

export const options = {
  scenarios: buildScenarios(),
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
}

function pickKeyword() {
  let r = Math.random() * TOTAL_WEIGHT
  for (const k of KEYWORDS) {
    r -= k.w
    if (r <= 0) return k
  }
  return KEYWORDS[KEYWORDS.length - 1]
}

function search(params, tags) {
  const query = Object.entries(params)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join('&')
  const res = http.get(`${BASE_URL}/search/products?${query}`, { tags, timeout: REQUEST_TIMEOUT })
  check(res, { 'status 200': r => r.status === 200 })
  respStatus.add(1, { status: String(res.status) })
  if (res.status < 200 || res.status >= 300) errorCode.add(1, { code: codeOf(res) })
  return res
}

function codeOf(res) {
  if (res.status === 0) return res.error_code === 1050 ? 'timeout' : 'net'
  try {
    const code = JSON.parse(res.body).errorCode
    return code === undefined || code === null ? 'na' : String(code)
  } catch (e) {
    return 'na'
  }
}

function pageData(res) {
  if (res.status !== 200) return null
  try {
    return res.json('data')
  } catch (e) {
    return null
  }
}

function firstPage(k, kind) {
  return search({ keyword: k.kw, size: 20 }, { kind, kw_class: k.cls, sort: 'popular' })
}

function filtered(k) {
  const params = { keyword: k.kw, size: 20, sort: 'price_asc', minPrice: 10000, maxPrice: 80000 }
  if (Math.random() < 0.5) params.category = MID_CATEGORIES[Math.floor(Math.random() * MID_CATEGORIES.length)]
  if (Math.random() < 0.3) params.freeShipping = true
  return search(params, { kind: 'filtered', kw_class: k.cls, sort: 'price_asc' })
}

function paging(k) {
  let res = firstPage(k, 'first')
  for (let page = 0; page < MAX_NEXT_PAGES; page++) {
    const body = pageData(res)
    if (!body || !body.hasNext) return
    res = search({ keyword: k.kw, size: 20, cursor: body.nextCursor }, { kind: 'next', kw_class: k.cls, sort: 'popular' })
  }
}

export default function () {
  const k = pickKeyword()
  if (MIX !== 'mixed') {
    firstPage(k, 'first')
    return
  }
  const r = Math.random()
  if (r < 0.6) firstPage(k, 'first')
  else if (r < 0.8) filtered(k)
  else paging(k)
}

export function probe() {
  firstPage(pickKeyword(), 'probe')
}

// 반복 순번으로 조합을 돌아가며 고르므로 같은 조합이 연달아 캐시를 덥히지 않는다
export function sequential() {
  const { k, sort } = SEQ_COMBOS[exec.scenario.iterationInTest % SEQ_COMBOS.length]
  const tags = { kind: 'seq', kw_class: k.cls, sort }
  const first = search({ keyword: k.kw, size: 20, sort }, Object.assign({ page: '1' }, tags))
  const body = pageData(first)
  if (body && body.hasNext) {
    search({ keyword: k.kw, size: 20, sort, cursor: body.nextCursor }, Object.assign({ page: '2' }, tags))
  }
}

function trendLine(data, metric) {
  const m = data.metrics[metric]
  // 표본이 없는 하위 지표는 값이 전부 0이라 뺀다
  if (!m || !m.values || !m.values.max) return null
  const v = m.values
  const f = x => (x === undefined ? '-' : x.toFixed(0))
  return `${metric}: med=${f(v.med)}ms p90=${f(v['p(90)'])}ms p95=${f(v['p(95)'])}ms p99=${f(v['p(99)'])}ms max=${f(v.max)}ms`
}

function tagCounts(data, metric, tag, known) {
  const total = data.metrics[metric] ? data.metrics[metric].values.count : 0
  let seen = 0
  const parts = []
  for (const v of known) {
    const m = data.metrics[`${metric}{${tag}:${v}}`]
    const c = m ? m.values.count : 0
    seen += c
    if (c) parts.push(`${v}=${c}`)
  }
  if (total - seen) parts.push(`other=${total - seen}`)
  return parts.join(' ') || '-'
}

export function handleSummary(data) {
  const arrivalScenario = SCENARIO === 'rate' || SCENARIO === 'spike'
  const kit = {
    run_id: __ENV.RUN_ID || 'na',
    scenario: SCENARIO,
    mix: MIX,
    req_rate: REQ_RATE,
    iter_rate_per_min: arrivalScenario ? iterRate(REQ_RATE, MIX) : null,
    effective_req_rate: arrivalScenario ? Number(((iterRate(REQ_RATE, MIX) / 60) * reqsPerIter(MIX)).toFixed(3)) : null,
    max_vus: MAX_VUS,
    request_timeout: REQUEST_TIMEOUT,
    spike_seconds: SPIKE_SECONDS,
    probe_rate: PROBE_RATE,
    probe_seconds: PROBE_SECONDS,
    seq_reps: SEQ_REPS,
    seq_keywords: SEQ_KEYWORDS,
    seq_only: SEQ_ONLY,
  }
  const lines = [`run_id=${kit.run_id} scenario=${SCENARIO} mix=${MIX} req_rate=${REQ_RATE} effective_req_rate=${kit.effective_req_rate} max_vus=${MAX_VUS}`]
  const reqs = data.metrics.http_reqs
  const total = reqs ? reqs.values.count : 0
  if (reqs) lines.push(`http_reqs: count=${total} rate=${reqs.values.rate.toFixed(2)}/s`)
  const failed = data.metrics.http_req_failed
  if (failed) lines.push(`http_req_failed: rate=${(failed.values.rate * 100).toFixed(2)}%`)
  const dropped = data.metrics.dropped_iterations
  lines.push(`dropped_iterations: ${dropped ? dropped.values.count : 0}`)
  const share = ['first', 'filtered', 'next', 'probe', 'seq']
    .map(kind => [kind, data.metrics[`http_reqs{kind:${kind}}`]])
    .filter(([, m]) => m && m.values.count)
    .map(([kind, m]) => `${kind}=${m.values.count}(${((m.values.count / total) * 100).toFixed(1)}%)`)
  lines.push(`request_share: ${share.join(' ') || '-'}`)
  lines.push(`status: ${tagCounts(data, 'resp_status', 'status', KNOWN_STATUS)}`)
  lines.push(`error_code: ${tagCounts(data, 'error_code', 'code', KNOWN_CODES)}`)
  for (const name of Object.keys(data.metrics).filter(n => n.startsWith('http_req_duration')).sort()) {
    const line = trendLine(data, name)
    if (line) lines.push(line)
  }
  const text = lines.join('\n') + '\n'
  const out = { stdout: text }
  if (__ENV.TEXT_SUMMARY_FILE) out[__ENV.TEXT_SUMMARY_FILE] = text
  if (__ENV.SUMMARY_FILE) out[__ENV.SUMMARY_FILE] = JSON.stringify(Object.assign({ kit }, data), null, 2)
  return out
}
