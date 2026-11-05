# SEARCH-T05 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-05 100k window

window 기준 요청률 8 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T05-100k-ramp-window-r1` | ramp (dropped는 마지막 단계) | window | 100k | SLO 미충족 (처리 가능 8) | - | - | - | - | - | - | 0 | - | - | - | 0 | COUNT products 68%, SELECT products 23%, COUNT products 4% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-steady-window-r1` | steady | window | 100k | 5.6 | 5.54 | 89/1,113/1,329/1,729 (n=577) | 83/1,080/1,203/1,729 (n=999) | 0.00% | 200:999 | - | 0 | 42.7/45.5 | 10/10 | 0.1544 | 0 | COUNT products 65%, SELECT products 26%, COUNT products 5% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-steady-window-r2` | steady | window | 100k | 5.6 | 5.46 | 83/1,102/1,305/1,617 (n=576) | 80/1,064/1,161/1,617 (n=989) | 0.00% | 200:989 | - | 0 | 45.1/46.6 | 10/10 | 0.1654 | 0 | COUNT products 66%, SELECT products 24%, COUNT products 8% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-steady-window-r3` | steady | window | 100k | 5.6 | 5.52 | 85/1,116/1,351/1,844 (n=577) | 82/1,086/1,179/1,844 (n=995) | 0.00% | 200:995 | - | 0 | 38.1/44.3 | 10/10 | 0.1381 | 0 | COUNT products 65%, SELECT products 25%, COUNT products 7% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-spike-window-r1` | spike | window | 100k | 24 | 6.47 | 10,000/10,001/10,001/10,005 (n=412) | 6,980/10,001/10,001/10,005 (n=776) | 41.11% | 0:319 200:457 | timeout:319 | 0 | 83.8/83.8 | 10/10 | ≈0.2603 | 0 | COUNT products 83%, SELECT products 8%, COUNT products 5% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-sequential-window-r1` | sequential | window | 100k | - | 7.43 | - | 49/897/1,034/1,048 (n=1,020) | 0.00% | 200:1020 | - | 0 | 50.7/50.7 | 10/10 | ≈0.1367 | 0 | COUNT products 22%, COUNT products 21%, COUNT products 20% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-explain-window-r1` | explain | window | 100k | - | - | - | - | - | - | - | 0 | ≈20.8/37.8 | 10/10 | - | 0 | COUNT products 16%, COUNT products 16%, COUNT products 14% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| window | 100k | 3 | 1113 | 1102~1116 | 1.3% |

### ramp `2026-11-05-SEARCH-T05-100k-ramp-window-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 8 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 2.02 | 1048.52 | 표본 부족(n=52) | 0 | 0 | ≈13.9 | 0 | 처리 기준만 |
| 4 | 3.95 | 1085.12 | 1149.47 (n=103) | 0 | 0 | ≈27.2 | 0 | 처리 기준만 |
| 8 | 7.95 | 1180.04 | 1463.69 (n=206) | 0 | 0 | ≈48.4 | 0 | 처리 기준만 |
| 16 | 11.96 | 9057.56 | 10000.61 (n=373) | 1.56 | 48 | ≈83.5 | 0 | 없음 |
| 1 | 0.99 | 816.39 | 표본 부족(n=26) | 0 | 0 | ≈24.9 | 0 | 처리 기준만 |
| 0.5 | 0.49 | 1041.12 | 표본 부족(n=16) | 0 | 0 | ≈5.9 | 0 | 처리 기준만 |
| 0.25 | 0.26 | 1040.59 | 표본 부족(n=16) | 0 | 0 | 5.1 | 0 | 처리 기준만 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T05-100k-spike-window-r1` | 24 | 2 | 49.24% | 14.36% | 1335ms | 31초 (회복 창 5s) | 0 |

### sequential `2026-11-05-SEARCH-T05-100k-sequential-window-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 1,035 | 1,044 | 1,048 |
| high | popular | 2 | 675 | 693 | 774 |
| high | price_asc | 1 | 946 | 967 | 991 |
| high | price_asc | 2 | 501 | 524 | 556 |
| high | latest | 1 | 897 | 904 | 936 |
| high | latest | 2 | 487 | 492 | 540 |
| mid | popular | 1 | 114 | 118 | 124 |
| mid | popular | 2 | 88 | 91 | 93 |
| mid | price_asc | 1 | 123 | 128 | 135 |
| mid | price_asc | 2 | 52 | 54 | 57 |
| mid | latest | 1 | 116 | 120 | 161 |
| mid | latest | 2 | 48 | 50 | 58 |
| low | popular | 1 | 47 | 63 | 92 |
| low | popular | 2 | 42 | 55 | 123 |
| low | price_asc | 1 | 50 | 65 | 77 |
| low | price_asc | 2 | 32 | 42 | 56 |
| low | latest | 1 | 48 | 64 | 74 |
| low | latest | 2 | 31 | 41 | 46 |
| rare | popular | 1 | 7 | 8 | 9 |
| rare | popular | 2 | 5 | 7 | 10 |
| rare | price_asc | 1 | 5 | 6 | 7 |
| rare | price_asc | 2 | 5 | 7 | 7 |
| rare | latest | 1 | 5 | 6 | 6 |
| rare | latest | 2 | 4 | 5 | 7 |
| none | popular | 1 | 3 | 4 | 7 |
| none | price_asc | 1 | 3 | 4 | 5 |
| none | latest | 1 | 3 | 4 | 4 |


## 2026-11-05 100k timeout

timeout 기준 요청률 8 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T05-100k-ramp-timeout-r1` | ramp (dropped는 마지막 단계) | timeout | 100k | SLO 미충족 (처리 가능 8) | - | - | - | - | - | - | 0 | - | - | - | 0 | COUNT products 71%, SELECT products 19%, COUNT products 6% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-steady-timeout-r1` | steady | timeout | 100k | 5.6 | 5.53 | 85/1,112/1,289/1,816 (n=577) | 82/1,076/1,156/1,816 (n=998) | 0.00% | 200:998 | - | 0 | 45.0/46.8 | 10/10 | 0.1627 | 0 | COUNT products 65%, SELECT products 26%, COUNT products 5% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-steady-timeout-r2` | steady | timeout | 100k | 5.6 | 5.49 | 85/1,108/1,331/1,600 (n=575) | 81/1,075/1,183/1,600 (n=988) | 0.00% | 200:988 | - | 0 | 44.8/45.7 | 10/10 | 0.1634 | 0 | COUNT products 65%, SELECT products 24%, COUNT products 8% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-steady-timeout-r3` | steady | timeout | 100k | 5.6 | 5.52 | 88/1,114/1,424/1,583 (n=577) | 83/1,078/1,175/1,583 (n=995) | 0.00% | 200:995 | - | 0 | 43.9/46.0 | 10/10 | 0.1590 | 0 | COUNT products 65%, SELECT products 25%, COUNT products 7% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |
| `2026-11-05-SEARCH-T05-100k-spike-timeout-r1` | spike | timeout | 100k | 24 | 7.30 | 4,430/8,719/10,000/10,000 (n=412) | 3,283/8,403/10,000/10,000 (n=876) | 5.94% | 0:12 200:824 503:40 | other:40 timeout:12 | 0 | 73.8/73.8 | 10/10 | ≈0.2033 | 0 | COUNT products 75%, SELECT products 14%, COUNT products 6% | 9c715056a644 | 3ffda37a | SEARCH-T02-fulltext |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| timeout | 100k | 3 | 1112 | 1108~1114 | 0.5% |

### ramp `2026-11-05-SEARCH-T05-100k-ramp-timeout-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 8 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 2.02 | 1062.32 | 표본 부족(n=52) | 0 | 0 | ≈9.9 | 0 | 처리 기준만 |
| 4 | 3.95 | 1084.69 | 1171.81 (n=103) | 0 | 0 | ≈23.3 | 0 | 처리 기준만 |
| 8 | 7.95 | 1149.96 | 1355.66 (n=206) | 0 | 0 | ≈53.4 | 0 | 처리 기준만 |
| 16 | 14.92 | 3090.71 | 3299.33 (n=412) | 5.46 | 0 | ≈69.3 | 0 | 없음 |
| 1 | 0.99 | 816.11 | 표본 부족(n=26) | 0 | 0 | ≈14.7 | 0 | 처리 기준만 |
| 0.5 | 0.49 | 1037.8 | 표본 부족(n=16) | 0 | 0 | ≈4.2 | 0 | 처리 기준만 |
| 0.25 | 0.26 | 1042.36 | 표본 부족(n=16) | 0 | 0 | 3.9 | 0 | 처리 기준만 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T05-100k-spike-timeout-r1` | 24 | 2 | 7.19% | 1.10% | 1335ms | 18초 (회복 창 5s) | 0 |

