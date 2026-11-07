# SEARCH-T03 측정 결과

지연은 ms, 요청률은 초당 HTTP 요청 수다. RDS CPU는 run 구간 안에 온전히 든 1분 지표의 평균이고, 요청당 RDS CPU초는 그 평균 CPU% / 100 x vCPU / 초당 요청 수다. ≈는 온전히 든 1분이 없어 겹친 시간으로 가중한 값이다. 요청당 RDS CPU초는 일정 도착률로 돈 steady에서 dropped가 0이고 실패율이 1% 미만일 때만 ≈ 없이 적는다.

## 2026-11-04 1m base

base 기준 요청률 0.25 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-04-SEARCH-T03-1m-steady-base-r1` | steady | base | 1m | 0.175 | 0.19 | 6,403/9,175/표본 부족(n=20)/10,480 | 5,841/7,966/표본 부족(n=35)/10,480 | 0.00% | 200:35 | - | 0 | 52.1/60.6 | 10/10 | 5.5727 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 20% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T03-1m-steady-base-r2` | steady | base | 1m | 0.175 | 0.19 | 6,393/7,884/표본 부족(n=20)/9,453 | 5,940/7,809/표본 부족(n=35)/9,453 | 0.00% | 200:35 | - | 0 | 53.5/60.1 | 10/10 | 5.6949 | 0 | SELECT products 35%, COUNT products 32%, SELECT products 19% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T03-1m-steady-base-r3` | steady | base | 1m | 0.175 | 0.18 | 6,318/9,436/표본 부족(n=19)/11,014 | 6,085/9,063/표본 부족(n=32)/11,014 | 0.00% | 200:32 | - | 0 | 48.1/55.3 | 10/10 | 5.4152 | 0 | SELECT products 35%, COUNT products 32%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T03-1m-sequential-base-r1` | sequential | base | 1m | - | 0.19 | - | 5,612/8,769/표본 부족(n=81)/8,935 | 0.00% | 200:81 | - | 0 | 50.7/52.1 | 10/10 | ≈5.2658 | 0 | COUNT products 33%, SELECT products 12%, SELECT products 12% | 8b9454805771 | 22eeed43 | none |
| `2026-11-04-SEARCH-T03-1m-explain-base-r1` | explain | base | 1m | - | - | - | - | - | - | - | 0 | 50.6/50.9 | 11/11 | - | 0 | COUNT products 28%, COUNT products 14%, EXPLAIN products 11% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base | 1m | 3 | 9175 | 7884~9436 | 16.9% |

### sequential `2026-11-04-SEARCH-T03-1m-sequential-base-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 8,769 | 8,902 | 8,935 |
| high | popular | 2 | 4,827 | 4,856 | 4,864 |
| high | price_asc | 1 | 8,709 | 8,823 | 8,851 |
| high | price_asc | 2 | 4,777 | 4,804 | 4,811 |
| high | latest | 1 | 8,779 | 8,844 | 8,860 |
| high | latest | 2 | 4,929 | 5,018 | 5,041 |
| mid | popular | 1 | 6,433 | 6,454 | 6,460 |
| mid | popular | 2 | 3,328 | 3,470 | 3,505 |
| mid | price_asc | 1 | 6,387 | 6,471 | 6,492 |
| mid | price_asc | 2 | 3,333 | 3,364 | 3,372 |
| mid | latest | 1 | 6,376 | 6,417 | 6,428 |
| mid | latest | 2 | 3,540 | 3,715 | 3,759 |
| low | popular | 1 | 5,882 | 5,951 | 5,968 |
| low | popular | 2 | 3,017 | 3,059 | 3,069 |
| low | price_asc | 1 | 5,825 | 5,842 | 5,846 |
| low | price_asc | 2 | 3,020 | 3,025 | 3,027 |
| low | latest | 1 | 5,886 | 5,920 | 5,928 |
| low | latest | 2 | 3,189 | 3,219 | 3,226 |
| rare | popular | 1 | 5,633 | 5,664 | 5,672 |
| rare | popular | 2 | 2,865 | 2,890 | 2,896 |
| rare | price_asc | 1 | 5,642 | 5,656 | 5,660 |
| rare | price_asc | 2 | 2,906 | 3,034 | 3,066 |
| rare | latest | 1 | 5,612 | 5,792 | 5,838 |
| rare | latest | 2 | 3,057 | 3,083 | 3,089 |
| none | popular | 1 | 5,641 | 5,667 | 5,673 |
| none | price_asc | 1 | 5,698 | 5,758 | 5,773 |
| none | latest | 1 | 5,675 | 5,689 | 5,693 |


## 2026-11-05 1m cell2

cell2 기준 요청률 0.25 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T03-1m-ramp-cell2-r1` | ramp (dropped는 마지막 단계) | cell2 | 1m | SLO 미충족 (처리 가능 0.25) | - | - | - | - | - | - | 0 | - | - | - | 0 | SELECT products 41%, COUNT products 38%, SELECT products 7% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-1m-steady-cell2-r1` | steady | cell2 | 1m | 0.175 | 0.19 | 6,162/8,180/표본 부족(n=20)/9,554 | 5,676/7,520/표본 부족(n=35)/9,554 | 0.00% | 200:35 | - | 0 | 47.6/57.0 | 10/10 | 5.0853 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 20% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-1m-steady-cell2-r2` | steady | cell2 | 1m | 0.175 | 0.19 | 6,106/7,328/표본 부족(n=20)/8,206 | 5,705/7,263/표본 부족(n=35)/8,206 | 0.00% | 200:35 | - | 0 | 51.0/55.5 | 10/10 | 5.4087 | 0 | SELECT products 35%, COUNT products 32%, SELECT products 20% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-1m-steady-cell2-r3` | steady | cell2 | 1m | 0.175 | 0.18 | 6,097/8,517/표본 부족(n=20)/9,860 | 5,832/8,209/표본 부족(n=33)/9,860 | 0.00% | 200:33 | - | 0 | 46.8/53.4 | 10/10 | 5.2909 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 17% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-1m-sequential-cell2-r1` | sequential | cell2 | 1m | - | 0.23 | - | 5,459/8,076/표본 부족(n=81)/8,334 | 0.00% | 200:81 | - | 0 | 49.1/51.9 | 10/10 | ≈4.3505 | 0 | COUNT products 37%, SELECT products 13%, SELECT products 13% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-1m-explain-cell2-r1` | explain | cell2 | 1m | - | - | - | - | - | - | - | 0 | 50.7/51.8 | 11/11 | - | 0 | COUNT products 27%, COUNT products 14%, EXPLAIN products 11% | 9c715056a644 | 33bf8f34 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| cell2 | 1m | 3 | 8180 | 7328~8517 | 14.5% |

### ramp `2026-11-05-SEARCH-T03-1m-ramp-cell2-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 0.25 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 0.12 | 29999.97 | 표본 부족(n=3) | 50 | 17 | ≈65.3 | 99 | 없음 |
| 1 | 0.2 | 30000.44 | 표본 부족(n=7) | 50 | 7 | ≈60.5 | 99 | 없음 |
| 0.5 | 0.3 | 30000.76 | 표본 부족(n=16) | 30.76 | 1 | ≈77.0 | 0 | 없음 |
| 0.25 | 0.24 | 9696.99 | 표본 부족(n=16) | 0 | 0 | 77.2 | 0 | 처리 기준만 |

### sequential `2026-11-05-SEARCH-T03-1m-sequential-cell2-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 8,122 | 8,137 | 8,140 |
| high | popular | 2 | 4,406 | 4,527 | 4,557 |
| high | price_asc | 1 | 8,008 | 8,193 | 8,239 |
| high | price_asc | 2 | 4,487 | 4,487 | 4,487 |
| high | latest | 1 | 8,056 | 8,278 | 8,334 |
| high | latest | 2 | 8 | 11 | 12 |
| mid | popular | 1 | 6,107 | 6,109 | 6,110 |
| mid | popular | 2 | 3,222 | 3,230 | 3,232 |
| mid | price_asc | 1 | 6,081 | 6,099 | 6,104 |
| mid | price_asc | 2 | 3,160 | 3,168 | 3,170 |
| mid | latest | 1 | 6,144 | 6,161 | 6,165 |
| mid | latest | 2 | 8 | 8 | 8 |
| low | popular | 1 | 5,620 | 5,623 | 5,624 |
| low | popular | 2 | 2,990 | 3,001 | 3,004 |
| low | price_asc | 1 | 5,765 | 5,766 | 5,766 |
| low | price_asc | 2 | 2,918 | 2,919 | 2,919 |
| low | latest | 1 | 5,628 | 5,631 | 5,632 |
| low | latest | 2 | 11 | 15 | 15 |
| rare | popular | 1 | 5,536 | 5,544 | 5,546 |
| rare | popular | 2 | 2,800 | 2,843 | 2,853 |
| rare | price_asc | 1 | 5,459 | 5,502 | 5,512 |
| rare | price_asc | 2 | 2,808 | 2,812 | 2,812 |
| rare | latest | 1 | 5,534 | 5,671 | 5,706 |
| rare | latest | 2 | 29 | 31 | 32 |
| none | popular | 1 | 5,466 | 5,492 | 5,498 |
| none | price_asc | 1 | 5,425 | 5,453 | 5,460 |
| none | latest | 1 | 5,497 | 5,503 | 5,504 |


## 2026-11-05 100k base

base 기준 요청률 2 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T03-100k-steady-base-r1` | steady | base | 100k | 1.4 | 1.39 | 553/759/859/977 (n=144) | 513/745/802/977 (n=250) | 0.00% | 200:250 | - | 0 | 35.5/35.5 | 10/10 | 0.5115 | 0 | SELECT products 37%, COUNT products 33%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-05-SEARCH-T03-100k-steady-base-r2` | steady | base | 100k | 1.4 | 1.38 | 543/753/776/839 (n=145) | 510/742/770/839 (n=249) | 0.00% | 200:249 | - | 0 | 34.5/35.2 | 10/10 | 0.5012 | 10 (디스크 읽기) | SELECT products 36%, COUNT products 32%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-05-SEARCH-T03-100k-steady-base-r3` | steady | base | 100k | 1.4 | 1.37 | 534/746/789/798 (n=144) | 508/738/778/798 (n=246) | 0.00% | 200:246 | - | 0 | 32.4/34.7 | 10/10 | 0.4752 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-05-SEARCH-T03-100k-sequential-base-r1` | sequential | base | 100k | - | 2.35 | - | 479/736/756/806 (n=1,020) | 0.00% | 200:1020 | - | 0 | 51.6/51.9 | 10/10 | ≈0.4400 | 1 (디스크 읽기) | COUNT products 32%, SELECT products 12%, SELECT products 12% | 8b9454805771 | 22eeed43 | none |
| `2026-11-05-SEARCH-T03-100k-explain-base-r1` | explain | base | 100k | - | - | - | - | - | - | - | 0 | ≈21.1/22.1 | 10/10 | - | 3 (디스크 읽기) | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base | 100k | 3 | 753 | 746~759 | 1.7% |

### sequential `2026-11-05-SEARCH-T03-100k-sequential-base-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 744 | 770 | 806 |
| high | popular | 2 | 409 | 421 | 447 |
| high | price_asc | 1 | 744 | 770 | 777 |
| high | price_asc | 2 | 416 | 422 | 448 |
| high | latest | 1 | 742 | 756 | 801 |
| high | latest | 2 | 422 | 434 | 519 |
| mid | popular | 1 | 544 | 559 | 602 |
| mid | popular | 2 | 286 | 294 | 392 |
| mid | price_asc | 1 | 544 | 563 | 740 |
| mid | price_asc | 2 | 288 | 297 | 328 |
| mid | latest | 1 | 544 | 563 | 618 |
| mid | latest | 2 | 303 | 317 | 413 |
| low | popular | 1 | 505 | 525 | 603 |
| low | popular | 2 | 262 | 269 | 316 |
| low | price_asc | 1 | 504 | 518 | 639 |
| low | price_asc | 2 | 262 | 271 | 314 |
| low | latest | 1 | 504 | 528 | 739 |
| low | latest | 2 | 279 | 288 | 333 |
| rare | popular | 1 | 481 | 493 | 500 |
| rare | popular | 2 | 249 | 255 | 307 |
| rare | price_asc | 1 | 481 | 491 | 500 |
| rare | price_asc | 2 | 250 | 254 | 300 |
| rare | latest | 1 | 482 | 493 | 591 |
| rare | latest | 2 | 215 | 219 | 225 |
| none | popular | 1 | 483 | 502 | 517 |
| none | price_asc | 1 | 486 | 496 | 584 |
| none | latest | 1 | 486 | 500 | 530 |


## 2026-11-05 100k cell1

cell1 기준 요청률 2 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T03-100k-steady-cell1-r1` | steady | cell1 | 100k | 1.4 | 1.40 | 531/746/843/1,429 (n=145) | 494/738/840/1,429 (n=253) | 0.00% | 200:253 | - | 0 | 33.6/34.3 | 10/10 | 0.4808 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 18% | 9c715056a644 | 4ea38578 | none |
| `2026-11-05-SEARCH-T03-100k-steady-cell1-r2` | steady | cell1 | 100k | 1.4 | 1.38 | 523/735/752/778 (n=144) | 497/723/743/778 (n=248) | 0.00% | 200:248 | - | 0 | 32.9/33.8 | 10/10 | 0.4786 | 0 | SELECT products 36%, COUNT products 32%, SELECT products 18% | 9c715056a644 | 4ea38578 | none |
| `2026-11-05-SEARCH-T03-100k-steady-cell1-r3` | steady | cell1 | 100k | 1.4 | 1.37 | 530/750/782/799 (n=144) | 497/727/766/799 (n=247) | 0.00% | 200:247 | - | 0 | 33.6/33.7 | 10/10 | 0.4908 | 0 | SELECT products 36%, COUNT products 33%, SELECT products 17% | 9c715056a644 | 4ea38578 | none |
| `2026-11-05-SEARCH-T03-100k-explain-cell1-r1` | explain | cell1 | 100k | - | - | - | - | - | - | - | 0 | ≈27.6/27.6 | 11/11 | - | 0 | COUNT products 29%, COUNT products 13%, EXPLAIN products 10% | 9c715056a644 | 4ea38578 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| cell1 | 100k | 3 | 746 | 735~750 | 2.0% |


## 2026-11-05 100k cell2

cell2 기준 요청률 2 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T03-100k-explain-cell2-r1` | explain | cell2 | 100k | - | - | - | - | - | - | - | 0 | ≈29.5/29.5 | 10/10 | - | 0 | COUNT products 28%, COUNT products 13%, EXPLAIN products 10% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-100k-ramp-cell2-r1` | ramp (dropped는 마지막 단계) | cell2 | 100k | SLO 미충족 (처리 가능 2) | - | - | - | - | - | - | 0 | - | - | - | 2 (디스크 읽기) | SELECT products 38%, COUNT products 35%, SELECT products 19% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-100k-sequential-cell2-r1` | sequential | cell2 | 100k | - | 2.46 | - | 465/663/680/788 (n=1,020) | 0.00% | 200:1020 | - | 0 | 51.3/51.6 | 10/10 | ≈0.4166 | 0 | COUNT products 32%, SELECT products 12%, SELECT products 12% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-100k-spike-cell2-r1` | spike | cell2 | 100k | 6 | 2.00 | 10,000/10,000/10,001/10,001 (n=103) | 6,662/10,001/10,001/10,001 (n=241) | 33.61% | 0:81 200:160 | timeout:81 | 0 | 85.5/85.5 | 10/10 | ≈0.8570 | 0 | SELECT products 44%, COUNT products 41%, SELECT products 7% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-100k-steady-cell2-r1` | steady | cell2 | 100k | 1.4 | 1.40 | 518/686/709/785 (n=145) | 491/665/700/785 (n=253) | 0.00% | 200:253 | - | 0 | 31.8/32.2 | 10/10 | 0.4550 | 0 | SELECT products 38%, COUNT products 33%, SELECT products 20% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-100k-steady-cell2-r2` | steady | cell2 | 100k | 1.4 | 1.38 | 522/685/720/737 (n=145) | 496/671/719/759 (n=249) | 0.00% | 200:249 | - | 0 | 31.8/32.6 | 10/10 | 0.4610 | 0 | SELECT products 37%, COUNT products 33%, SELECT products 18% | 9c715056a644 | 33bf8f34 | none |
| `2026-11-05-SEARCH-T03-100k-steady-cell2-r3` | steady | cell2 | 100k | 1.4 | 1.37 | 519/680/697/740 (n=144) | 493/669/691/740 (n=247) | 0.00% | 200:247 | - | 0 | 31.4/31.9 | 10/10 | 0.4580 | 0 | SELECT products 38%, COUNT products 33%, SELECT products 17% | 9c715056a644 | 33bf8f34 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| cell2 | 100k | 3 | 685 | 680~686 | 0.9% |

### ramp `2026-11-05-SEARCH-T03-100k-ramp-cell2-r1`

SLO를 지킨 단계가 없어 최대 안정 요청률은 0(SLO 미충족)이고 대표 행은 비어 있다. 처리 가능 요청률은 2 req/s다.

| 목표 req/s | 실제 req/s | 첫 페이지 p95 | 첫 페이지 p99 (n) | 실패율 % | dropped | RDS CPU 평균 % | k6 종료 | 통과 기준 |
|---|---|---|---|---|---|---|---|---|
| 2 | 1.98 | 777.89 | 표본 부족(n=52) | 0 | 0 | ≈46.1 | 0 | 처리 기준만 |
| 4 | 3.51 | 4582.16 | 5100.32 (n=100) | 0 | 5 | ≈60.9 | 0 | 없음 |
| 1 | 0.99 | 651.16 | 표본 부족(n=26) | 0 | 0 | ≈17.9 | 0 | 처리 기준만 |
| 0.5 | 0.49 | 682.09 | 표본 부족(n=16) | 0 | 0 | ≈8.0 | 0 | 처리 기준만 |
| 0.25 | 0.26 | 692.25 | 표본 부족(n=16) | 0 | 0 | 8.2 | 0 | 처리 기준만 |

### spike

| run | 목표 req/s | 탐침 req/s | burst 실패율 | probe 실패율 | 회복 기준 p95 | 회복 시간 | dropped |
|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T03-100k-spike-cell2-r1` | 6 | 1 | 45.33% | 14.29% | 822ms | 27초 (회복 창 5s) | 0 |

### sequential `2026-11-05-SEARCH-T03-100k-sequential-cell2-r1`

| 등급 | 정렬 | 페이지 | 중앙값 | p90 | 최대 |
|---|---|---|---|---|---|
| high | popular | 1 | 670 | 681 | 788 |
| high | popular | 2 | 371 | 379 | 600 |
| high | price_asc | 1 | 673 | 688 | 737 |
| high | price_asc | 2 | 380 | 386 | 395 |
| high | latest | 1 | 667 | 690 | 781 |
| high | latest | 2 | 387 | 406 | 437 |
| mid | popular | 1 | 516 | 531 | 580 |
| mid | popular | 2 | 274 | 282 | 306 |
| mid | price_asc | 1 | 519 | 544 | 641 |
| mid | price_asc | 2 | 276 | 283 | 290 |
| mid | latest | 1 | 517 | 539 | 640 |
| mid | latest | 2 | 291 | 298 | 309 |
| low | popular | 1 | 485 | 498 | 534 |
| low | popular | 2 | 254 | 261 | 318 |
| low | price_asc | 1 | 488 | 496 | 510 |
| low | price_asc | 2 | 255 | 262 | 297 |
| low | latest | 1 | 486 | 502 | 591 |
| low | latest | 2 | 273 | 280 | 316 |
| rare | popular | 1 | 469 | 478 | 484 |
| rare | popular | 2 | 242 | 251 | 301 |
| rare | price_asc | 1 | 471 | 478 | 516 |
| rare | price_asc | 2 | 244 | 248 | 252 |
| rare | latest | 1 | 471 | 487 | 575 |
| rare | latest | 2 | 212 | 218 | 287 |
| none | popular | 1 | 470 | 482 | 516 |
| none | price_asc | 1 | 469 | 480 | 486 |
| none | latest | 1 | 472 | 488 | 502 |


## 2026-11-05 100k base2

base2 기준 요청률 2 req/s (직접 지정). steady는 0.7배, spike는 3배로 돌렸다.

| run | 셀 | 변형 | 데이터 | 목표 req/s | 실제 req/s | 첫 페이지 p50/p95/p99/max (n) | 전체 p50/p95/p99/max (n) | 실패율 | 상태 코드 | 오류 코드 | dropped | RDS CPU 평균/최대 % | DB 접속 평균/최대 | 요청당 RDS CPU초 | 버퍼 풀 디스크 읽기 | 상위 다이제스트 | rev | jar | DDL |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `2026-11-05-SEARCH-T03-100k-steady-base2-r1` | steady | base2 | 100k | 1.4 | 1.40 | 547/754/797/835 (n=145) | 511/733/768/835 (n=253) | 0.00% | 200:253 | - | 0 | 35.2/36.2 | 10/10 | 0.5050 | 0 | SELECT products 37%, COUNT products 32%, SELECT products 19% | 8b9454805771 | 22eeed43 | none |
| `2026-11-05-SEARCH-T03-100k-steady-base2-r2` | steady | base2 | 100k | 1.4 | 1.38 | 551/780/804/838 (n=145) | 517/754/793/838 (n=249) | 0.00% | 200:249 | - | 0 | 33.2/34.2 | 10/10 | 0.4826 | 4 (디스크 읽기) | SELECT products 36%, COUNT products 32%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |
| `2026-11-05-SEARCH-T03-100k-steady-base2-r3` | steady | base2 | 100k | 1.4 | 1.37 | 551/768/813/833 (n=144) | 518/759/807/833 (n=247) | 0.00% | 200:247 | - | 0 | 35.1/35.9 | 10/10 | 0.5122 | 0 | SELECT products 36%, COUNT products 32%, SELECT products 18% | 8b9454805771 | 22eeed43 | none |

### steady 첫 페이지 p95

| 변형 | 데이터 | 반복 | 중앙값 | 최소~최대 | 폭(중앙값 대비) |
|---|---|---|---|---|---|
| base2 | 100k | 3 | 768 | 754~780 | 3.4% |

