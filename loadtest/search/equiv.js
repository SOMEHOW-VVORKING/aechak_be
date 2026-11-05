// 검색 결과 동치 확인. 1 VU로 검색어 x 정렬 x 필터마다 첫 3페이지를 차례로 받아
// 한 페이지에 한 줄씩 `EQUIV {"kw","sort","filter","page","status","totalCount","ids","hasNext"}`를 로그로 찍는다.
// 예) k6 run --log-format raw -e BASE_URL=... equiv.js 2>&1 | sed -n 's/^EQUIV //p' > results/equiv/<step>-<dataset>-<engine>.jsonl
import http from 'k6/http'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080/api/v1'
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '60s'
const PAGES = 3

// 앞 9개는 search.js의 혼합 검색어, 뒤는 경계 사례
const KEYWORDS = [
  '강아지', '사료', '연어', '간식', '관절', '하네스', '바잇미', '한정판', '없는상품',
  '개',
  '강아지 사료',
  '강아지', // 강아지의 NFD 자모 표기
  '강아지사료'.repeat(20), // 검색어 최대 길이 100자
  '%',
  '_',
  '+강아지 -사료',
  '"연어"',
]
const SORTS = ['popular', 'price_asc', 'latest']
const FILTERS = {
  none: {},
  price: { minPrice: 10000, maxPrice: 80000 },
  category: { category: 11 },
  freeShipping: { freeShipping: true },
}

export const options = {
  scenarios: { equiv: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '3h' } },
}

function get(params) {
  const query = Object.entries(params)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join('&')
  return http.get(`${BASE_URL}/search/products?${query}`, { timeout: REQUEST_TIMEOUT })
}

export default function () {
  for (const kw of KEYWORDS) {
    for (const sort of SORTS) {
      for (const [filter, extra] of Object.entries(FILTERS)) {
        let cursor = null
        for (let page = 1; page <= PAGES; page++) {
          const params = Object.assign({ keyword: kw, sort, size: 20 }, extra)
          if (cursor) params.cursor = cursor
          const res = get(params)
          let data = null
          try {
            data = res.status === 200 ? res.json('data') : null
          } catch (e) {
            data = null
          }
          console.log('EQUIV ' + JSON.stringify({
            kw,
            sort,
            filter,
            page,
            status: res.status,
            totalCount: data ? data.totalCount : null,
            ids: data ? data.products.map(p => p.productId) : [],
            hasNext: data ? data.hasNext : false,
          }))
          if (!data || !data.hasNext) break
          cursor = data.nextCursor
        }
      }
    }
  }
}
