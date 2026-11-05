# SEARCH-T02 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-04 100k base

base 기준 요청률 2 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-smoke-base-r1` | smoke | base | 100k | - | 2.03 | 592/804/표본 부족(n=36)/916 | 549/791/표본 부족(n=62)/916 | 0.00% | 200:62 | - | 0 | ≈34.4/51.9 | 10/10 | ≈0.3404 | 0 | SELECT products 38%, COUNT products 34%, SELECT products 19% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-ramp-base-r1` | ramp (dropped는 마지막 단계) | base | 100k | SLO 미충족 (처리 가능 2) | - | - | - | - | - | - | 0 | - | - | - | 14 (디스크 읽기) | SELECT products 36%, COUNT products 34%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-steady-base-r1` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.40 | 580/794/835/912 (n=145) | 536/777/823/912 (n=253) | 0.00% | 200:253 | - | 0 | 35.8/37.5 | 10/10 | 0.5134 | 0 | SELECT products 36%, COUNT products 32%, SELECT products 19% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-steady-base-r2` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.38 | 580/791/862/917 (n=145) | 547/779/848/917 (n=249) | 0.00% | 200:249 | - | 0 | 26.8/36.7 | 10/10 | 0.3897 | 0 | SELECT products 37%, COUNT products 32%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-steady-base-r3` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.37 | 579/785/878/960 (n=144) | 539/780/852/960 (n=247) | 0.00% | 200:247 | - | 0 | 32.2/36.7 | 10/10 | 0.4698 | 0 | SELECT products 37%, COUNT products 32%, SELECT products 17% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-spike-base-r1` | spike (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 6 | 1.97 | 10,000/10,001/10,001/10,001 (n=103) | 8,695/10,001/10,001/10,001 (n=237) | 44.73% | 0:106 200:131 | timeout:106 | 0 | 30.6/30.6 | 10/10 | ≈0.3120 | 0 | SELECT products 44%, COUNT products 41%, SELECT products 5% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-sequential-base-r1` | sequential | base | 100k | - | 2.22 | - | 505/777/794/921 (n=1,020) | 0.00% | 200:1020 | - | 0 | 48.5/51.6 | 10/11 | ≈0.4377 | 0 | COUNT products 32%, SELECT products 12%, SELECT products 12% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-explain-base-r1` | explain | base | 100k | - | - | - | - | - | - | - | 0 | ≈28.7/51.8 | 10/10 | - | 2 (디스크 읽기) | COUNT products 28%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base | 100k | 3 | 791 | 785~794 | 1.1% |

### ramp `2026-11-04-SEARCH-T02-100k-ramp-base-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 2 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 1.98 | 825.09 | 표본 부족(n=52) | 0 | 0 | ≈23.8 | 0 | 처리 기준만 |
| 4 | 3.15 | 7061.4 | 표본 부족(n=95) | 0 | 11 | ≈55.1 | 0 | 없음 |
| 1 | 0.99 | 789.14 | 표본 부족(n=26) | 0 | 0 | ≈39.7 | 0 | 처리 기준만 |
| 0.5 | 0.49 | 783.63 | 표본 부족(n=16) | 0 | 0 | ≈16.5 | 0 | 처리 기준만 |
| 0.25 | 0.26 | 788.68 | 표본 부족(n=16) | 0 | 0 | 6.5 | 0 | 처리 기준만 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-spike-base-r1` | 6 | 1 | 58.90% | 21.98% | 949ms | 37초 (회복 창 5s) | 0 |

### sequential `2026-11-04-SEARCH-T02-100k-sequential-base-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 785 | 794 | 797 |
| high | popular | 2 | 432 | 451 | 497 |
| high | price_asc | 1 | 788 | 794 | 824 |
| high | price_asc | 2 | 436 | 443 | 498 |
| high | latest | 1 | 788 | 814 | 921 |
| high | latest | 2 | 449 | 457 | 559 |
| mid | popular | 1 | 574 | 594 | 634 |
| mid | popular | 2 | 304 | 316 | 396 |
| mid | price_asc | 1 | 575 | 594 | 687 |
| mid | price_asc | 2 | 305 | 317 | 409 |
| mid | latest | 1 | 574 | 595 | 714 |
| mid | latest | 2 | 323 | 334 | 404 |
| low | popular | 1 | 535 | 557 | 657 |
| low | popular | 2 | 279 | 285 | 308 |
| low | price_asc | 1 | 534 | 547 | 585 |
| low | price_asc | 2 | 280 | 290 | 510 |
| low | latest | 1 | 532 | 547 | 636 |
| low | latest | 2 | 296 | 305 | 333 |
| rare | popular | 1 | 513 | 559 | 616 |
| rare | popular | 2 | 264 | 275 | 292 |
| rare | price_asc | 1 | 512 | 529 | 586 |
| rare | price_asc | 2 | 268 | 273 | 279 |
| rare | latest | 1 | 517 | 526 | 562 |
| rare | latest | 2 | 228 | 234 | 237 |
| none | popular | 1 | 510 | 523 | 567 |
| none | price_asc | 1 | 513 | 522 | 524 |
| none | latest | 1 | 512 | 535 | 615 |


## 2026-11-04 100k visible

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-explain-visible-r1` | explain | visible | 100k | - | - | - | - | - | - | - | 0 | ≈35.8/37.5 | 11/11 | - | 0 | COUNT products 28%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-visible |


## 2026-11-04 100k visible-name

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-explain-visible-name-r1` | explain | visible-name | 100k | - | - | - | - | - | - | - | 0 | ≈32.2/34.1 | 10/10 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-visible-name |


## 2026-11-04 100k stats-review

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-explain-stats-review-r1` | explain | stats-review | 100k | - | - | - | - | - | - | - | 0 | ≈33.2/52.8 | 10/10 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-stats-review |


## 2026-11-04 100k sellers-active

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-explain-sellers-active-r1` | explain | sellers-active | 100k | - | - | - | - | - | - | - | 0 | ≈36.6/44.0 | 11/11 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-sellers-active |


## 2026-11-04 100k sellers-active-load

sellers-active-load 기준 요청률 2 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-steady-sellers-active-load-r1` | steady | sellers-active-load | 100k | 1.4 | 1.40 | 599/801/869/983 (n=145) | 554/793/835/983 (n=253) | 0.00% | 200:253 | - | 0 | 36.6/38.2 | 10/10 | 0.5248 | 0 | SELECT products 37%, COUNT products 32%, SELECT products 19% | 8b9454805771 | 22eeed43 | SEARCH-T02-sellers-active |
| `2026-11-04-SEARCH-T02-100k-steady-sellers-active-load-r2` | steady | sellers-active-load | 100k | 1.4 | 1.38 | 591/809/859/1,133 (n=145) | 553/798/853/1,133 (n=249) | 0.00% | 200:249 | - | 0 | 27.2/37.5 | 10/10 | 0.3957 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 18% | 8b9454805771 | 22eeed43 | SEARCH-T02-sellers-active |
| `2026-11-04-SEARCH-T02-100k-steady-sellers-active-load-r3` | steady | sellers-active-load | 100k | 1.4 | 1.37 | 586/801/853/917 (n=144) | 547/797/847/917 (n=247) | 0.00% | 200:247 | - | 0 | 32.7/37.0 | 10/10 | 0.4776 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 18% | 8b9454805771 | 22eeed43 | SEARCH-T02-sellers-active |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| sellers-active-load | 100k | 3 | 801 | 801~809 | 1.0% |


## 2026-11-04 100k base2

base2 기준 요청률 2 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-steady-base2-r1` | steady | base2 | 100k | 1.4 | 1.40 | 598/802/863/1,002 (n=145) | 555/792/854/1,002 (n=253) | 0.00% | 200:253 | - | 0 | 29.7/37.8 | 10/10 | 0.4254 | 0 | SELECT products 37%, COUNT products 32%, SELECT products 19% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-steady-base2-r2` | steady | base2 | 100k | 1.4 | 1.38 | 586/803/862/928 (n=145) | 549/787/841/928 (n=249) | 0.00% | 200:249 | - | 0 | 36.4/37.0 | 10/10 | 0.5288 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-100k-steady-base2-r3` | steady | base2 | 100k | 1.4 | 1.37 | 593/810/890/909 (n=144) | 556/801/865/909 (n=247) | 0.00% | 200:247 | - | 0 | 27.0/35.6 | 10/10 | 0.3951 | 0 | SELECT products 36%, COUNT products 32%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base2 | 100k | 3 | 803 | 802~810 | 0.9% |


## 2026-11-04 100k fulltext

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-100k-explain-fulltext-r1` | explain | fulltext | 100k | - | - | - | - | - | - | - | 0 | ≈25.9/50.1 | 10/10 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-fulltext |


## 2026-11-04 1m base

base 기준 요청률 0.25 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-1m-ramp-base-r1` | ramp (dropped는 마지막 단계) | base | 1m | SLO 미충족 (처리 가능 0.25) | - | - | - | - | - | - | 0 | - | - | - | 39 (디스크 읽기) | SELECT products 42%, COUNT products 39%, SELECT products 5% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-1m-steady-base-r1` | steady (SLO 미충족, 비교 부하 0.25 req/s) | base | 1m | 0.175 | 0.19 | 6,675/9,636/표본 부족(n=20)/11,125 | 6,140/8,507/표본 부족(n=35)/11,125 | 0.00% | 200:35 | - | 0 | 54.2/56.3 | 10/10 | 5.8172 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 19% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-1m-steady-base-r2` | steady (SLO 미충족, 비교 부하 0.25 req/s) | base | 1m | 0.175 | 0.19 | 6,627/8,503/표본 부족(n=20)/9,543 | 6,199/8,205/표본 부족(n=35)/9,543 | 0.00% | 200:35 | - | 0 | 59.5/62.5 | 10/10 | 6.3451 | 0 | SELECT products 34%, COUNT products 32%, SELECT products 20% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-1m-steady-base-r3` | steady (SLO 미충족, 비교 부하 0.25 req/s) | base | 1m | 0.175 | 0.18 | 6,693/9,529/표본 부족(n=20)/11,145 | 6,323/9,435/표본 부족(n=33)/11,145 | 0.00% | 200:33 | - | 0 | 47.3/47.9 | 10/10 | 5.3589 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 17% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-1m-explain-base-r1` | explain | base | 1m | - | - | - | - | - | - | - | 0 | 51.0/51.6 | 11/12 | - | 3 (디스크 읽기) | COUNT products 29%, COUNT products 14%, EXPLAIN products 11% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base | 1m | 3 | 9529 | 8503~9636 | 11.9% |

### ramp `2026-11-04-SEARCH-T02-1m-ramp-base-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 0.25 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 0.12 | 29999.74 | 표본 부족(n=3) | 50 | 17 | ≈47.7 | 99 | 없음 |
| 1 | 0.2 | 30000.53 | 표본 부족(n=7) | 50 | 7 | ≈50.6 | 99 | 없음 |
| 0.5 | 0.24 | 30000.75 | 표본 부족(n=12) | 50 | 1 | ≈73.1 | 99 | 없음 |
| 0.25 | 0.24 | 11277.83 | 표본 부족(n=16) | 0 | 0 | 83.8 | 0 | 처리 기준만 |
| 0.125 | 0.11 | 9075.75 | 표본 부족(n=16) | 0 | 0 | 35.3 | 0 | 처리 기준만 |


## 2026-11-04 1m visible

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-1m-explain-visible-r1` | explain | visible | 1m | - | - | - | - | - | - | - | 0 | 50.5/51.0 | 11/12 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-visible |


## 2026-11-04 1m visible-name

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-1m-explain-visible-name-r1` | explain | visible-name | 1m | - | - | - | - | - | - | - | 0 | 48.9/51.8 | 11/11 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-visible-name |


## 2026-11-04 1m stats-review

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-1m-explain-stats-review-r1` | explain | stats-review | 1m | - | - | - | - | - | - | - | 0 | 51.0/51.6 | 11/11 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-stats-review |


## 2026-11-04 1m sellers-active

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-1m-explain-sellers-active-r1` | explain | sellers-active | 1m | - | - | - | - | - | - | - | 0 | 50.0/50.8 | 10/11 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-sellers-active |


## 2026-11-04 1m sellers-active-load

sellers-active-load 기준 요청률 0.25 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-1m-steady-sellers-active-load-r1` | steady | sellers-active-load | 1m | 0.175 | 0.19 | 6,599/9,478/표본 부족(n=20)/11,032 | 6,131/8,280/표본 부족(n=35)/11,032 | 0.00% | 200:35 | - | 0 | 54.4/55.2 | 10/10 | 5.8349 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 20% | 8b9454805771 | 22eeed43 | SEARCH-T02-sellers-active |
| `2026-11-04-SEARCH-T02-1m-steady-sellers-active-load-r2` | steady | sellers-active-load | 1m | 0.175 | 0.19 | 6,582/7,985/표본 부족(n=20)/9,323 | 6,183/7,967/표본 부족(n=35)/9,323 | 0.00% | 200:35 | - | 0 | 56.1/63.4 | 10/10 | 5.9695 | 0 | SELECT products 34%, COUNT products 32%, SELECT products 20% | 8b9454805771 | 22eeed43 | SEARCH-T02-sellers-active |
| `2026-11-04-SEARCH-T02-1m-steady-sellers-active-load-r3` | steady | sellers-active-load | 1m | 0.175 | 0.18 | 6,608/9,599/표본 부족(n=20)/11,183 | 6,336/9,439/표본 부족(n=33)/11,183 | 0.00% | 200:33 | - | 0 | 51.3/62.6 | 10/10 | 5.8136 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 17% | 8b9454805771 | 22eeed43 | SEARCH-T02-sellers-active |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| sellers-active-load | 1m | 3 | 9478 | 7985~9599 | 17.0% |


## 2026-11-04 1m base2

base2 기준 요청률 0.25 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-1m-steady-base2-r1` | steady | base2 | 1m | 0.175 | 0.19 | 6,558/9,432/표본 부족(n=20)/11,866 | 6,112/8,430/표본 부족(n=35)/11,866 | 0.00% | 200:35 | - | 0 | 55.6/57.6 | 10/10 | 5.9731 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 20% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-1m-steady-base2-r2` | steady | base2 | 1m | 0.175 | 0.19 | 6,580/7,935/표본 부족(n=20)/9,385 | 6,267/7,917/표본 부족(n=35)/9,385 | 0.00% | 200:35 | - | 0 | 58.5/67.0 | 10/10 | 6.2397 | 0 | SELECT products 34%, COUNT products 32%, SELECT products 20% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T02-1m-steady-base2-r3` | steady | base2 | 1m | 0.175 | 0.18 | 6,637/9,495/표본 부족(n=20)/10,939 | 6,355/9,367/표본 부족(n=33)/10,939 | 0.00% | 200:33 | - | 0 | 49.4/64.9 | 10/10 | 5.5924 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 17% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base2 | 1m | 3 | 9432 | 7935~9495 | 16.5% |


## 2026-11-04 1m fulltext

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T02-1m-explain-fulltext-r1` | explain | fulltext | 1m | - | - | - | - | - | - | - | 0 | 50.6/50.8 | 11/11 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | SEARCH-T02-fulltext |

