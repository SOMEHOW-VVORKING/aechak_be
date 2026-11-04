# SEARCH-T00 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-04 100k base

base 기준 요청률 2 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T00-100k-smoke-base-r1` | smoke | base | 100k | - | 2.19 | 530/749/표본 부족(n=38)/782 | 506/741/표본 부족(n=66)/782 | 0.00% | 200:66 | - | 0 | ≈22.3/22.3 | 10/10 | ≈0.2053 | 0 | SELECT products 37%, COUNT products 34%, SELECT products 19% | 6dce8608b83c | 22eeed43 | none |
| `2026-11-04-SEARCH-T00-100k-ramp-base-r1` | ramp (dropped는 마지막 단계) | base | 100k | SLO 미충족 (처리 가능 2) | - | - | - | - | - | - | 0 | - | - | - | 24 (디스크 읽기) | SELECT products 34%, COUNT products 31%, SELECT products 17% | 6dce8608b83c | 22eeed43 | none |
| `2026-11-04-SEARCH-T00-100k-steady-base-r1` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.38 | 547/742/777/794 (n=149) | 510/739/760/794 (n=249) | 0.00% | 200:249 | - | 0 | 32.9/36.7 | 10/10 | 0.4774 | 14 (디스크 읽기) | SELECT products 37%, COUNT products 34%, SELECT products 18% | 6dce8608b83c | 22eeed43 | none |
| `2026-11-04-SEARCH-T00-100k-steady-base-r2` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.27 | 543/744/787/906 (n=153) | 507/740/770/906 (n=229) | 0.00% | 200:229 | - | 0 | 24.8/33.9 | 10/10 | 0.3924 | 0 | SELECT products 39%, COUNT products 36%, SELECT products 15% | 6dce8608b83c | 22eeed43 | none |
| `2026-11-04-SEARCH-T00-100k-steady-base-r3` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.46 | 545/740/765/789 (n=141) | 504/707/758/789 (n=265) | 0.00% | 200:265 | - | 0 | 34.6/38.1 | 10/10 | 0.4733 | 0 | SELECT products 34%, COUNT products 31%, SELECT products 21% | 6dce8608b83c | 22eeed43 | none |
| `2026-11-04-SEARCH-T00-100k-spike-base-r1` | spike (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 6 | 1.86 | 10,000/10,001/표본 부족(n=99)/10,001 | 7,123/10,001/10,001/10,001 (n=224) | 40.18% | 0:90 200:134 | timeout:90 | 5 (부하 미달) | 79.0/79.0 | 10/10 | ≈0.8513 | 0 | SELECT products 42%, COUNT products 40%, SELECT products 4% | 6dce8608b83c | 22eeed43 | none |
| `2026-11-04-SEARCH-T00-100k-sequential-base-r1` | sequential | base | 100k | - | 2.37 | - | 479/730/740/835 (n=1,020) | 0.00% | 200:1020 | - | 0 | 48.7/51.6 | 10/10 | ≈0.4114 | 0 | COUNT products 32%, SELECT products 12%, SELECT products 12% | 6dce8608b83c | 22eeed43 | none |
| `2026-11-04-SEARCH-T00-100k-explain-base-r1` | explain | base | 100k | - | - | - | - | - | - | - | 0 | ≈28.5/28.7 | 10/10 | - | 2 (디스크 읽기) | COUNT products 28%, COUNT products 13%, EXPLAIN products 10% | 6dce8608b83c | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base | 100k | 3 | 742 | 740~744 | 0.6% |

### ramp `2026-11-04-SEARCH-T00-100k-ramp-base-r1`

통과한 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 2 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 |
|---|---|---|---|---|---|---|---|---|
| 2 | 1.85 | 763.35 | 표본 부족(n=48) | 0 | 0 | ≈23.0 | 0 | 아니오 |
| 1 | 1.02 | 788.31 | 표본 부족(n=27) | 0 | 0 | ≈27.3 | 0 | 아니오 |
| 0.5 | 0.44 | 740.34 | 표본 부족(n=12) | 0 | 0 | ≈12.1 | 0 | 아니오 |
| 0.25 | 0.28 | 719.78 | 표본 부족(n=5) | 0 | 0 | ≈8.3 | 0 | 아니오 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T00-100k-spike-base-r1` | 6 | 1 | 55.80% | 15.12% | 891ms | 31초 (회복 창 5s) | 5 (부하 미달) |

### sequential `2026-11-04-SEARCH-T00-100k-sequential-base-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 734 | 748 | 835 |
| high | popular | 2 | 405 | 410 | 429 |
| high | price_asc | 1 | 734 | 750 | 786 |
| high | price_asc | 2 | 409 | 415 | 454 |
| high | latest | 1 | 735 | 738 | 799 |
| high | latest | 2 | 419 | 424 | 459 |
| mid | popular | 1 | 541 | 549 | 562 |
| mid | popular | 2 | 284 | 291 | 343 |
| mid | price_asc | 1 | 542 | 550 | 641 |
| mid | price_asc | 2 | 287 | 292 | 336 |
| mid | latest | 1 | 541 | 554 | 606 |
| mid | latest | 2 | 302 | 307 | 377 |
| low | popular | 1 | 501 | 510 | 544 |
| low | popular | 2 | 260 | 265 | 325 |
| low | price_asc | 1 | 501 | 512 | 599 |
| low | price_asc | 2 | 262 | 266 | 272 |
| low | latest | 1 | 502 | 514 | 657 |
| low | latest | 2 | 276 | 285 | 349 |
| rare | popular | 1 | 482 | 489 | 531 |
| rare | popular | 2 | 248 | 251 | 269 |
| rare | price_asc | 1 | 483 | 492 | 505 |
| rare | price_asc | 2 | 249 | 258 | 442 |
| rare | latest | 1 | 481 | 488 | 517 |
| rare | latest | 2 | 216 | 219 | 220 |
| none | popular | 1 | 481 | 489 | 535 |
| none | price_asc | 1 | 480 | 490 | 581 |
| none | latest | 1 | 480 | 496 | 509 |


## 2026-11-04 100k kit2

kit2 기준 요청률 2 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T00-100k-ramp-kit2-r1` | ramp (dropped는 마지막 단계) | kit2 | 100k | SLO 미충족 (처리 가능 2) | - | - | - | - | - | - | 7 | - | - | - | 0 | SELECT products 40%, COUNT products 36%, SELECT products 13% | 6dce8608b83c | 22eeed43 | none |
| `2026-11-04-SEARCH-T00-100k-spike-kit2-r1` | spike (SLO 미충족, 비교 부하 2 req/s) | kit2 | 100k | 6 | 1.93 | 10,000/10,001/10,001/10,007 (n=104) | 7,512/10,001/10,001/10,007 (n=233) | 37.34% | 0:87 200:146 | timeout:87 | 5 (부하 미달) | 84.0/84.0 (CW 미완) | 10/10 | ≈0.8720 (CW 미완) | 0 | SELECT products 44%, COUNT products 40%, SELECT products 5% | 6dce8608b83c | 22eeed43 | none |

### ramp `2026-11-04-SEARCH-T00-100k-ramp-kit2-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 2 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 2.05 | 828.67 | 표본 부족(n=55) | 0 | 0 | ≈31.9 | 0 | 처리 기준만 |
| 4 | 3.14 | 5529.87 | 6599.92 (n=100) | 0 | 7 | ≈64.7 | 0 | 없음 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T00-100k-spike-kit2-r1` | 6 | 1 | 51.70% | 12.79% | 500ms | 90초 안에 회복 안 됨 (회복 창 5s) | 5 (부하 미달) |

