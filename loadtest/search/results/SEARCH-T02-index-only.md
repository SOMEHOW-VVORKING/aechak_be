# SEARCH-T02 쿼리 그대로 인덱스만 추가

2026-11-04에 AWS perf 스택에서 검색 쿼리는 바꾸지 않고 인덱스만 하나씩 더해 키워드 상품 검색 `GET /api/v1/search/products`의 실행 계획과 지연이 바뀌는지 잰 기록이다. 인덱스마다 base 위에 하나만 더하고 다음 인덱스 전에 지웠다. 지연은 ms, 요청률은 초당 HTTP 요청 수(req/s)다. run별 전체 표는 `SEARCH-T02.md`에 있다.

## 요약

- 후보 인덱스 다섯 개 가운데 실행 계획에 쓰인 것은 셀러 커버링 인덱스 `ix_sellers_active` 하나다. 10만 건과 100만 건 모두 같다. 이 인덱스도 셀러 1,000행을 읽는 단계만 테이블 스캔에서 커버링 인덱스 스캔으로 바꿨고 상품을 읽고 정렬하는 단계는 그대로다.
- `ix_sellers_active`는 지연을 바꾸지 못했다. steady 첫 페이지 p50 중앙값은 10만 건에서 base 580ms, 인덱스 591ms, base2 593ms이고 100만 건에서 base 6,675ms, 인덱스 6,599ms, base2 6,580ms다. 두 크기 모두 인덱스 값이 같은 jar와 스키마를 앞뒤로 두 번 잰 base와 base2 사이에 있다.
- 노출 조건 인덱스, 상품명 커버링 인덱스, 리뷰 수 정렬 인덱스, FULLTEXT ngram 인덱스는 계획에 나오지 않았다. 옵티마이저는 네 경우 모두 셀러를 먼저 읽고 `ix_products_seller_id`로 상품을 찾는 base 계획을 그대로 썼다.
- 유지할 인덱스는 없다. 비용은 인덱스로 줄일 수 없는 곳에 있다. 일치하는 상품마다 하는 조인과 식으로 된 정렬 키, 선행 와일드카드 LIKE다. 앞의 둘은 T03(검색 전용 기본 쿼리)에서 다루고 LIKE는 T04(FULLTEXT 술어)에서 다룬다.

## 가설

선행 와일드카드 `lower(name) LIKE '%검색어%'`는 어떤 인덱스도 쓰지 못한다. FULLTEXT 인덱스가 있어도 쿼리가 `MATCH`를 쓰지 않으면 옵티마이저는 무시한다. 노출 조건 인덱스는 행 대부분이 조건을 통과해 선택도가 없다. 리뷰 수 인덱스는 LEFT JOIN 위의 CASE 정렬 키에 쓰이지 않는다. 셀러 커버링 인덱스는 셀러가 1,000행뿐이라 효과가 작다. 첫 페이지 지연 변화는 0%에서 15%로 예상했다.

## 변경 내용

| 인덱스 | 테이블 | 열 | 노린 곳 |
|---|---|---|---|
| `ix_products_visible` | products | (inspection_status, sale_status, id) | 노출 조건 |
| `ix_products_visible_name` | products | (inspection_status, sale_status, id, name) | 노출 조건과 상품명 커버링 |
| `ix_product_stats_review` | product_stats | (review_count DESC, product_id DESC) | 인기순 정렬 |
| `ix_sellers_active` | sellers | (user_id, status, store_name) | 셀러 조인 커버링 |
| `ft_products_name` | products | FULLTEXT (name) WITH PARSER ngram | 상품명 역색인 |

- DDL은 `ddl/SEARCH-T02-<id>.sql`, 되돌리는 문장은 `results/rollback/SEARCH-T02-<id>.sql`이다. 셀러 커버링 인덱스의 세 번째 열은 검색 쿼리가 sellers에서 실제로 읽는 `store_name`이다.
- 인덱스 변형은 explain 셀을 먼저 돌려 새 인덱스가 계획에 나오는지 봤다. 계획에 나온 인덱스만 같은 요청률로 steady를 더 돌았다. base는 10만 건에서 전체 셀, 100만 건에서 ramp와 steady와 explain을 돌았다. 마지막에 같은 jar와 스키마로 base2(A/A)를 steady만 다시 돌아 시간에 따른 흔들림을 쟀다.
- FULLTEXT는 처음 만들 때 테이블을 다시 만들고 지운 뒤에도 숨은 `FTS_DOC_ID` 열과 보조 테이블을 남긴다. 그래서 B-tree 인덱스를 모두 잰 뒤 맨 끝에 재고 지웠다.
- kit 변경. `explain.sh`가 부하의 필터 요청과 같은 모양(가격 범위만, 가격 범위와 카테고리와 무료배송)도 EXPLAIN ANALYZE로 돈다. 10만 건 base를 시작한 뒤에 바꿔서 그 run만 kit 해시가 `48d871d6b9ff`이고 나머지 run은 모두 `0484d429c131`이다. 바뀐 파일은 `explain.sh` 하나라 부하 셀 비교에는 영향이 없다.

## 환경

T01과 같은 perf 측정층(api m7i.large, 러너 c7g.large, RDS db.m7g.large)이다. 10만 건은 16:18부터 18:09까지 쟀고 100만 건은 18:16부터 23:30까지 쟀다. jar는 T01과 같은 `8b9454805771`이다.

## 측정 결과

### 인덱스별 계획 사용

| 인덱스 | 10만 건 | 100만 건 | 바뀐 계획 |
|---|---|---|---|
| `ix_products_visible` | 안 쓰임 | 안 쓰임 | 없음 |
| `ix_products_visible_name` | 안 쓰임 | 안 쓰임 | 없음 |
| `ix_product_stats_review` | 안 쓰임 | 안 쓰임 | 없음 |
| `ix_sellers_active` | 쓰임 | 쓰임 | 셀러 테이블 스캔이 커버링 인덱스 스캔으로 바뀜(계획 42개 중 38개) |
| `ft_products_name` | 안 쓰임 | 안 쓰임 | 없음 |

`ix_sellers_active`를 쓰지 않은 계획 4개는 무료배송 필터가 붙은 요청이다. 무료배송 조건은 이 인덱스에 없는 `base_shipping_fee`를 읽어서 테이블 스캔이 남았다.

10만 건, 강아지 인기순 첫 페이지 목록 쿼리의 EXPLAIN ANALYZE(두 번째 실행)에서 셀러와 상품을 읽는 줄이다.

```
base
-> Limit: 21 row(s)  (actual time=503..503 rows=21 loops=1)
-> Table scan on s1_0  (cost=103 rows=1000) (actual time=0.0723..0.742 rows=1000 loops=1)
-> Index lookup on p1_0 using ix_products_seller_id (seller_id=s1_0.user_id)  (cost=25.7 rows=103) (actual time=0.0272..0.227 rows=100 loops=990)

ix_sellers_active
-> Limit: 21 row(s)  (actual time=512..512 rows=21 loops=1)
-> Covering index scan on s1_0 using ix_sellers_active  (cost=103 rows=1000) (actual time=0.0686..0.662 rows=1000 loops=1)
-> Index lookup on p1_0 using ix_products_seller_id (seller_id=s1_0.user_id)  (cost=25.7 rows=103) (actual time=0.0279..0.232 rows=100 loops=990)
```

셀러를 읽는 단계는 1ms 미만이고 쿼리 전체는 500ms 남짓이다. 시간 대부분은 셀러 990명마다 상품을 찾아 일치한 47,000행을 조인하고 정렬하는 데 든다. 셀러 단계를 줄여도 전체 시간은 거의 그대로다.

### steady

steady는 base 처리 가능 요청률의 0.7배로 돌았다. 10만 건은 1.4 req/s이고 100만 건은 0.18 req/s다. 실패는 없다.

| 변형 | 크기 | 첫 페이지 p50 중앙값 (반복별) | 첫 페이지 p95 중앙값 (반복별) | 요청당 RDS CPU초 (반복별) |
|---|---|---|---|---|
| base | 10만 건 | 580 (580, 580, 579) | 791 (794, 791, 785) | 0.51, 0.39, 0.47 |
| `ix_sellers_active` | 10만 건 | 591 (599, 591, 586) | 801 (801, 809, 801) | 0.52, 0.40, 0.48 |
| base2 | 10만 건 | 593 (598, 586, 593) | 803 (802, 803, 810) | 0.43, 0.53, 0.40 |
| base | 100만 건 | 6,675 (6,675, 6,627, 6,693) | 9,529 (9,636, 8,503, 9,529) | 5.82, 6.35, 5.36 |
| `ix_sellers_active` | 100만 건 | 6,599 (6,599, 6,582, 6,608) | 9,478 (9,478, 7,985, 9,599) | 5.83, 5.97, 5.81 |
| base2 | 100만 건 | 6,580 (6,558, 6,580, 6,637) | 9,432 (9,432, 7,935, 9,495) | 5.97, 6.24, 5.59 |

- 두 크기 모두 인덱스 변형이 base와 base2 사이에 있다. 같은 jar와 스키마를 다시 잰 base2는 base보다 p50이 10만 건에서 2.2% 느렸고 100만 건에서 1.4% 빨랐다. 인덱스 변형과 base의 차이(10만 건 1.9% 느림, 100만 건 1.1% 빠름)는 이 흔들림 안이다.
- 100만 건 p95는 반복마다 첫 페이지 표본이 20개라 반복 사이 폭이 12%에서 17%다. 그래서 100만 건은 p50으로 판정했다.
- 요청당 RDS CPU초도 세 변형이 같은 범위에 있다.

### FULLTEXT 인덱스 생성 시간

10만 건 4초, 100만 건 49초다. 러너 진행 로그에서 DDL을 적용한 시각과 다음 단계 시각의 차이라서 1초 단위다. 100만 건 값은 T00에서 사본 테이블로 잰 49초와 같다.

## 결정

- 유지할 인덱스는 없다. 그래서 엔티티 `@Index`와 마이그레이션은 더하지 않는다.
- FULLTEXT 인덱스는 T04에서 술어를 `MATCH ... AGAINST`로 바꿀 때 다시 만든다.
- T03의 base는 이 단계를 마친 스키마다. 인덱스는 없고 FULLTEXT를 지운 뒤 남은 `FTS_DOC_ID` 열과 보조 테이블이 있다.

## 측정의 한계

- 인덱스는 하나씩만 더했고 조합은 재지 않았다.
- 100만 건 `ix_sellers_active` steady 도중(19:51) 측정을 지휘하는 노트북이 절전 모드에 들어갔다. 부하는 러너가 예정대로 19:56부터 20:07까지 돌렸고 결과는 22:50에 S3에서 받았다. 세 반복 모두 kit 해시와 반복별 순번 오프셋이 다른 run과 같다. 그 뒤로 RDS가 2시간 넘게 유휴 상태였고 base2는 22:52에 돌았다.
- 10만 건 base의 explain에는 필터 요청 모양이 없다(위 kit 변경). 10만 건 필터 요청의 계획은 인덱스 변형의 explain에서만 봤다.
- FULLTEXT 흔적 때문에 이 단계 뒤의 products 테이블은 T01과 물리적으로 같지 않다.

## 재현

```bash
make variant STEP=SEARCH-T02 DATASET=100k VARIANT=base REV=8b9454805771
make variant STEP=SEARCH-T02 DATASET=100k VARIANT=sellers-active REV=8b9454805771 DDL=SEARCH-T02-sellers-active CELLS=explain
make variant STEP=SEARCH-T02 DATASET=100k VARIANT=sellers-active-load REV=8b9454805771 DDL=SEARCH-T02-sellers-active BASE_RATE=2 CELLS=steady
make variant STEP=SEARCH-T02 DATASET=100k VARIANT=base2 REV=8b9454805771 BASE_RATE=2 CELLS=steady
make variant STEP=SEARCH-T02 DATASET=1m VARIANT=base REV=8b9454805771 RAMP_FLOOR_RATES="1 0.5 0.25 0.125" CELLS="ramp steady explain"
# 인덱스 변형 사이에는 러너에서 results/rollback/SEARCH-T02-<id>.sql을 실행해 인덱스를 지운다
```
