import http from 'k6/http'
import { check } from 'k6'
import { Counter, Trend } from 'k6/metrics'

const API_BASE_URL = __ENV.API_URL || 'http://localhost:8000'
const API_ENDPOINT = __ENV.API_ENDPOINT || '/ctr/latest'
const REQUEST_RATE = Number(__ENV.RATE || 10) // requests per second
const TEST_DURATION = __ENV.TEST_DURATION || '60s'
const PRE_ALLOCATED_VUS = Number(__ENV.VUS || 2)
const MAX_VUS = Number(__ENV.MAX_VUS || 20)

export const options = {
  scenarios: {
    ctr_load: {
      executor: 'constant-arrival-rate',
      rate: REQUEST_RATE,
      timeUnit: '1s',
      duration: TEST_DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'], // 95% under 1s
    response_time: ['p(95)<1000'],
  },
}

const responseTime = new Trend('response_time', true)
const windowEndChangeCounter = new Counter('window_end_changes')
const errorCounter = new Counter('ctr_errors')

let previousWindowEnds = null
let lastWindowChangeAt = null
const changeLog = []

function extractWindowEnds(payload) {
  if (!payload || typeof payload !== 'object') {
    return null
  }
  const ends = new Set()
  Object.values(payload).forEach((value) => {
    if (value && typeof value === 'object' && value.window_end !== undefined) {
      ends.add(Number(value.window_end))
    }
  })
  return ends
}

function setsEqual(a, b) {
  if (a === null || b === null) return false
  if (a.size !== b.size) return false
  for (const v of a) {
    if (!b.has(v)) return false
  }
  return true
}

export default function () {
  const start = Date.now()
  const res = http.get(`${API_BASE_URL}${API_ENDPOINT}`, { tags: { endpoint: 'ctr_latest' } })
  const duration = Date.now() - start
  responseTime.add(duration)

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'json body is present': (r) => (r.headers['Content-Type'] || '').includes('application/json'),
  })

  if (!ok) {
    errorCounter.add(1)
    return
  }

  let payload
  try {
    payload = res.json()
  } catch (e) {
    errorCounter.add(1)
    return
  }

  const currentWindowEnds = extractWindowEnds(payload)
  if (currentWindowEnds === null) {
    errorCounter.add(1)
    return
  }

  if (previousWindowEnds === null) {
    previousWindowEnds = currentWindowEnds
    lastWindowChangeAt = Date.now()
    return
  }

  if (!setsEqual(currentWindowEnds, previousWindowEnds)) {
    const now = Date.now()
    const durationMs = now - (lastWindowChangeAt || now)
    const newWindows = Array.from(currentWindowEnds).filter((end) => !previousWindowEnds.has(end)).sort()
    const removedWindows = Array.from(previousWindowEnds).filter((end) => !currentWindowEnds.has(end)).sort()

    changeLog.push({
      changeNumber: changeLog.length + 1,
      timestamp: new Date(now).toISOString(),
      callNumber: __ITER + 1,
      prevWindowEnds: Array.from(previousWindowEnds).sort(),
      currentWindowEnds: Array.from(currentWindowEnds).sort(),
      newWindows,
      removedWindows,
      durationMs,
    })

    windowEndChangeCounter.add(1)
    previousWindowEnds = currentWindowEnds
    lastWindowChangeAt = now
  }
}

export function handleSummary(data) {
  const metrics = data.metrics || {}
  const totalCalls = metrics.http_reqs ? metrics.http_reqs.values.count : 0
  const totalErrors = metrics.ctr_errors ? metrics.ctr_errors.values.count : 0
  const successRate = totalCalls > 0 ? ((totalCalls - totalErrors) / totalCalls) * 100 : 0
  const latencyMetric = metrics.response_time || metrics.http_req_duration || { values: {} }
  const { avg, min, max, median } = latencyMetric.values || {}
  const p95 = latencyMetric.values ? latencyMetric.values['p(95)'] : undefined
  const cps = (() => {
    const seconds = durationToSeconds(TEST_DURATION)
    return seconds ? totalCalls / seconds : totalCalls
  })()

  const finalWindowDurationMs = lastWindowChangeAt ? Date.now() - lastWindowChangeAt : 0

  const lines = []
  lines.push('WINDOW_END CHANGES')
  lines.push(`- detected: ${changeLog.length}`)
  changeLog.forEach((change) => {
    lines.push(`  #${change.changeNumber} @ ${change.timestamp} (call ${change.callNumber})`)
    lines.push(`    prev -> ${JSON.stringify(change.prevWindowEnds)}`)
    lines.push(`    curr -> ${JSON.stringify(change.currentWindowEnds)}`)
    lines.push(`    duration: ${change.durationMs.toFixed(0)}ms`)
    if (change.newWindows.length) {
      lines.push(`    new: ${JSON.stringify(change.newWindows)}`)
    }
    if (change.removedWindows.length) {
      lines.push(`    removed: ${JSON.stringify(change.removedWindows)}`)
    }
  })
  if (finalWindowDurationMs > 0) {
    lines.push(`- final window duration until test end: ${finalWindowDurationMs.toFixed(0)}ms`)
  }

  lines.push('')
  lines.push('PERFORMANCE METRICS')
  lines.push(`- total calls: ${totalCalls}`)
  lines.push(`- total errors: ${totalErrors}`)
  lines.push(`- success rate: ${successRate.toFixed(1)}%`)
  lines.push(`- calls per second (approx): ${cps.toFixed(1)}`)

  if (avg !== undefined) {
    lines.push('')
    lines.push('RESPONSE TIME (ms)')
    lines.push(`- avg: ${avg.toFixed(2)}`)
    lines.push(`- min: ${min.toFixed(2)}`)
    lines.push(`- max: ${max.toFixed(2)}`)
    lines.push(`- median: ${median.toFixed(2)}`)
    if (p95 !== undefined) {
      lines.push(`- p95: ${p95.toFixed(2)}`)
    }
  }

  return {
    stdout: `${lines.join('\n')}`,
  }
}

function durationToSeconds(value) {
  if (!value || typeof value !== 'string') return null
  const match = value.match(/^(\\d+)(ms|s|m|h)$/)
  if (!match) return null
  const [, numeric, unit] = match
  const number = Number(numeric)
  switch (unit) {
    case 'ms':
      return number / 1000
    case 's':
      return number
    case 'm':
      return number * 60
    case 'h':
      return number * 3600
    default:
      return null
  }
}
