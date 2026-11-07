// DB만 쓰는 대조 요청. search.js가 CONTROL_RATE > 0일 때 rate와 spike 시나리오 옆에 같은 길이로 더한다.
// GET /products/{publicId}(비로그인 상품 상세)는 상품, 이미지, 통계를 MySQL에서 키로 읽고 ES와 Redis는 부르지 않는다.
// 상품은 seed.sql의 n = 3(판매중, 검수 승인, 활성 셀러)이라 모든 데이터셋에서 같은 행을 읽는다.
import http from 'k6/http'
import { check } from 'k6'

const CONTROL_PRODUCT = __ENV.CONTROL_PRODUCT || '01PERF00000000000000000004'

// 응답이 타임아웃까지 늘어져도 대조 요청이 dropped로 빠지지 않을 만큼 VU를 미리 만든다
export function controlScenario(rate, duration, timeoutSeconds) {
  const vus = Math.max(2, Math.ceil(rate * timeoutSeconds * 1.2) + 1)
  return {
    executor: 'constant-arrival-rate',
    rate: Math.max(1, Math.round(rate * 60)),
    timeUnit: '1m',
    duration,
    preAllocatedVUs: vus,
    maxVUs: vus,
    gracefulStop: `${Math.max(30, timeoutSeconds)}s`,
    exec: 'control',
    tags: { kind: 'control' },
  }
}

export function control(baseUrl, timeout) {
  const res = http.get(`${baseUrl}/products/${CONTROL_PRODUCT}`, { tags: { kind: 'control', name: 'control' }, timeout })
  check(res, { 'control 200': r => r.status === 200 })
}
