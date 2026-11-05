# SEARCH-T04 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-05 100k like

like 기준 요청률 2 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T04-100k-steady-like-r1` | steady | like | 100k | 1.4 | 1.40 | 545/700/760/1,094 (n=145) | 515/686/736/1,094 (n=253) | 0.00% | 200:253 | - | 0 | 33.0/34.1 | 10/10 | 0.4732 | 0 | SELECT products 37%, COUNT products 34%, SELECT products 20% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-steady-like-r2` | steady | like | 100k | 1.4 | 1.38 | 551/737/1,089/1,904 (n=145) | 524/713/837/1,904 (n=249) | 0.00% | 200:249 | - | 0 | 34.1/34.8 | 10/10 | 0.4948 | 0 | SELECT products 38%, COUNT products 33%, SELECT products 18% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-steady-like-r3` | steady | like | 100k | 1.4 | 1.37 | 545/721/780/804 (n=144) | 517/704/772/804 (n=247) | 0.00% | 200:247 | - | 0 | 30.7/34.6 | 10/11 | 0.4489 | 0 | SELECT products 37%, COUNT products 33%, SELECT products 18% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-explain-like-r1` | explain | like | 100k | - | - | - | - | - | - | - | 0 | ≈19.2/22.1 | 11/11 | - | 2 (디스크 읽기) | COUNT products 28%, COUNT products 14%, EXPLAIN products 10% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| like | 100k | 3 | 721 | 700~737 | 5.1% |


## 2026-11-05 100k match

match 기준 요청률 2 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T04-100k-ramp-match-r1` | ramp (dropped는 마지막 단계) | match | 100k | SLO 미충족 (처리 가능 8) | - | - | - | - | - | - | 0 | - | - | - | 0 | SELECT products 40%, COUNT products 37%, SELECT products 11% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-steady-match-r1` | steady | match | 100k | 1.4 | 1.41 | 111/1,313/1,358/1,564 (n=145) | 92/1,301/1,353/1,564 (n=253) | 0.00% | 200:253 | - | 0 | 16.2/16.5 | 10/10 | 0.2315 | 0 | SELECT products 35%, COUNT products 32%, SELECT products 20% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-steady-match-r2` | steady | match | 100k | 1.4 | 1.38 | 125/1,320/1,393/1,463 (n=145) | 93/1,312/1,365/1,463 (n=249) | 0.00% | 200:249 | - | 0 | 15.6/15.8 | 10/10 | 0.2253 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 17% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-steady-match-r3` | steady | match | 100k | 1.4 | 1.37 | 134/1,322/1,377/1,380 (n=144) | 93/1,302/1,364/1,380 (n=247) | 0.00% | 200:247 | - | 0 | 14.1/14.4 | 10/10 | 0.2054 | 0 | SELECT products 37%, COUNT products 33%, SELECT products 19% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-spike-match-r1` | spike | match | 100k | 6 | 2.24 | 162/1,511/2,591/2,773 (n=103) | 129/1,329/1,914/2,773 (n=269) | 0.00% | 200:269 | - | 0 | 39.8/39.8 | 10/10 | ≈0.3561 | 0 | SELECT products 40%, COUNT products 36%, SELECT products 17% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-sequential-match-r1` | sequential | match | 100k | - | 6.59 | - | 54/1,082/1,282/1,328 (n=1,020) | 0.00% | 200:1020 | - | 0 | 50.5/50.5 | 10/10 | ≈0.1536 | 0 | COUNT products 36%, SELECT products 14%, SELECT products 13% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-100k-explain-match-r1` | explain | match | 100k | - | - | - | - | - | - | - | 0 | ≈21.9/22.6 | 10/10 | - | 0 | COUNT products 28%, COUNT products 12%, EXPLAIN products 11% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| match | 100k | 3 | 1320 | 1313~1322 | 0.7% |

### ramp `2026-11-05-SEARCH-T04-100k-ramp-match-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 8 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 2.02 | 1310.21 | 표본 부족(n=52) | 0 | 0 | ≈12.5 | 0 | 처리 기준만 |
| 4 | 3.92 | 1382.28 | 1678.65 (n=103) | 0 | 0 | ≈33.9 | 0 | 처리 기준만 |
| 8 | 7.72 | 2771.68 | 4026.5 (n=206) | 0 | 0 | ≈64.4 | 0 | 처리 기준만 |
| 16 | 9.51 | 10000.69 | 10001 (n=346) | 38.81 | 81 | ≈87.8 | 0 | 없음 |
| 1 | 0.99 | 1009.3 | 표본 부족(n=26) | 0 | 0 | ≈15.0 | 0 | 처리 기준만 |
| 0.5 | 0.49 | 1291.69 | 표본 부족(n=16) | 0 | 0 | ≈4.8 | 0 | 처리 기준만 |
| 0.25 | 0.26 | 1295.85 | 표본 부족(n=16) | 0 | 0 | 4.9 | 0 | 처리 기준만 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T04-100k-spike-match-r1` | 6 | 1 | 0.00% | 0.00% | 1585ms | 2초 (회복 창 5s) | 0 |

### sequential `2026-11-05-SEARCH-T04-100k-sequential-match-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 1,283 | 1,320 | 1,328 |
| high | popular | 2 | 670 | 682 | 731 |
| high | price_asc | 1 | 1,101 | 1,132 | 1,168 |
| high | price_asc | 2 | 502 | 515 | 593 |
| high | latest | 1 | 1,084 | 1,106 | 1,176 |
| high | latest | 2 | 485 | 497 | 570 |
| mid | popular | 1 | 159 | 162 | 172 |
| mid | popular | 2 | 89 | 91 | 94 |
| mid | price_asc | 1 | 121 | 123 | 125 |
| mid | price_asc | 2 | 52 | 54 | 58 |
| mid | latest | 1 | 119 | 122 | 142 |
| mid | latest | 2 | 48 | 50 | 53 |
| low | popular | 1 | 75 | 100 | 103 |
| low | popular | 2 | 41 | 54 | 58 |
| low | price_asc | 1 | 64 | 85 | 89 |
| low | price_asc | 2 | 31 | 41 | 44 |
| low | latest | 1 | 63 | 85 | 87 |
| low | latest | 2 | 31 | 40 | 42 |
| rare | popular | 1 | 9 | 10 | 17 |
| rare | popular | 2 | 6 | 6 | 13 |
| rare | price_asc | 1 | 7 | 10 | 12 |
| rare | price_asc | 2 | 5 | 6 | 13 |
| rare | latest | 1 | 7 | 8 | 10 |
| rare | latest | 2 | 5 | 6 | 6 |
| none | popular | 1 | 5 | 6 | 11 |
| none | price_asc | 1 | 4 | 6 | 6 |
| none | latest | 1 | 4 | 5 | 5 |


## 2026-11-05 1m like

like 기준 요청률 0.25 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T04-1m-steady-like-r1` | steady | like | 1m | 0.175 | 0.19 | 6,366/8,623/표본 부족(n=20)/10,152 | 6,063/8,023/표본 부족(n=35)/10,152 | 0.00% | 200:35 | - | 0 | 46.5/58.7 | 10/10 | 4.9839 | 5 (디스크 읽기) | SELECT products 36%, COUNT products 33%, SELECT products 20% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-1m-steady-like-r2` | steady | like | 1m | 0.175 | 0.19 | 6,359/7,787/표본 부족(n=20)/8,650 | 6,012/7,684/표본 부족(n=35)/8,650 | 0.00% | 200:35 | - | 0 | 49.6/50.6 | 10/10 | 5.2697 | 3 (디스크 읽기) | SELECT products 35%, COUNT products 32%, SELECT products 20% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-1m-steady-like-r3` | steady | like | 1m | 0.175 | 0.18 | 6,471/9,165/표본 부족(n=20)/10,224 | 6,204/8,757/표본 부족(n=33)/10,224 | 0.00% | 200:33 | - | 0 | 42.8/54.8 | 10/10 | 4.8503 | 2 (디스크 읽기) | SELECT products 36%, COUNT products 33%, SELECT products 17% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T04-1m-explain-like-r1` | explain | like | 1m | - | - | - | - | - | - | - | 0 | 47.8/51.8 | 11/11 | - | 3 (디스크 읽기) | COUNT products 28%, COUNT products 14%, EXPLAIN products 10% | 9c715056a644 | ed059d04 | SEARCH-T02-fulltext |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| like | 1m | 3 | 8623 | 7787~9165 | 16.0% |

