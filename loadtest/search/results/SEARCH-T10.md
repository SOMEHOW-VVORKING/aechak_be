# SEARCH-T10 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-06 10m l1

l1 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-06-SEARCH-T10-10m-steady-l1-r1` | steady | l1 | 10m | 22.4 | 22.10 | 26/97/199/487 (n=2,305) | 24/340/767/1,178 (n=3,979) | 0.00% | 200:3979 | - | 0 | 3.3/3.4 | 10/10 | 0.0030 | 0 | SELECT categories 57%, SET ? 13%, SET ? 11% | 15fc78db721f | 3656c970 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l1-r2` | steady | l1 | 10m | 22.4 | 22.08 | 25/92/185/436 (n=2,304) | 20/329/752/1,172 (n=3,975) | 0.00% | 200:3975 | - | 0 | 3.4/3.5 | 10/10 | 0.0030 | 10 (디스크 읽기) | SELECT categories 57%, SET ? 13%, COMMIT ? 11% | 15fc78db721f | 3656c970 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l1-r3` | steady | l1 | 10m | 22.4 | 22.05 | 25/92/174/328 (n=2,303) | 20/319/753/1,222 (n=3,972) | 0.00% | 200:3972 | - | 0 | 3.5/3.5 | 10/10 | 0.0032 | 0 | SELECT categories 58%, SET ? 13%, SET ? 12% | 15fc78db721f | 3656c970 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-sequential-l1-r1` | sequential | l1 | 10m | - | 8.76 | - | 23/673/표본 부족(n=81)/971 | 0.00% | 200:81 | - | 0 | ≈2.9/3.2 | 10/10 | ≈0.0068 | 0 | SELECT categories 55%, SET ? 16%, COMMIT ? 14% | 15fc78db721f | 3656c970 | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| l1 | 10m | 3 | 92 | 92~97 | 5.5% |

### sequential `2026-11-06-SEARCH-T10-10m-sequential-l1-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 82 | 84 | 84 |
| high | popular | 2 | 15 | 15 | 15 |
| high | price_asc | 1 | 967 | 971 | 971 |
| high | price_asc | 2 | 143 | 143 | 144 |
| high | latest | 1 | 198 | 199 | 199 |
| high | latest | 2 | 194 | 194 | 194 |
| mid | popular | 1 | 25 | 25 | 25 |
| mid | popular | 2 | 14 | 14 | 14 |
| mid | price_asc | 1 | 673 | 675 | 676 |
| mid | price_asc | 2 | 143 | 152 | 154 |
| mid | latest | 1 | 49 | 50 | 51 |
| mid | latest | 2 | 50 | 54 | 55 |
| low | popular | 1 | 16 | 24 | 26 |
| low | popular | 2 | 13 | 14 | 14 |
| low | price_asc | 1 | 166 | 167 | 167 |
| low | price_asc | 2 | 176 | 176 | 176 |
| low | latest | 1 | 23 | 24 | 24 |
| low | latest | 2 | 24 | 26 | 26 |
| rare | popular | 1 | 10 | 10 | 10 |
| rare | popular | 2 | 12 | 12 | 12 |
| rare | price_asc | 1 | 16 | 16 | 16 |
| rare | price_asc | 2 | 17 | 18 | 18 |
| rare | latest | 1 | 8 | 9 | 10 |
| rare | latest | 2 | 10 | 11 | 11 |
| none | popular | 1 | 6 | 6 | 6 |
| none | price_asc | 1 | 7 | 7 | 7 |
| none | latest | 1 | 6 | 6 | 6 |


## 2026-11-06 10m l2

l2 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-06-SEARCH-T10-10m-sequential-l2-r1` | sequential | l2 | 10m | - | 9.02 | - | 26/580/표본 부족(n=81)/976 | 0.00% | 200:81 | - | 0 | ≈2.2/2.2 | 10/10 | ≈0.0050 | 0 | SELECT categories 61%, COMMIT ? 14%, SET ? 14% | 31d5b3188453 | a53ccbe2 | SEARCH-T06 |

### sequential `2026-11-06-SEARCH-T10-10m-sequential-l2-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 149 | 149 | 150 |
| high | popular | 2 | 25 | 30 | 31 |
| high | price_asc | 1 | 974 | 975 | 976 |
| high | price_asc | 2 | 112 | 116 | 118 |
| high | latest | 1 | 99 | 100 | 100 |
| high | latest | 2 | 20 | 21 | 22 |
| mid | popular | 1 | 35 | 37 | 37 |
| mid | popular | 2 | 22 | 23 | 23 |
| mid | price_asc | 1 | 580 | 596 | 600 |
| mid | price_asc | 2 | 115 | 115 | 115 |
| mid | latest | 1 | 25 | 28 | 28 |
| mid | latest | 2 | 19 | 20 | 20 |
| low | popular | 1 | 26 | 26 | 26 |
| low | popular | 2 | 21 | 26 | 27 |
| low | price_asc | 1 | 176 | 177 | 177 |
| low | price_asc | 2 | 187 | 187 | 187 |
| low | latest | 1 | 24 | 24 | 24 |
| low | latest | 2 | 23 | 26 | 27 |
| rare | popular | 1 | 18 | 21 | 22 |
| rare | popular | 2 | 21 | 26 | 28 |
| rare | price_asc | 1 | 26 | 343 | 423 |
| rare | price_asc | 2 | 31 | 83 | 96 |
| rare | latest | 1 | 24 | 26 | 26 |
| rare | latest | 2 | 21 | 54 | 63 |
| none | popular | 1 | 14 | 16 | 16 |
| none | price_asc | 1 | 12 | 14 | 14 |
| none | latest | 1 | 11 | 13 | 14 |


## 2026-11-06 10m l3

l3 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-06-SEARCH-T10-10m-sequential-l3-r1` | sequential | l3 | 10m | - | 24.33 | - | 20/180/표본 부족(n=81)/230 | 0.00% | 200:81 | - | 0 | ≈2.1/2.1 | 10/10 | ≈0.0018 | 0 | SELECT categories 59%, SET ? 16%, COMMIT ? 12% | f633570a0165 | 1456baeb | SEARCH-T06 |

### sequential `2026-11-06-SEARCH-T10-10m-sequential-l3-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 21 | 24 | 25 |
| high | popular | 2 | 23 | 25 | 26 |
| high | price_asc | 1 | 50 | 61 | 64 |
| high | price_asc | 2 | 103 | 108 | 109 |
| high | latest | 1 | 18 | 19 | 19 |
| high | latest | 2 | 19 | 20 | 20 |
| mid | popular | 1 | 24 | 24 | 24 |
| mid | popular | 2 | 23 | 27 | 28 |
| mid | price_asc | 1 | 40 | 49 | 51 |
| mid | price_asc | 2 | 108 | 111 | 111 |
| mid | latest | 1 | 18 | 18 | 18 |
| mid | latest | 2 | 18 | 27 | 29 |
| low | popular | 1 | 19 | 19 | 20 |
| low | popular | 2 | 20 | 21 | 21 |
| low | price_asc | 1 | 180 | 184 | 184 |
| low | price_asc | 2 | 186 | 221 | 230 |
| low | latest | 1 | 18 | 19 | 20 |
| low | latest | 2 | 19 | 20 | 20 |
| rare | popular | 1 | 17 | 19 | 19 |
| rare | popular | 2 | 22 | 23 | 23 |
| rare | price_asc | 1 | 27 | 31 | 32 |
| rare | price_asc | 2 | 31 | 34 | 34 |
| rare | latest | 1 | 19 | 22 | 23 |
| rare | latest | 2 | 19 | 23 | 24 |
| none | popular | 1 | 11 | 14 | 15 |
| none | price_asc | 1 | 11 | 11 | 12 |
| none | latest | 1 | 10 | 11 | 12 |


## 2026-11-06 10m l2s

l2s 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-06-SEARCH-T10-10m-steady-l2s-r1` | steady | l2s | 10m | 22.4 | 22.10 | 31/205/368/636 (n=2,305) | 30/428/826/1,414 (n=3,979) | 0.00% | 200:3979 | - | 0 | 2.9/2.9 | 10/10 | 0.0026 | 0 | SELECT categories 57%, SET ? 13%, SET ? 12% | 31d5b3188453 | a53ccbe2 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l2s-r2` | steady | l2s | 10m | 22.4 | 22.08 | 30/226/396/576 (n=2,304) | 29/441/920/1,579 (n=3,975) | 0.00% | 200:3975 | - | 0 | 3.3/3.7 | 10/10 | 0.0030 | 0 | SELECT categories 57%, SET ? 13%, COMMIT ? 11% | 31d5b3188453 | a53ccbe2 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l2s-r3` | steady | l2s | 10m | 22.4 | 22.06 | 30/205/414/652 (n=2,304) | 29/427/875/1,596 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.2/3.4 | 10/10 | 0.0029 | 0 | SELECT categories 55%, SET ? 15%, COMMIT ? 12% | 31d5b3188453 | a53ccbe2 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-sequential-l2s-r1` | sequential | l2s | 10m | - | 10.16 | - | 18/574/표본 부족(n=81)/978 | 0.00% | 200:81 | - | 0 | ≈2.3/4.1 | 10/10 | ≈0.0047 | 0 | SELECT categories 58%, COMMIT ? 13%, SET ? 12% | 31d5b3188453 | a53ccbe2 | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| l2s | 10m | 3 | 205 | 205~226 | 10.4% |

### sequential `2026-11-06-SEARCH-T10-10m-sequential-l2s-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 139 | 141 | 141 |
| high | popular | 2 | 18 | 18 | 18 |
| high | price_asc | 1 | 978 | 978 | 978 |
| high | price_asc | 2 | 103 | 128 | 134 |
| high | latest | 1 | 93 | 106 | 110 |
| high | latest | 2 | 14 | 15 | 15 |
| mid | popular | 1 | 29 | 29 | 29 |
| mid | popular | 2 | 17 | 17 | 17 |
| mid | price_asc | 1 | 574 | 577 | 578 |
| mid | price_asc | 2 | 100 | 101 | 101 |
| mid | latest | 1 | 20 | 20 | 20 |
| mid | latest | 2 | 13 | 14 | 14 |
| low | popular | 1 | 21 | 21 | 21 |
| low | popular | 2 | 15 | 15 | 15 |
| low | price_asc | 1 | 176 | 176 | 176 |
| low | price_asc | 2 | 182 | 182 | 182 |
| low | latest | 1 | 18 | 18 | 18 |
| low | latest | 2 | 13 | 13 | 14 |
| rare | popular | 1 | 13 | 13 | 13 |
| rare | popular | 2 | 15 | 16 | 16 |
| rare | price_asc | 1 | 19 | 22 | 22 |
| rare | price_asc | 2 | 20 | 21 | 21 |
| rare | latest | 1 | 13 | 13 | 13 |
| rare | latest | 2 | 13 | 13 | 13 |
| none | popular | 1 | 6 | 7 | 7 |
| none | price_asc | 1 | 7 | 8 | 8 |
| none | latest | 1 | 6 | 6 | 6 |


## 2026-11-06 10m l3s

l3s 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-06-SEARCH-T10-10m-steady-l3s-r1` | steady | l3s | 10m | 22.4 | 22.10 | 19/42/117/456 (n=2,305) | 19/359/690/1,399 (n=3,979) | 0.00% | 200:3979 | - | 0 | 3.5/3.8 | 10/10 | 0.0032 | 0 | SELECT categories 56%, SET ? 14%, SET ? 11% | f633570a0165 | 1456baeb | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l3s-r2` | steady | l3s | 10m | 22.4 | 22.08 | 18/42/111/366 (n=2,304) | 18/365/800/1,399 (n=3,975) | 0.00% | 200:3975 | - | 0 | 2.8/3.1 | 10/10 | 0.0025 | 0 | SELECT categories 58%, SET ? 14%, COMMIT ? 11% | f633570a0165 | 1456baeb | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l3s-r3` | steady | l3s | 10m | 22.4 | 22.06 | 18/39/162/364 (n=2,304) | 17/346/734/1,555 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.0/3.1 | 10/10 | 0.0027 | 0 | SELECT categories 58%, SET ? 13%, SET ? 11% | f633570a0165 | 1456baeb | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-sequential-l3s-r1` | sequential | l3s | 10m | - | 28.64 | - | 14/178/표본 부족(n=81)/216 | 0.00% | 200:81 | - | 0 | ≈2.6/2.6 | 10/10 | ≈0.0019 | 0 | SELECT categories 63%, SET ? 13%, COMMIT ? 12% | f633570a0165 | 1456baeb | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| l3s | 10m | 3 | 42 | 39~42 | 8.5% |

### sequential `2026-11-06-SEARCH-T10-10m-sequential-l3s-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 16 | 18 | 18 |
| high | popular | 2 | 17 | 18 | 18 |
| high | price_asc | 1 | 41 | 42 | 42 |
| high | price_asc | 2 | 96 | 105 | 108 |
| high | latest | 1 | 13 | 14 | 14 |
| high | latest | 2 | 13 | 13 | 13 |
| mid | popular | 1 | 16 | 16 | 16 |
| mid | popular | 2 | 16 | 16 | 17 |
| mid | price_asc | 1 | 35 | 38 | 39 |
| mid | price_asc | 2 | 99 | 111 | 114 |
| mid | latest | 1 | 12 | 12 | 12 |
| mid | latest | 2 | 13 | 13 | 13 |
| low | popular | 1 | 13 | 14 | 14 |
| low | popular | 2 | 15 | 15 | 15 |
| low | price_asc | 1 | 178 | 189 | 191 |
| low | price_asc | 2 | 183 | 210 | 216 |
| low | latest | 1 | 12 | 13 | 13 |
| low | latest | 2 | 13 | 14 | 14 |
| rare | popular | 1 | 13 | 13 | 13 |
| rare | popular | 2 | 14 | 15 | 15 |
| rare | price_asc | 1 | 20 | 20 | 20 |
| rare | price_asc | 2 | 21 | 21 | 21 |
| rare | latest | 1 | 12 | 12 | 12 |
| rare | latest | 2 | 12 | 14 | 14 |
| none | popular | 1 | 6 | 6 | 6 |
| none | price_asc | 1 | 7 | 7 | 7 |
| none | latest | 1 | 7 | 7 | 7 |


## 2026-11-06 10m l4

l4 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-06-SEARCH-T10-10m-steady-l4-r1` | steady | l4 | 10m | 22.4 | 22.08 | 18/85/140/496 (n=2,304) | 19/325/461/1,029 (n=3,978) | 0.00% | 200:3978 | - | 0 | 3.4/3.6 | 10/10 | 0.0031 | 0 | SELECT categories 58%, SET ? 12%, SET ? 11% | 96ab796662af | a62d488b | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l4-r2` | steady | l4 | 10m | 22.4 | 22.08 | 18/88/154/396 (n=2,304) | 18/321/455/835 (n=3,975) | 0.00% | 200:3975 | - | 0 | 2.7/3.0 | 10/10 | 0.0025 | 0 | SELECT categories 57%, SET ? 13%, COMMIT ? 11% | 96ab796662af | a62d488b | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l4-r3` | steady | l4 | 10m | 22.4 | 22.06 | 17/74/141/264 (n=2,304) | 17/323/446/938 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.0/3.0 | 10/10 | 0.0027 | 0 | SELECT categories 57%, SET ? 13%, SET ? 12% | 96ab796662af | a62d488b | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-sequential-l4-r1` | sequential | l4 | 10m | - | 28.44 | - | 15/177/표본 부족(n=81)/182 | 0.00% | 200:81 | - | 0 | ≈2.4/2.4 | 10/10 | ≈0.0019 | 0 | SELECT categories 62%, SET ? 13%, COMMIT ? 12% | 96ab796662af | a62d488b | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| l4 | 10m | 3 | 85 | 74~88 | 16.5% |

### sequential `2026-11-06-SEARCH-T10-10m-sequential-l4-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 16 | 17 | 17 |
| high | popular | 2 | 17 | 17 | 17 |
| high | price_asc | 1 | 41 | 41 | 41 |
| high | price_asc | 2 | 102 | 102 | 102 |
| high | latest | 1 | 12 | 13 | 14 |
| high | latest | 2 | 12 | 13 | 13 |
| mid | popular | 1 | 15 | 15 | 15 |
| mid | popular | 2 | 16 | 17 | 17 |
| mid | price_asc | 1 | 39 | 48 | 50 |
| mid | price_asc | 2 | 99 | 145 | 156 |
| mid | latest | 1 | 12 | 13 | 14 |
| mid | latest | 2 | 13 | 13 | 13 |
| low | popular | 1 | 14 | 16 | 16 |
| low | popular | 2 | 15 | 16 | 16 |
| low | price_asc | 1 | 177 | 178 | 178 |
| low | price_asc | 2 | 182 | 182 | 182 |
| low | latest | 1 | 12 | 13 | 13 |
| low | latest | 2 | 13 | 13 | 13 |
| rare | popular | 1 | 13 | 13 | 13 |
| rare | popular | 2 | 14 | 15 | 15 |
| rare | price_asc | 1 | 21 | 21 | 21 |
| rare | price_asc | 2 | 20 | 20 | 20 |
| rare | latest | 1 | 12 | 12 | 12 |
| rare | latest | 2 | 12 | 13 | 13 |
| none | popular | 1 | 6 | 7 | 7 |
| none | price_asc | 1 | 7 | 7 | 7 |
| none | latest | 1 | 6 | 6 | 7 |


## 2026-11-06 10m l5

l5 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-06-SEARCH-T10-10m-ramp-l5-r1` | ramp | l5 | 10m | 64 (128에서 실패) | 63.13 | 18/77/114/166 (n=1,646) | 20/98/140/219 (n=2,842) | 0.00% | 200:2842 | - | 0 | ≈3.7/3.7 | 10/10 | ≈0.0012 | 0 | SELECT categories 58%, SET ? 15%, COMMIT ? 11% | 533fc2978da2 | 722b4426 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l5-r1` | steady | l5 | 10m | 22.4 | 22.10 | 17/25/33/61 (n=2,305) | 17/68/91/141 (n=3,979) | 0.00% | 200:3979 | - | 0 | 3.3/3.7 | 10/10 | 0.0030 | 0 | SELECT categories 58%, SET ? 13%, SET ? 11% | 533fc2978da2 | 722b4426 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l5-r2` | steady | l5 | 10m | 22.4 | 22.08 | 17/25/31/72 (n=2,304) | 17/68/92/164 (n=3,975) | 0.00% | 200:3975 | - | 0 | 2.8/3.1 | 10/10 | 0.0025 | 0 | SELECT categories 58%, SET ? 13%, SET ? 11% | 533fc2978da2 | 722b4426 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-steady-l5-r3` | steady | l5 | 10m | 22.4 | 22.07 | 17/25/33/49 (n=2,304) | 17/68/91/158 (n=3,973) | 0.00% | 200:3973 | - | 0 | 2.8/2.8 | 10/11 | 0.0025 | 0 | SELECT categories 58%, SET ? 14%, SET ? 10% | 533fc2978da2 | 722b4426 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-spike-l5-r1` | spike | l5 | 10m | 96 | 25.18 | 95/229/279/331 (n=1,645) | 102/249/307/524 (n=3,022) | 0.00% | 200:3022 | - | 0 | 2.7/2.7 | 10/10 | ≈0.0022 | 0 | SELECT categories 58%, SET ? 15%, COMMIT ? 10% | 533fc2978da2 | 722b4426 | SEARCH-T06 |
| `2026-11-06-SEARCH-T10-10m-sequential-l5-r1` | sequential | l5 | 10m | - | 58.17 | - | 15/30/표본 부족(n=81)/35 | 0.00% | 200:81 | - | 0 | ≈2.1/2.1 | 10/10 | ≈0.0008 | 0 | SELECT categories 62%, SET ? 14%, COMMIT ? 12% | 533fc2978da2 | 722b4426 | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| l5 | 10m | 3 | 25 | 25~25 | 0.4% |

### ramp `2026-11-06-SEARCH-T10-10m-ramp-l5-r1`

최대 안정 요청률 64 req/s (128에서 실패). 대표 행은 64 req/s 단계 구간의 값이다. 처리 가능 요청률은 64 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 2.02 | 32.03 | 표본 부족(n=52) | 0 | 0 | ≈2.2 | 0 | SLO |
| 4 | 3.97 | 29.86 | 31.74 (n=103) | 0 | 0 | ≈2.3 | 0 | SLO |
| 8 | 8.04 | 29.03 | 29.88 (n=206) | 0 | 0 | ≈3.0 | 0 | SLO |
| 16 | 15.84 | 26.69 | 27.55 (n=412) | 0 | 0 | ≈3.0 | 0 | SLO |
| 32 | 31.57 | 31.56 | 47.54 (n=824) | 0 | 0 | ≈2.8 | 0 | SLO |
| 64 | 63.12 | 77.12 | 114.32 (n=1,646) | 0 | 0 | ≈3.7 | 0 | SLO |
| 128 | 96.47 | 8077.05 | 9856.47 (n=2,974) | 0 | 398 | ≈4.4 | 0 | 없음 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-06-SEARCH-T10-10m-spike-l5-r1` | 96 | 2 | 0.00% | 0.00% | 500ms | 1초 (회복 창 5s) | 0 |

### sequential `2026-11-06-SEARCH-T10-10m-sequential-l5-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 15 | 17 | 18 |
| high | popular | 2 | 18 | 18 | 18 |
| high | price_asc | 1 | 29 | 29 | 29 |
| high | price_asc | 2 | 30 | 31 | 31 |
| high | latest | 1 | 12 | 13 | 13 |
| high | latest | 2 | 12 | 14 | 14 |
| mid | popular | 1 | 16 | 16 | 16 |
| mid | popular | 2 | 17 | 17 | 17 |
| mid | price_asc | 1 | 29 | 29 | 29 |
| mid | price_asc | 2 | 30 | 32 | 32 |
| mid | latest | 1 | 12 | 14 | 14 |
| mid | latest | 2 | 14 | 17 | 18 |
| low | popular | 1 | 15 | 20 | 21 |
| low | popular | 2 | 16 | 16 | 16 |
| low | price_asc | 1 | 27 | 27 | 27 |
| low | price_asc | 2 | 25 | 33 | 35 |
| low | latest | 1 | 12 | 12 | 12 |
| low | latest | 2 | 12 | 13 | 13 |
| rare | popular | 1 | 13 | 14 | 14 |
| rare | popular | 2 | 15 | 16 | 16 |
| rare | price_asc | 1 | 18 | 19 | 19 |
| rare | price_asc | 2 | 19 | 19 | 19 |
| rare | latest | 1 | 11 | 11 | 11 |
| rare | latest | 2 | 13 | 13 | 13 |
| none | popular | 1 | 6 | 7 | 7 |
| none | price_asc | 1 | 7 | 7 | 7 |
| none | latest | 1 | 6 | 6 | 6 |

