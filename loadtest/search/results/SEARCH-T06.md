# SEARCH-T06 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-05 1m t06

t06 기준 요청률 4 req/s (SLO 미충족, 자기 ramp의 처리 가능 요청률을 비교 부하로 씀). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T06-1m-ramp-t06-r1` | ramp (dropped는 마지막 단계) | t06 | 1m | SLO 미충족 (처리 가능 4) | - | - | - | - | - | - | 0 | - | - | - | 0 | COUNT products 58%, COUNT products 22%, COUNT products 6% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-1m-steady-t06-r1` | steady (SLO 미충족, 비교 부하 4 req/s) | t06 | 1m | 2.8 | 2.77 | 702/820/871/935 (n=289) | 373/818/853/935 (n=501) | 0.00% | 200:501 | - | 0 | 52.6/53.4 | 10/10 | 0.3802 | 0 | COUNT products 61%, COUNT products 20%, COUNT products 7% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-1m-steady-t06-r2` | steady (SLO 미충족, 비교 부하 4 req/s) | t06 | 1m | 2.8 | 2.75 | 702/818/893/1,020 (n=289) | 400/835/972/1,075 (n=497) | 0.00% | 200:497 | - | 0 | 40.1/55.6 | 10/10 | 0.2924 | 0 | COUNT products 59%, COUNT products 19%, COUNT products 11% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-1m-steady-t06-r3` | steady (SLO 미충족, 비교 부하 4 req/s) | t06 | 1m | 2.8 | 2.74 | 700/814/842/938 (n=289) | 369/818/897/969 (n=495) | 0.00% | 200:495 | - | 0 | 47.5/53.8 | 10/10 | 0.3466 | 0 | COUNT products 61%, COUNT products 19%, COUNT products 8% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-1m-spike-t06-r1` | spike (SLO 미충족, 비교 부하 4 req/s) | t06 | 1m | 12 | 4.05 | 15,975/29,232/30,000/30,001 (n=206) | 14,417/28,864/30,000/30,001 (n=487) | 38.19% | 0:14 200:301 503:172 | other:172 timeout:14 | 0 | 99.7/99.7 | 10/10 | ≈0.4952 | 0 | COUNT products 62%, COUNT products 24%, COUNT products 5% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-1m-sequential-t06-r1` | sequential | t06 | 1m | - | 4.04 | - | 29/805/표본 부족(n=81)/813 | 0.00% | 200:81 | - | 0 | ≈63.7/63.7 | 10/10 | ≈0.3195 | 0 | COUNT products 70%, COUNT products 7%, COUNT products 6% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-1m-explain-t06-r1` | explain | t06 | 1m | - | - | - | - | - | - | - | 0 | ≈18.1/18.1 | 10/10 | - | 0 | COUNT products 56%, COUNT products 15%, COUNT products 7% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| t06 | 1m | 3 | 818 | 814~820 | 0.8% |

### ramp `2026-11-05-SEARCH-T06-1m-ramp-t06-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 4 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 2 | 814.43 | 표본 부족(n=52) | 0 | 0 | ≈17.0 | 0 | 처리 기준만 |
| 4 | 3.93 | 836.14 | 937.41 (n=103) | 0 | 0 | ≈45.6 | 0 | 처리 기준만 |
| 8 | 4.87 | 9790.64 | 10472.47 (n=180) | 37.91 | 33 | ≈73.7 | 0 | 없음 |
| 1 | 0.99 | 819.28 | 표본 부족(n=26) | 0 | 0 | ≈35.3 | 0 | 처리 기준만 |
| 0.5 | 0.49 | 828.9 | 표본 부족(n=16) | 0 | 0 | ≈13.2 | 0 | 처리 기준만 |
| 0.25 | 0.26 | 825.65 | 표본 부족(n=16) | 0 | 0 | 5.1 | 0 | 처리 기준만 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T06-1m-spike-t06-r1` | 12 | 2 | 40.85% | 33.70% | 981ms | 68초 (회복 창 5s) | 0 |

### sequential `2026-11-05-SEARCH-T06-1m-sequential-t06-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 805 | 811 | 813 |
| high | popular | 2 | 16 | 23 | 25 |
| high | price_asc | 1 | 806 | 807 | 807 |
| high | price_asc | 2 | 19 | 24 | 25 |
| high | latest | 1 | 804 | 805 | 806 |
| high | latest | 2 | 21 | 23 | 24 |
| mid | popular | 1 | 718 | 722 | 724 |
| mid | popular | 2 | 15 | 17 | 18 |
| mid | price_asc | 1 | 712 | 740 | 747 |
| mid | price_asc | 2 | 19 | 19 | 19 |
| mid | latest | 1 | 700 | 705 | 707 |
| mid | latest | 2 | 20 | 21 | 22 |
| low | popular | 1 | 403 | 407 | 407 |
| low | popular | 2 | 194 | 203 | 206 |
| low | price_asc | 1 | 424 | 430 | 431 |
| low | price_asc | 2 | 197 | 200 | 201 |
| low | latest | 1 | 393 | 398 | 400 |
| low | latest | 2 | 195 | 196 | 196 |
| rare | popular | 1 | 29 | 29 | 30 |
| rare | popular | 2 | 23 | 24 | 24 |
| rare | price_asc | 1 | 30 | 31 | 31 |
| rare | price_asc | 2 | 22 | 23 | 23 |
| rare | latest | 1 | 28 | 29 | 29 |
| rare | latest | 2 | 27 | 29 | 29 |
| none | popular | 1 | 13 | 14 | 14 |
| none | price_asc | 1 | 13 | 17 | 18 |
| none | latest | 1 | 12 | 13 | 13 |


## 2026-11-05 1m t05

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T06-1m-sequential-t05-r1` | sequential | t05 | 1m | - | 1.22 | - | 352/3,454/표본 부족(n=72)/3,575 | 12.50% | 200:63 503:9 | other:9 | 0 | ≈38.5/96.2 | 10/10 | ≈0.6339 | 0 | COUNT products 29%, COUNT products 28%, COUNT products 28% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext,SEARCH-T06 |

### sequential `2026-11-05-SEARCH-T06-1m-sequential-t05-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 3,454 | 3,478 | 3,484 |
| high | price_asc | 1 | 3,453 | 3,454 | 3,455 |
| high | latest | 1 | 3,481 | 3,556 | 3,575 |
| mid | popular | 1 | 1,741 | 1,849 | 1,875 |
| mid | popular | 2 | 974 | 992 | 997 |
| mid | price_asc | 1 | 1,635 | 1,692 | 1,706 |
| mid | price_asc | 2 | 567 | 614 | 626 |
| mid | latest | 1 | 1,486 | 1,537 | 1,550 |
| mid | latest | 2 | 546 | 554 | 556 |
| low | popular | 1 | 419 | 444 | 450 |
| low | popular | 2 | 325 | 325 | 325 |
| low | price_asc | 1 | 422 | 427 | 428 |
| low | price_asc | 2 | 255 | 265 | 267 |
| low | latest | 1 | 402 | 410 | 412 |
| low | latest | 2 | 201 | 202 | 203 |
| rare | popular | 1 | 33 | 37 | 38 |
| rare | popular | 2 | 33 | 34 | 35 |
| rare | price_asc | 1 | 36 | 39 | 40 |
| rare | price_asc | 2 | 28 | 28 | 28 |
| rare | latest | 1 | 31 | 35 | 36 |
| rare | latest | 2 | 31 | 32 | 33 |
| none | popular | 1 | 9 | 10 | 10 |
| none | price_asc | 1 | 10 | 12 | 12 |
| none | latest | 1 | 8 | 11 | 12 |


## 2026-11-05 100k t06

t06 기준 요청률 8 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T06-100k-ramp-t06-r1` | ramp | t06 | 100k | 32 (64에서 실패) | 31.53 | 88/153/312/605 (n=824) | 55/131/304/738 (n=1,421) | 0.00% | 200:1421 | - | 0 | ≈41.4/70.7 | 10/10 | ≈0.0264 | 4 (디스크 읽기) | COUNT products 58%, COUNT products 17%, COUNT products 8% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-100k-steady-t06-r1` | steady | t06 | 100k | 5.6 | 5.55 | 85/97/110/191 (n=577) | 45/99/109/191 (n=999) | 0.00% | 200:999 | - | 0 | 12.0/14.4 | 10/10 | 0.0434 | 9 (디스크 읽기) | COUNT products 57%, COUNT products 16%, COUNT products 7% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-100k-steady-t06-r2` | steady | t06 | 100k | 5.6 | 5.49 | 84/96/102/208 (n=576) | 47/99/117/208 (n=989) | 0.00% | 200:989 | - | 0 | 14.6/14.9 | 10/10 | 0.0534 | 0 | COUNT products 55%, COUNT products 16%, COUNT products 10% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-100k-steady-t06-r3` | steady | t06 | 100k | 5.6 | 5.52 | 85/97/109/196 (n=576) | 44/97/111/196 (n=994) | 0.00% | 200:994 | - | 0 | 11.4/14.9 | 10/10 | 0.0412 | 0 | COUNT products 56%, COUNT products 16%, COUNT products 7% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-100k-spike-t06-r1` | spike | t06 | 100k | 24 | 7.45 | 85/97/108/162 (n=412) | 48/98/106/162 (n=894) | 0.00% | 200:894 | - | 0 | 29.9/29.9 | 10/10 | ≈0.0806 | 0 | COUNT products 60%, COUNT products 17%, COUNT products 7% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-100k-sequential-t06-r1` | sequential | t06 | 100k | - | 23.85 | - | 31/95/99/188 (n=1,020) | 0.00% | 200:1020 | - | 0 | ≈15.9/15.9 | 10/10 | ≈0.0134 | 0 | COUNT products 53%, COUNT products 11%, COUNT products 7% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |
| `2026-11-05-SEARCH-T06-100k-explain-t06-r1` | explain | t06 | 100k | - | - | - | - | - | - | - | 0 | ≈19.4/19.4 | 11/11 | - | 3 (디스크 읽기) | COUNT products 53%, COUNT products 14%, COUNT products 8% | 5985eaf0e8e5 | ff7c9c2f | SEARCH-T02-fulltext,SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| t06 | 100k | 3 | 97 | 96~97 | 1.4% |

### ramp `2026-11-05-SEARCH-T06-100k-ramp-t06-r1`

최대 안정 요청률 32 req/s (64에서 실패). 대표 행은 32 req/s 단계 구간의 값이다. 처리 가능 요청률은 32 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 2.02 | 106.05 | 표본 부족(n=52) | 0 | 0 | ≈5.4 | 0 | SLO |
| 4 | 3.97 | 105.03 | 112.83 (n=103) | 0 | 0 | ≈7.1 | 0 | SLO |
| 8 | 8.02 | 101.54 | 114.35 (n=206) | 0 | 0 | ≈14.2 | 0 | SLO |
| 16 | 15.82 | 101.4 | 144.91 (n=412) | 0 | 0 | ≈23.9 | 0 | SLO |
| 32 | 31.53 | 152.83 | 311.97 (n=824) | 0 | 0 | ≈41.4 | 0 | SLO |
| 64 | 42.78 | 10000.4 | 10000.94 (n=1,445) | 9.16 | 252 | ≈72.0 | 0 | 없음 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T06-100k-spike-t06-r1` | 24 | 2 | 0.00% | 0.00% | 500ms | 1초 (회복 창 5s) | 0 |

### sequential `2026-11-05-SEARCH-T06-100k-sequential-t06-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 95 | 97 | 100 |
| high | popular | 2 | 14 | 14 | 15 |
| high | price_asc | 1 | 98 | 99 | 103 |
| high | price_asc | 2 | 17 | 18 | 23 |
| high | latest | 1 | 95 | 99 | 109 |
| high | latest | 2 | 16 | 22 | 40 |
| mid | popular | 1 | 85 | 89 | 97 |
| mid | popular | 2 | 14 | 16 | 17 |
| mid | price_asc | 1 | 88 | 90 | 101 |
| mid | price_asc | 2 | 17 | 18 | 21 |
| mid | latest | 1 | 85 | 87 | 105 |
| mid | latest | 2 | 16 | 18 | 29 |
| low | popular | 1 | 46 | 54 | 105 |
| low | popular | 2 | 29 | 32 | 73 |
| low | price_asc | 1 | 49 | 57 | 99 |
| low | price_asc | 2 | 30 | 34 | 74 |
| low | latest | 1 | 47 | 56 | 188 |
| low | latest | 2 | 32 | 35 | 80 |
| rare | popular | 1 | 14 | 15 | 16 |
| rare | popular | 2 | 13 | 14 | 18 |
| rare | price_asc | 1 | 13 | 14 | 14 |
| rare | price_asc | 2 | 13 | 14 | 17 |
| rare | latest | 1 | 13 | 14 | 17 |
| rare | latest | 2 | 16 | 16 | 21 |
| none | popular | 1 | 12 | 15 | 17 |
| none | price_asc | 1 | 12 | 15 | 18 |
| none | latest | 1 | 11 | 12 | 21 |

