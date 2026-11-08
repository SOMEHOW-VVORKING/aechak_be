# SEARCH-T11 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-08 10m final

final 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-r1` | steady | final | 10m | 22.4 | 22.10 | 11/19/23/442 (n=2,305) | 11/50/68/442 (n=3,979) | 0.00% | 200:3979 | - | 0 | 2.9/3.0 | 10/10 | 0.0026 | 0 | SELECT categories 61%, SET ? 13%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-r2` | steady | final | 10m | 22.4 | 22.08 | 10/18/19/67 (n=2,304) | 10/46/66/129 (n=3,975) | 0.00% | 200:3975 | - | 0 | 3.2/3.7 | 10/10 | 0.0029 | 0 | SELECT categories 61%, SET ? 13%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-r3` | steady | final | 10m | 22.4 | 22.07 | 10/18/19/280 (n=2,304) | 10/47/66/280 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.5/4.2 | 10/10 | 0.0032 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final | 10m | 3 | 18 | 18~19 | 5.9% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-r1` | steady | 없음 | - | - | - | 2,305 | 1,152 | 66.7% | 0 | 0 | 34569256 |
| `2026-11-08-SEARCH-T11-10m-steady-final-r2` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 35700664 |
| `2026-11-08-SEARCH-T11-10m-steady-final-r3` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 36829384 |


## 2026-11-08 10m final-diverse

final-diverse 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-r1` | steady | final-diverse | 10m | 22.4 | 22.10 | 26/66/150/351 (n=2,305) | 26/84/153/351 (n=3,979) | 0.00% | 200:3979 | - | 0 | 2.7/2.8 | 10/10 | 0.0024 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-r2` | steady | final-diverse | 10m | 22.4 | 22.08 | 23/62/148/210 (n=2,304) | 22/79/148/311 (n=3,974) | 0.00% | 200:3974 | - | 0 | 3.4/4.1 | 10/10 | 0.0031 | 0 | SELECT categories 61%, SET ? 13%, SET ? 13% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-r3` | steady | final-diverse | 10m | 22.4 | 22.07 | 23/62/147/355 (n=2,303) | 23/78/149/355 (n=3,972) | 0.00% | 200:3972 | - | 0 | 2.8/2.8 | 10/10 | 0.0025 | 0 | SELECT categories 61%, SET ? 13%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final-diverse | 10m | 3 | 62 | 62~66 | 7.1% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-r1` | steady | 없음 | - | - | - | 2,080 | 1,377 | 60.2% | 0 | 0 | 38244040 |
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-r2` | steady | 없음 | - | - | - | 2,304 | 1,152 | 66.7% | 0 | 0 | 39372920 |
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-r3` | steady | 없음 | - | - | - | 2,303 | 1,154 | 66.6% | 0 | 0 | 40500512 |


## 2026-11-08 10m final-nocache

final-nocache 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-nocache-r1` | steady | final-nocache | 10m | 22.4 | 22.10 | 11/19/24/411 (n=2,305) | 11/49/70/411 (n=3,979) | 0.00% | 200:3979 | - | 0 | 2.8/2.8 | 10/10 | 0.0025 | 0 | SELECT categories 61%, SET ? 13%, SET ? 11% | 4b23455c3959 | ff1f65e1 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-nocache-r2` | steady | final-nocache | 10m | 22.4 | 22.08 | 10/18/20/31 (n=2,304) | 10/47/69/98 (n=3,974) | 0.00% | 200:3974 | - | 0 | 3.1/3.5 | 10/10 | 0.0028 | 0 | SELECT categories 62%, SET ? 13%, SET ? 11% | 4b23455c3959 | ff1f65e1 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-nocache-r3` | steady | final-nocache | 10m | 22.4 | 22.07 | 10/18/21/322 (n=2,304) | 10/46/68/322 (n=3,973) | 0.00% | 200:3973 | - | 0 | 2.7/2.7 | 10/10 | 0.0024 | 0 | SELECT categories 62%, SET ? 13%, SET ? 11% | 4b23455c3959 | ff1f65e1 | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final-nocache | 10m | 3 | 18 | 18~19 | 8.3% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-nocache-r1` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 40500512 |
| `2026-11-08-SEARCH-T11-10m-steady-final-nocache-r2` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 40500512 |
| `2026-11-08-SEARCH-T11-10m-steady-final-nocache-r3` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 40500512 |


## 2026-11-08 10m final-write10

final-write10 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-write10-r1` | steady | final-write10 | 10m | 22.4 | 22.10 | 12/22/43/468 (n=2,305) | 12/57/89/515 (n=3,979) | 0.00% | 200:3979 | - | 0 | 2.8/3.0 | 10/10 | 0.0026 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-write10-r2` | steady | final-write10 | 10m | 22.4 | 22.08 | 14/36/247/746 (n=2,304) | 14/90/248/934 (n=3,974) | 0.00% | 200:3974 | - | 0 | 2.8/2.9 | 10/10 | 0.0026 | 0 | SELECT categories 62%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-write10-r3` | steady | final-write10 | 10m | 22.4 | 22.07 | 14/20/29/133 (n=2,303) | 13/56/90/139 (n=3,972) | 0.00% | 200:3972 | - | 0 | 3.3/4.5 | 10/10 | 0.0031 | 0 | SELECT categories 62%, SET ? 13%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final-write10 | 10m | 3 | 22 | 20~36 | 72.4% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-write10-r1` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.00 pool=1000 updated=1792 | 905 | 2,552 | 26.2% | 179 | 0 | 516408 |
| `2026-11-08-SEARCH-T11-10m-steady-final-write10-r2` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1791 calls=1791 errors=0 rate=10.01 pool=1000 updated=1791 | 896 | 2,560 | 25.9% | 179 | 0 | 279696 |
| `2026-11-08-SEARCH-T11-10m-steady-final-write10-r3` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.01 pool=1000 updated=1792 | 890 | 2,567 | 25.7% | 180 | 0 | 23808 |


## 2026-11-08 10m final-diverse-write10

final-diverse-write10 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-write10-r1` | steady | final-diverse-write10 | 10m | 22.4 | 22.10 | 21/83/227/544 (n=2,305) | 23/105/203/544 (n=3,979) | 0.00% | 200:3979 | - | 0 | 2.7/2.7 | 10/10 | 0.0025 | 0 | SELECT categories 60%, SET ? 13%, SET ? 13% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-write10-r2` | steady | final-diverse-write10 | 10m | 22.4 | 22.08 | 17/38/200/424 (n=2,304) | 17/78/176/489 (n=3,975) | 0.00% | 200:3975 | - | 0 | 2.7/2.8 | 10/10 | 0.0025 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-write10-r3` | steady | final-diverse-write10 | 10m | 22.4 | 22.07 | 16/35/188/440 (n=2,304) | 17/75/175/440 (n=3,973) | 0.00% | 200:3973 | - | 0 | 2.7/2.8 | 10/10 | 0.0025 | 1 (디스크 읽기) | SELECT categories 62%, SET ? 14%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final-diverse-write10 | 10m | 3 | 38 | 35~83 | 125.8% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-write10-r1` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.01 pool=1000 updated=1792 | 20 | 3,437 | 0.6% | 179 | 0 | 867296 |
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-write10-r2` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.00 pool=1000 updated=1792 | 0 | 3,458 | 0.0% | 180 | 0 | 529616 |
| `2026-11-08-SEARCH-T11-10m-steady-final-diverse-write10-r3` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1793 calls=1793 errors=0 rate=10.01 pool=1000 updated=1793 | 0 | 3,458 | 0.0% | 180 | 0 | 201192 |


## 2026-11-08 10m final-recheck

final-recheck 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-recheck-r1` | steady | final-recheck | 10m | 22.4 | 22.10 | 16/28/96/570 (n=2,304) | 16/71/124/760 (n=3,978) | 0.00% | 200:3978 | - | 0 | 3.8/4.2 | 10/10 | 0.0035 | 4 (디스크 읽기) | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-recheck-r2` | steady | final-recheck | 10m | 22.4 | 22.08 | 14/18/26/116 (n=2,304) | 14/51/80/130 (n=3,974) | 0.00% | 200:3974 | - | 0 | 3.2/3.2 | 10/10 | 0.0029 | 4 (디스크 읽기) | SELECT categories 60%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final-recheck-r3` | steady | final-recheck | 10m | 22.4 | 22.07 | 14/17/23/105 (n=2,304) | 14/47/78/105 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.4/3.4 | 10/10 | 0.0031 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final-recheck | 10m | 3 | 18 | 17~28 | 61.0% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final-recheck-r1` | steady | 없음 | - | - | - | 2,304 | 1,152 | 66.7% | 0 | 0 | 1167808 |
| `2026-11-08-SEARCH-T11-10m-steady-final-recheck-r2` | steady | 없음 | - | - | - | 2,304 | 1,152 | 66.7% | 0 | 0 | 2297552 |
| `2026-11-08-SEARCH-T11-10m-steady-final-recheck-r3` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 3426192 |


## 2026-11-08 10m t07

t07 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-t07-r1` | steady | t07 | 10m | 22.4 | 22.10 | 32/290/601/1,070 (n=2,304) | 32/326/659/1,763 (n=3,978) | 0.00% | 200:3978 | - | 0 | 3.3/3.7 | 10/10 | 0.0030 | 0 | SELECT categories 57%, SET ? 14%, SET ? 11% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-r2` | steady | t07 | 10m | 22.4 | 22.08 | 30/169/289/575 (n=2,304) | 26/246/577/2,142 (n=3,975) | 0.00% | 200:3975 | - | 0 | 3.5/3.8 | 10/10 | 0.0031 | 0 | SELECT categories 57%, SET ? 13%, COMMIT ? 12% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-r3` | steady | t07 | 10m | 22.4 | 22.07 | 30/164/283/551 (n=2,303) | 25/228/493/2,228 (n=3,972) | 0.00% | 200:3972 | - | 0 | 3.4/3.6 | 10/10 | 0.0031 | 0 | SELECT categories 58%, SET ? 14%, COMMIT ? 11% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| t07 | 10m | 3 | 169 | 164~290 | 74.8% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-t07-r1` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 0 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-r2` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 0 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-r3` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 0 |


## 2026-11-08 10m t07-diverse

t07-diverse 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-r1` | steady | t07-diverse | 10m | 22.4 | 22.10 | 89/313/527/986 (n=2,305) | 86/329/559/2,096 (n=3,979) | 0.00% | 200:3979 | - | 0 | 3.3/3.6 | 10/10 | 0.0030 | 0 | SELECT categories 56%, SET ? 15%, COMMIT ? 12% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-r2` | steady | t07-diverse | 10m | 22.4 | 22.08 | 93/353/556/937 (n=2,304) | 91/380/647/2,485 (n=3,974) | 0.00% | 200:3974 | - | 0 | 3.1/3.6 | 10/10 | 0.0028 | 0 | SELECT categories 58%, SET ? 13%, COMMIT ? 12% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-r3` | steady | t07-diverse | 10m | 22.4 | 22.06 | 83/296/497/1,042 (n=2,304) | 76/316/523/2,015 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.0/3.0 | 10/10 | 0.0027 | 0 | SELECT categories 56%, SET ? 14%, SET ? 11% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| t07-diverse | 10m | 3 | 313 | 296~353 | 18.3% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-r1` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 0 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-r2` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 0 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-r3` | steady | 없음 | - | - | - | 0 | 0 | - | 0 | 0 | 0 |


## 2026-11-08 10m t07-write10

t07-write10 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-t07-write10-r1` | steady | t07-write10 | 10m | 22.4 | 22.10 | 37/1,396/2,361/3,377 (n=2,304) | 36/1,463/2,296/4,010 (n=3,978) | 0.08% | 200:3975 503:3 | other:3 | 1 | 3.3/3.6 | 10/10 | ≈0.0030 | 0 | SELECT categories 56%, SET ? 16%, COMMIT ? 11% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-write10-r2` | steady | t07-write10 | 10m | 22.4 | 22.08 | 30/171/274/472 (n=2,304) | 25/254/588/2,190 (n=3,975) | 0.00% | 200:3975 | - | 0 | 3.4/3.7 | 10/10 | 0.0031 | 0 | SELECT categories 57%, SET ? 14%, SET ? 10% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-write10-r3` | steady | t07-write10 | 10m | 22.4 | 22.07 | 30/168/288/585 (n=2,303) | 25/236/515/2,175 (n=3,972) | 0.00% | 200:3972 | - | 0 | 3.0/3.0 | 10/10 | 0.0027 | 0 | SELECT categories 59%, SET ? 12%, COMMIT ? 11% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| t07-write10 | 10m | 3 | 171 | 168~1396 | 716.1% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-t07-write10-r1` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1791 calls=1789 errors=0 rate=10.01 pool=1000 updated=1791 | 0 | 0 | - | 177 | 0 | 0 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-write10-r2` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1791 calls=1791 errors=0 rate=10.01 pool=1000 updated=1791 | 0 | 0 | - | 180 | 0 | 0 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-write10-r3` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.01 pool=1000 updated=1792 | 0 | 0 | - | 181 | 0 | 0 |


## 2026-11-08 10m t07-diverse-write10

t07-diverse-write10 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-write10-r1` | steady | t07-diverse-write10 | 10m | 22.4 | 22.10 | 57/142/289/711 (n=2,305) | 57/173/340/1,661 (n=3,979) | 0.00% | 200:3979 | - | 0 | 3.2/3.7 | 10/10 | 0.0029 | 0 | SELECT categories 57%, SET ? 14%, COMMIT ? 11% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-write10-r2` | steady | t07-diverse-write10 | 10m | 22.4 | 22.08 | 56/147/283/638 (n=2,304) | 55/169/335/1,769 (n=3,975) | 0.00% | 200:3975 | - | 0 | 2.9/3.0 | 10/10 | 0.0027 | 0 | SELECT categories 58%, SET ? 14%, COMMIT ? 12% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-write10-r3` | steady | t07-diverse-write10 | 10m | 22.4 | 22.06 | 54/127/270/657 (n=2,304) | 54/161/314/1,680 (n=3,973) | 0.00% | 200:3973 | - | 0 | 2.9/3.3 | 10/10 | 0.0027 | 0 | SELECT categories 57%, SET ? 14%, COMMIT ? 12% | 235e95a53568 | 1d88d1b5 | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| t07-diverse-write10 | 10m | 3 | 142 | 127~147 | 13.8% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-write10-r1` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.01 pool=1000 updated=1792 | 0 | 0 | - | 180 | 0 | 0 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-write10-r2` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1791 calls=1791 errors=0 rate=10.01 pool=1000 updated=1791 | 0 | 0 | - | 181 | 0 | 0 |
| `2026-11-08-SEARCH-T11-10m-steady-t07-diverse-write10-r3` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.01 pool=1000 updated=1792 | 0 | 0 | - | 180 | 0 | 0 |


## 2026-11-08 10m final2

final2 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-r1` | steady | final2 | 10m | 22.4 | 22.10 | 12/20/34/351 (n=2,304) | 12/57/79/405 (n=3,978) | 0.00% | 200:3978 | - | 0 | 3.0/3.0 | 10/10 | 0.0027 | 0 | SELECT categories 60%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-r2` | steady | final2 | 10m | 22.4 | 22.08 | 11/17/19/124 (n=2,304) | 11/50/70/125 (n=3,975) | 0.00% | 200:3975 | - | 0 | 3.0/3.2 | 10/10 | 0.0028 | 0 | SELECT categories 60%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-r3` | steady | final2 | 10m | 22.4 | 22.07 | 11/17/20/106 (n=2,304) | 11/49/69/176 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.1/3.4 | 10/10 | 0.0028 | 0 | SELECT categories 55%, SET ? 17%, SET ? 13% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final2 | 10m | 3 | 17 | 17~20 | 17.2% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-r1` | steady | 없음 | - | - | - | 2,304 | 1,152 | 66.7% | 0 | 0 | 1149976 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-r2` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 2281352 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-r3` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 3410080 |


## 2026-11-08 10m final2-diverse

final2-diverse 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-r1` | steady | final2-diverse | 10m | 22.4 | 22.10 | 27/67/169/541 (n=2,305) | 27/87/162/541 (n=3,979) | 0.00% | 200:3979 | - | 0 | 2.9/3.4 | 10/10 | 0.0027 | 0 | SELECT categories 61%, SET ? 14%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-r2` | steady | final2-diverse | 10m | 22.4 | 22.08 | 24/64/165/377 (n=2,304) | 23/80/161/441 (n=3,975) | 0.00% | 200:3975 | - | 0 | 2.8/2.9 | 10/11 | 0.0026 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-r3` | steady | final2-diverse | 10m | 22.4 | 22.07 | 24/62/128/213 (n=2,304) | 23/76/151/247 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.0/3.1 | 10/10 | 0.0027 | 0 | SELECT categories 62%, SET ? 12%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final2-diverse | 10m | 3 | 64 | 62~67 | 7.1% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-r1` | steady | 없음 | - | - | - | 2,081 | 1,376 | 60.2% | 0 | 0 | 4824736 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-r2` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 5955304 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-r3` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 7082808 |


## 2026-11-08 10m final2-write10

final2-write10 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-write10-r1` | steady | final2-write10 | 10m | 22.4 | 22.10 | 13/24/30/446 (n=2,305) | 13/57/79/446 (n=3,979) | 0.00% | 200:3979 | - | 0 | 3.2/3.4 | 10/10 | 0.0029 | 0 | SELECT categories 60%, SET ? 14%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-write10-r2` | steady | final2-write10 | 10m | 22.4 | 22.08 | 15/39/405/756 (n=2,304) | 15/97/338/1,079 (n=3,975) | 0.00% | 200:3975 | - | 0 | 2.6/2.7 | 10/10 | 0.0024 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-write10-r3` | steady | final2-write10 | 10m | 22.4 | 22.07 | 15/24/28/307 (n=2,304) | 15/54/82/468 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.0/3.2 | 10/10 | 0.0027 | 0 | SELECT categories 60%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final2-write10 | 10m | 3 | 24 | 24~39 | 63.6% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-write10-r1` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.01 pool=1000 updated=1792 | 890 | 2,567 | 25.7% | 180 | 0 | 670216 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-write10-r2` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1791 errors=0 rate=10.01 pool=1000 updated=1792 | 904 | 2,554 | 26.1% | 179 | 0 | 375008 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-write10-r3` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.01 pool=1000 updated=1792 | 887 | 2,571 | 25.7% | 180 | 0 | 55976 |


## 2026-11-08 10m final2-diverse-write10

final2-diverse-write10 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-write10-r1` | steady | final2-diverse-write10 | 10m | 22.4 | 22.10 | 23/79/249/488 (n=2,304) | 24/96/196/488 (n=3,978) | 0.00% | 200:3978 | - | 0 | 2.8/2.8 | 10/10 | 0.0025 | 0 | SELECT categories 61%, SET ? 13%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-write10-r2` | steady | final2-diverse-write10 | 10m | 22.4 | 22.08 | 18/39/205/452 (n=2,304) | 19/72/180/513 (n=3,974) | 0.00% | 200:3974 | - | 0 | 3.1/3.2 | 10/10 | 0.0028 | 0 | SELECT categories 61%, SET ? 12%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-write10-r3` | steady | final2-diverse-write10 | 10m | 22.4 | 22.07 | 17/36/187/426 (n=2,304) | 18/65/173/426 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.1/3.5 | 10/10 | 0.0028 | 0 | SELECT categories 60%, SET ? 12%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final2-diverse-write10 | 10m | 3 | 39 | 36~79 | 109.8% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-write10-r1` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.00 pool=1000 updated=1792 | 12 | 3,444 | 0.3% | 180 | 0 | 582872 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-write10-r2` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.00 pool=1000 updated=1792 | 0 | 3,456 | 0.0% | 180 | 0 | 182240 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-write10-r3` | steady | write | value=10 at=0s for=180s | 1~180s | sent=1792 calls=1792 errors=0 rate=10.01 pool=1000 updated=1792 | 0 | 3,458 | 0.0% | 180 | 0 | 808672 |


## 2026-11-08 10m final2-diverse-after

final2-diverse-after 기준 요청률 32 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-after-r1` | steady | final2-diverse-after | 10m | 22.4 | 22.10 | 18/32/76/418 (n=2,304) | 19/64/130/418 (n=3,978) | 0.00% | 200:3978 | - | 0 | 2.9/3.4 | 10/10 | 0.0026 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-after-r2` | steady | final2-diverse-after | 10m | 22.4 | 22.08 | 17/29/60/255 (n=2,304) | 18/62/122/396 (n=3,975) | 0.00% | 200:3975 | - | 0 | 2.8/2.9 | 10/10 | 0.0025 | 0 | SELECT categories 62%, SET ? 13%, SET ? 11% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-after-r3` | steady | final2-diverse-after | 10m | 22.4 | 22.07 | 17/27/59/210 (n=2,304) | 17/60/115/261 (n=3,973) | 0.00% | 200:3973 | - | 0 | 3.0/3.2 | 10/10 | 0.0027 | 0 | SELECT categories 61%, SET ? 13%, SET ? 12% | 4d530ad7b75e | 96a277fd | SEARCH-T06 |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| final2-diverse-after | 10m | 3 | 29 | 27~32 | 16.2% |

### ES 요청 캐시와 교란

| run | 셀 | 교란 | 교란 값 | 교란 구간 | 교란 결과 | 캐시 적중 | 캐시 실패 | 적중률 | refresh | evictions | 끝 캐시 메모리 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-after-r1` | steady | 없음 | - | - | - | 2,073 | 1,383 | 60.0% | 0 | 0 | 1420736 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-after-r2` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 2551304 |
| `2026-11-08-SEARCH-T11-10m-steady-final2-diverse-after-r3` | steady | 없음 | - | - | - | 2,304 | 1,154 | 66.6% | 0 | 0 | 3678904 |

