# SEARCH-T01 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-04 100k base

base 기준 요청률 2 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T01-100k-smoke-base-r1` | smoke | base | 100k | - | 2.04 | 568/786/표본 부족(n=36)/796 | 534/639/표본 부족(n=63)/796 | 0.00% | 200:63 | - | 0 | ≈27.9/27.9 | 10/10 | ≈0.2758 | 17 (디스크 읽기) | SELECT products 36%, COUNT products 32%, SELECT products 19% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-100k-ramp-base-r1` | ramp (dropped는 마지막 단계) | base | 100k | SLO 미충족 (처리 가능 2) | - | - | - | - | - | - | 11 | - | - | - | 0 | SELECT products 37%, COUNT products 34%, SELECT products 14% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-100k-steady-base-r1` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.40 | 560/774/814/872 (n=146) | 530/760/797/872 (n=253) | 0.00% | 200:253 | - | 0 | 35.9/38.0 | 10/10 | 0.5136 | 4 (디스크 읽기) | SELECT products 36%, COUNT products 33%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-100k-steady-base-r2` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.36 | 568/773/852/877 (n=148) | 546/761/820/877 (n=247) | 0.00% | 200:247 | - | 0 | 36.8/37.9 | 10/10 | 0.5401 | 0 | SELECT products 37%, COUNT products 33%, SELECT products 17% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-100k-steady-base-r3` | steady (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 1.4 | 1.44 | 577/802/858/865 (n=146) | 551/746/848/865 (n=261) | 0.00% | 200:261 | - | 0 | 37.3/38.7 | 10/10 | 0.5193 | 0 | SELECT products 36%, COUNT products 31%, SELECT products 21% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-100k-spike-base-r1` | spike (SLO 미충족, 비교 부하 2 req/s) | base | 100k | 6 | 1.98 | 10,000/10,000/10,000/10,001 (n=105) | 8,186/10,000/10,001/10,001 (n=238) | 44.12% | 0:105 200:133 | timeout:105 | 0 | 58.6/58.6 | 10/10 | ≈0.5922 | 0 | SELECT products 44%, COUNT products 40%, SELECT products 6% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-100k-sequential-base-r1` | sequential | base | 100k | - | 2.24 | - | 492/746/796/881 (n=1,020) | 0.00% | 200:1020 | - | 0 | 51.5/51.8 | 10/10 | ≈0.4588 | 0 | COUNT products 32%, SELECT products 12%, SELECT products 12% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-100k-explain-base-r1` | explain | base | 100k | - | - | - | - | - | - | - | 0 | ≈28.8/28.9 | 10/10 | - | 3 (디스크 읽기) | COUNT products 29%, COUNT products 13%, EXPLAIN products 11% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base | 100k | 3 | 774 | 773~802 | 3.8% |

### ramp `2026-11-04-SEARCH-T01-100k-ramp-base-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 2 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 2 | 780.03 | 표본 부족(n=54) | 0 | 0 | ≈33.6 | 0 | 처리 기준만 |
| 4 | 3.01 | 6761.67 | 표본 부족(n=93) | 0 | 11 | ≈88.8 | 0 | 없음 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T01-100k-spike-base-r1` | 6 | 1 | 58.11% | 21.11% | 929ms | 36초 (회복 창 5s) | 0 |

### sequential `2026-11-04-SEARCH-T01-100k-sequential-base-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 770 | 802 | 823 |
| high | popular | 2 | 426 | 451 | 455 |
| high | price_asc | 1 | 770 | 800 | 822 |
| high | price_asc | 2 | 438 | 459 | 522 |
| high | latest | 1 | 756 | 798 | 881 |
| high | latest | 2 | 437 | 464 | 493 |
| mid | popular | 1 | 568 | 607 | 652 |
| mid | popular | 2 | 298 | 322 | 364 |
| mid | price_asc | 1 | 564 | 605 | 648 |
| mid | price_asc | 2 | 299 | 319 | 352 |
| mid | latest | 1 | 563 | 602 | 629 |
| mid | latest | 2 | 317 | 334 | 342 |
| low | popular | 1 | 535 | 550 | 554 |
| low | popular | 2 | 277 | 289 | 389 |
| low | price_asc | 1 | 532 | 557 | 656 |
| low | price_asc | 2 | 276 | 291 | 419 |
| low | latest | 1 | 531 | 551 | 642 |
| low | latest | 2 | 294 | 308 | 329 |
| rare | popular | 1 | 497 | 527 | 531 |
| rare | popular | 2 | 268 | 302 | 492 |
| rare | price_asc | 1 | 517 | 532 | 557 |
| rare | price_asc | 2 | 269 | 276 | 283 |
| rare | latest | 1 | 517 | 532 | 536 |
| rare | latest | 2 | 227 | 239 | 241 |
| none | popular | 1 | 505 | 531 | 567 |
| none | price_asc | 1 | 510 | 530 | 544 |
| none | latest | 1 | 513 | 527 | 534 |


## 2026-11-04 mirror base

base 기준 요청률 2 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T01-mirror-smoke-base-r1` | smoke | base | mirror | - | 1.21 | 853/1,199/표본 부족(n=25)/1,264 | 817/1,200/표본 부족(n=37)/1,264 | 0.00% | 200:37 | - | 0 | ≈36.9/41.4 | 10/10 | ≈0.6133 | 0 | SELECT products 38%, COUNT products 35%, SELECT products 6% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-mirror-ramp-base-r1` | ramp (k6 종료 99) (dropped는 마지막 단계) | base | mirror | SLO 미충족 (처리 가능 2) | - | - | - | - | - | - | 19 | - | - | - | 0 | SELECT products 39%, COUNT products 35%, SELECT products 9% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-mirror-steady-base-r1` | steady (SLO 미충족, 비교 부하 2 req/s) | base | mirror | 1.4 | 1.31 | 868/1,210/1,560/1,767 (n=146) | 835/1,202/1,340/1,767 (n=237) | 0.00% | 200:237 | - | 0 | 54.3/58.2 | 10/10 | 0.8309 | 0 | SELECT products 38%, COUNT products 33%, SELECT products 16% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-mirror-steady-base-r2` | steady (SLO 미충족, 비교 부하 2 req/s) | base | mirror | 1.4 | 1.41 | 873/1,269/1,539/1,619 (n=137) | 816/1,179/1,404/1,619 (n=254) | 0.00% | 200:254 | - | 0 | 55.1/60.2 | 10/10 | 0.7818 | 10 (디스크 읽기) | SELECT products 34%, COUNT products 30%, SELECT products 21% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-mirror-steady-base-r3` | steady (SLO 미충족, 비교 부하 2 req/s) | base | mirror | 1.4 | 1.42 | 896/1,302/1,547/1,569 (n=146) | 826/1,232/1,510/1,569 (n=257) | 0.00% | 200:257 | - | 0 | 57.5/61.3 | 10/10 | 0.8100 | 0 | SELECT products 35%, COUNT products 31%, SELECT products 22% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-mirror-spike-base-r1` | spike (SLO 미충족, 비교 부하 2 req/s) | base | mirror | 6 | 1.89 | 10,000/10,000/10,000/10,000 (n=112) | 10,000/10,000/10,001/10,001 (n=229) | 72.05% | 0:165 200:64 | timeout:165 | 0 | 72.6/72.6 | 10/10 | ≈0.7688 | 0 | SELECT products 48%, COUNT products 44%, SELECT products 2% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-mirror-sequential-base-r1` | sequential | base | mirror | - | 1.35 | - | 762/1,191/표본 부족(n=81)/2,445 | 0.00% | 200:81 | - | 0 | ≈35.2/47.2 | 10/10 | ≈0.5222 | 0 | COUNT products 32%, SELECT products 14%, SELECT products 12% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-mirror-explain-base-r1` | explain | base | mirror | - | - | - | - | - | - | - | 0 | ≈26.8/27.2 | 11/11 | - | 3 (디스크 읽기) | COUNT products 28%, COUNT products 13%, EXPLAIN products 11% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base | mirror | 3 | 1269 | 1210~1302 | 7.2% |

### ramp `2026-11-04-SEARCH-T01-mirror-ramp-base-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 2 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 1.85 | 1885.23 | 표본 부족(n=48) | 0 | 0 | ≈58.0 | 0 | 처리 기준만 |
| 4 | 2.12 | 10000.81 | 표본 부족(n=73) | 50 | 19 | ≈80.2 | 99 | 없음 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T01-mirror-spike-base-r1` | 6 | 1 | 78.99% | 61.54% | 1523ms | 76초 (회복 창 5s) | 0 |

### sequential `2026-11-04-SEARCH-T01-mirror-sequential-base-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 1,188 | 1,205 | 1,209 |
| high | popular | 2 | 655 | 678 | 683 |
| high | price_asc | 1 | 1,190 | 1,255 | 1,271 |
| high | price_asc | 2 | 673 | 686 | 690 |
| high | latest | 1 | 1,191 | 1,229 | 1,238 |
| high | latest | 2 | 692 | 717 | 724 |
| mid | popular | 1 | 870 | 879 | 882 |
| mid | popular | 2 | 467 | 467 | 468 |
| mid | price_asc | 1 | 873 | 939 | 955 |
| mid | price_asc | 2 | 462 | 466 | 467 |
| mid | latest | 1 | 870 | 877 | 879 |
| mid | latest | 2 | 491 | 496 | 498 |
| low | popular | 1 | 857 | 2,127 | 2,445 |
| low | popular | 2 | 454 | 482 | 489 |
| low | price_asc | 1 | 880 | 1,014 | 1,047 |
| low | price_asc | 2 | 428 | 447 | 452 |
| low | latest | 1 | 810 | 814 | 815 |
| low | latest | 2 | 446 | 447 | 447 |
| rare | popular | 1 | 788 | 794 | 796 |
| rare | popular | 2 | 404 | 407 | 408 |
| rare | price_asc | 1 | 775 | 775 | 775 |
| rare | price_asc | 2 | 395 | 399 | 400 |
| rare | latest | 1 | 776 | 832 | 847 |
| rare | latest | 2 | 352 | 353 | 353 |
| none | popular | 1 | 774 | 805 | 813 |
| none | price_asc | 1 | 764 | 784 | 789 |
| none | latest | 1 | 772 | 777 | 779 |


## 2026-11-04 1m base

base 기준 요청률 0.25 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T01-1m-smoke-base-r1` | smoke | base | 1m | - | 0.16 | 6,357/6,816/표본 부족(n=5)/6,870 | 6,357/6,816/표본 부족(n=5)/6,870 | 0.00% | 200:5 | - | 0 | ≈34.6/34.6 | 10/10 | ≈4.4395 | 0 | SELECT products 51%, COUNT products 49%, SET ? 0% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-1m-ramp-base-r1` | ramp (dropped는 마지막 단계) | base | 1m | SLO 미충족 (처리 가능 0.25) | - | - | - | - | - | - | 0 | - | - | - | 16 (디스크 읽기) | SELECT products 40%, COUNT products 37%, SELECT products 5% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-1m-steady-base-r1` | steady (SLO 미충족, 비교 부하 0.25 req/s) | base | 1m | 0.175 | 0.15 | 6,395/9,984/표본 부족(n=19)/10,027 | 6,388/9,440/표본 부족(n=27)/10,027 | 0.00% | 200:27 | - | 0 | 43.2/45.0 | 10/10 | 5.9584 | 0 | SELECT products 39%, COUNT products 37%, SELECT products 8% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-1m-steady-base-r2` | steady (SLO 미충족, 비교 부하 0.25 req/s) | base | 1m | 0.175 | 0.20 | 6,916/9,735/표본 부족(n=20)/12,993 | 6,323/9,492/표본 부족(n=37)/12,993 | 0.00% | 200:37 | - | 0 | 67.2/68.9 | 10/10 | 6.8961 | 0 | SELECT products 35%, COUNT products 32%, SELECT products 23% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-1m-steady-base-r3` | steady (SLO 미충족, 비교 부하 0.25 req/s) | base | 1m | 0.175 | 0.17 | 6,870/9,842/표본 부족(n=21)/9,891 | 6,489/9,284/표본 부족(n=30)/9,891 | 0.00% | 200:30 | - | 0 | 52.9/56.3 | 10/10 | 6.3615 | 0 | SELECT products 41%, COUNT products 38%, SELECT products 14% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-1m-spike-base-r1` | spike (SLO 미충족, 비교 부하 0.25 req/s) | base | 1m | 0.75 | 0.23 | 30,000/30,000/표본 부족(n=12)/30,000 | 30,000/30,000/표본 부족(n=31)/30,001 | 77.42% | 0:24 200:7 | timeout:24 | 0 | 85.5/99.6 | 10/10 | ≈7.3614 | 0 | SELECT products 44%, COUNT products 39%, SELECT products 4% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-1m-sequential-base-r1` | sequential | base | 1m | - | 0.18 | - | 6,033/9,434/표본 부족(n=81)/9,585 | 0.00% | 200:81 | - | 0 | 51.6/52.5 | 10/10 | ≈5.7748 | 3 (디스크 읽기) | COUNT products 33%, SELECT products 12%, SELECT products 12% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T01-1m-explain-base-r1` | explain | base | 1m | - | - | - | - | - | - | - | 0 | 49.4/51.3 | 11/11 | - | 3 (디스크 읽기) | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base | 1m | 3 | 9842 | 9735~9984 | 2.5% |

### ramp `2026-11-04-SEARCH-T01-1m-ramp-base-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 0.25 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 0.23 | 29999.78 | 표본 부족(n=5) | 62.5 | 16 | ≈35.0 | 99 | 없음 |
| 1 | 0.16 | 30000.29 | 표본 부족(n=5) | 50 | 6 | ≈50.3 | 99 | 없음 |
| 0.5 | 0.25 | 30000.83 | 표본 부족(n=10) | 50 | 1 | ≈65.2 | 99 | 없음 |
| 0.25 | 0.22 | 13881.63 | 표본 부족(n=7) | 0 | 0 | ≈40.1 | 0 | 처리 기준만 |
| 0.125 | 0.17 | 7094.31 | 표본 부족(n=3) | 0 | 0 | ≈33.8 | 0 | 처리 기준만 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T01-1m-spike-base-r1` | 0.75 | 0.125 | 83.33% | 69.23% | 11811ms | 90초 안에 회복 안 됨 (회복 창 24s) | 0 |

### sequential `2026-11-04-SEARCH-T01-1m-sequential-base-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 9,434 | 9,437 | 9,437 |
| high | popular | 2 | 5,084 | 5,123 | 5,133 |
| high | price_asc | 1 | 9,527 | 9,574 | 9,585 |
| high | price_asc | 2 | 5,219 | 5,226 | 5,228 |
| high | latest | 1 | 9,421 | 9,433 | 9,436 |
| high | latest | 2 | 5,442 | 5,491 | 5,503 |
| mid | popular | 1 | 6,899 | 6,943 | 6,954 |
| mid | popular | 2 | 3,576 | 3,582 | 3,583 |
| mid | price_asc | 1 | 7,006 | 7,051 | 7,062 |
| mid | price_asc | 2 | 3,560 | 3,573 | 3,577 |
| mid | latest | 1 | 6,913 | 6,925 | 6,928 |
| mid | latest | 2 | 3,742 | 3,747 | 3,748 |
| low | popular | 1 | 6,381 | 6,397 | 6,401 |
| low | popular | 2 | 3,239 | 3,263 | 3,269 |
| low | price_asc | 1 | 6,364 | 6,401 | 6,410 |
| low | price_asc | 2 | 3,223 | 3,232 | 3,234 |
| low | latest | 1 | 6,315 | 6,346 | 6,353 |
| low | latest | 2 | 3,448 | 3,465 | 3,469 |
| rare | popular | 1 | 6,042 | 6,130 | 6,151 |
| rare | popular | 2 | 3,083 | 3,112 | 3,120 |
| rare | price_asc | 1 | 6,155 | 6,204 | 6,216 |
| rare | price_asc | 2 | 3,101 | 3,124 | 3,130 |
| rare | latest | 1 | 6,043 | 6,051 | 6,053 |
| rare | latest | 2 | 3,268 | 3,285 | 3,289 |
| none | popular | 1 | 6,008 | 6,009 | 6,009 |
| none | price_asc | 1 | 6,263 | 6,577 | 6,656 |
| none | latest | 1 | 6,188 | 6,243 | 6,257 |

