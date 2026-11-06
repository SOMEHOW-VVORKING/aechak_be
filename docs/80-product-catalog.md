# 공개 상품 목록 조회 — 커서 페이지네이션과 유효가격 (80-product-catalog)

> **성격**
> - 이 문서는 아키텍처 규칙이 아니라 **기능 설계 기록**이다. 공개 상품 목록 조회(`GET /products`)를 왜 이렇게 지었는지를 남긴다.
> - 이 기능은 커서·순회(application)와 유효가격(domain)에 걸쳐 있어, 특정 계층 문서(10/20)가 아닌 **주제 문서**로 둔다(00 §중복 금지).
> - 관련 코드
>   - `application/product/service/ProductService` — 순회·앵커·검증
>   - `application/product/support/ProductCursorCodec` — 커서 인코딩/디코딩
>   - `application/support/CursorPageResult` — 출력 래퍼
>   - `application/product/facade/ProductFacade` — 트랜잭션 경계·표시가 조립
>   - `domain/product/product/ProductPricing` — 유효가격 계산

---

## 1. 개요

- **무엇**: 공개 상품 목록. 카테고리 옵셔널 필터, 정렬 `LATEST`(신상품순) / `PRICE_ASC`(낮은 유효가격순), 커서 페이지네이션.
- **흐름**: `GET /products` → `ProductController` → `ProductUseCase`(구현 `ProductFacade`) → `ProductService`.
- **트랜잭션**: 조회이므로 `ProductFacade`가 `@Transactional(readOnly = true)` 경계를 소유한다(20 §2-1).
- **출력**: `CursorPageResult<ProductSummaryResult>` — `items`, `totalCount?`, `nextCursor?`, `hasNext`.
- **키워드 검색과의 관계**: 키워드 검색(`GET /search/products`)은 노출 조건이 같지만 `visibleProductQuery`를 공유하지 않고 자기 기본 쿼리(`ProductKeywordSearchQuerySupport`)를 쓴다. 카테고리 체인 조인 대신 application이 계산한 활성 카테고리 id 목록으로 거르고, 셀러도 조인 대신 어댑터가 요청마다 구한 활성 셀러 id 목록(무료배송 필터면 기본 배송비 0원인 셀러만)으로 거른다.
  - 셀러 상호와 상품 통계는 왼쪽 조인으로만 붙인다. 셀러 조건을 내부 조인에 두면 옵티마이저가 셀러부터 읽고 셀러 인덱스로 상품을 하나씩 찾아서, 정렬 인덱스 순서로 읽다 멈추거나 상품 테이블을 순서대로 훑지 못한다.
- **키워드 검색의 읽는 경로**: 어댑터가 검색마다 최근 상품 2,000개 표본에서 상품명 일치 수와 검색 조건 전체(상품명, 노출 조건, 필터) 통과 수를 세서 세 경로 중 하나를 고른다.
  - FULLTEXT: `aechak.search.fulltext-enabled=true`이고 MATCH를 쓸 수 있는 검색어이며 상품명 일치 비율이 `aechak.search.fulltext-max-match-ratio`(기본 0.05)보다 낮으면 ngram FULLTEXT 인덱스(`ft_products_name`)에 `MATCH AGAINST`를 걸어 후보를 좁히고 같은 LIKE로 다시 걸러 결과를 LIKE와 같게 맞춘다.
  - 인덱스 순서: 아니면서 검색 조건 전체 통과 비율이 `aechak.search.index-walk-min-match-ratio`(기본 0.01) 이상이면 MATCH 없이 정렬 인덱스 순서로 읽다 limit+1 행에서 멈추고 총개수는 따로 센다.
  - 스캔: 나머지는 MATCH 없이 테이블 전체를 읽어 정렬한다. 정렬 열에 0을 더해(`review_count + 0`) 옵티마이저가 정렬 인덱스를 고르지 못하게 한다.
  - 일치 상품이 많은 검색어는 MATCH가 일치 상품을 FULLTEXT 인덱스에서 하나씩 찾아 읽어서 테이블을 순서대로 훑는 것보다 느리다. MySQL 옵티마이저는 MATCH 조건의 일치 수를 1행으로 추정해 늘 FULLTEXT부터 읽는다.
  - 일치 상품이 드문 검색어를 정렬 인덱스 순서로 읽으면 일치 행을 찾을 때까지 인덱스 항목마다 상품 행을 하나씩 찾아 읽어 테이블을 훑는 것보다 느리다. 옵티마이저는 `LIKE '%검색어%'`의 일치 비율을 실제와 관계없이 추정해 ORDER BY LIMIT이 정렬 인덱스와 맞으면 그 인덱스를 고른다. 그래서 이 두 판단을 어댑터가 표본으로 한다.
  - MATCH 검색어는 단어마다 연속한 2글자 조각을 모두 `+`로 요구하는 불리언 AND 검색이다(`강아지` → `+강아 +아지`). 구문 검색(`"강아지"`)은 일치 문서마다 토큰 위치를 확인해 흔한 검색어에서 느리다. AND 검색은 구문 검색보다 많이 찾고(`강아 아지 간식`, `아지강아`) 순서와 인접은 LIKE 재검사가 맞춘다.
  - MATCH가 LIKE 결과를 놓치는 검색어는 LIKE만 쓴다. 2글자 미만 조각이 있는 검색어(`개`, `사료 강`)는 2글자 ngram 토큰이 없어서다. ASCII 밖 구분 문자(가운데점, 전각 공백, 이모지)가 든 검색어는 ngram 파서의 구분 규칙과 검색어 분리 규칙이 어긋날 수 있어서다. 라틴 글자가 든 검색어(`cat`)는 `innodb_ft_enable_stopword`가 켜져 있으면 불용어가 든 토큰이 색인에서 빠져서다.
  - 영숫자와 `_` 밖의 문자(불리언 연산자 `+ - * ( ) < > ~ @ "` 포함)는 단어 구분으로 보고 버린다. 연산자로 해석되지 않고 LIKE 재검사가 글자 그대로 맞춘다.
  - 프로퍼티 기본값은 false다. 인덱스는 마이그레이션만 만들고 ddl-auto는 만들지 못해서, 인덱스가 없는 DB에서 켜면 MATCH가 오류 1191을 낸다.
- **키워드 검색의 인기순**: 리뷰 수 내림차순, 동률은 id 내림차순이다. 정렬 키와 커서 값은 `products.review_count`다.
  - product_stats 리뷰 수로 정렬하면 일치 행 전부를 조인해 정렬한 뒤에야 LIMIT을 적용한다. 리뷰 수를 상품 행에 복사해 두면 인덱스 순서 경로가 `ix_products_popular(review_count DESC, id DESC)` 순서로 읽다가 limit+1 행에서 멈출 수 있다.
  - 리뷰 projection 컨슈머(`ReviewRatingProjectionConsumer`)가 부르는 `ProductStatsService.applyReviewStats`가 product_stats upsert와 같은 트랜잭션에서 같은 절대값을 이 컬럼에 쓴다. 네이티브 UPDATE라 상품 `@Version`을 올리지 않는다.
  - 엔티티 컬럼은 `insertable = false, updatable = false`라 Hibernate가 쓰지 않는다. 셀러 상품 수정이 이 값을 덮어쓰지 않고 통계 반영과 셀러 수정이 낙관적 락으로 부딪히지도 않는다.
  - 표시용 리뷰 수와 평균 별점, 최소 평점 필터는 그대로 product_stats 조인에서 읽는다. 커서에 담는 리뷰 수는 이전 정렬 키(product_stats 리뷰 수, 통계 행이 없으면 0)와 같은 값이라 배포 전에 받은 커서도 이어진다.
  - 운영 DB에는 마이그레이션이 컬럼을 더하고 product_stats에서 한 번에 채운 뒤 인덱스를 만든다. 개발과 테스트 스키마는 엔티티의 컬럼과 `@Index` 선언으로 같은 이름의 컬럼과 인덱스를 받는다.
- **키워드 검색의 낮은 가격순**: 유효가격(할인 기간 안이면 할인가, 아니면 정가) 오름차순, 동률은 id 내림차순이다. 유효가격은 조회 시각에 따라 달라서 인덱스에 담지 못한다.
  - 인덱스 순서 경로는 상품을 할인 중인 것과 아닌 것으로 나눠 읽는다. 할인 중인 상품은 `ix_products_discount_price(discount_price, id DESC)`, 나머지는 `ix_products_regular_price(regular_price, id DESC)` 순서로 limit+1 행씩 읽고 유효가격, id 순서로 합친다. 두 쪽에서 유효가격은 각각 할인가, 정가와 같아서 커서 경계와 가격대 필터도 그 열로 건다.
  - 표본에서 일치 비율이 높은 쪽을 먼저 읽고, 그 쪽이 limit+1 행을 채우면 다른 쪽은 채운 마지막 가격까지만 읽는다. 일치 상품이 한쪽에만 있으면(예를 들어 일치 상품이 모두 할인 중) 다른 쪽 인덱스를 끝까지 훑게 되기 때문이다.
  - 정렬 인덱스 셋은 `inspection_status`를 앞에 두지 않는다. 앞에 두면 총개수 쿼리가 그 인덱스로 승인 상품 전부를 하나씩 찾아 읽어 테이블을 순서대로 훑을 때보다 느려진다.
- **키워드 검색의 총개수**: 첫 페이지(`cursor == null`)만 센다(`ProductKeywordSearchPort.searchFirstPage`). 이후 페이지는 세지 않고 `null`이다.
  - FULLTEXT와 스캔 경로는 목록 쿼리에 `count(*) over()`를 붙여 목록과 총개수를 한 번에 읽는다. 윈도 값은 LIMIT 전에 계산되므로 일치 행 전체 수다. 일치 행이 없으면 값을 실을 행도 없어서 0으로 둔다. MySQL은 윈도 값을 구하려고 일치 행 전부를 임시 테이블에 넣은 뒤 LIMIT을 적용하므로 일치 행이 적은 경로에만 쓴다.
  - 인덱스 순서 경로는 목록을 정렬 인덱스로 읽고 총개수는 따로 센다. 총개수 쿼리는 상품 테이블만 순서대로 훑고 셀러는 id 목록으로 거른다. 상품 통계는 최소 평점 필터가 있을 때만 붙인다. 조건은 상품명 LIKE를 맨 앞에 둬 일치하지 않는 행은 나머지 조건을 계산하지 않는다.
- **키워드 검색의 쿼리 타임아웃**: 표본, 목록, 총개수 쿼리에 `jakarta.persistence.query.timeout` 힌트를 건다. 값은 `aechak.search.query-timeout-ms`(기본 3000)다.
  - Hibernate가 밀리초를 초로 반올림해 JDBC에 넘기므로 500 미만은 타임아웃이 없는 것과 같다.
  - 시간을 넘기면 드라이버가 서버에 `KILL QUERY`를 보내 쿼리를 멈춘다. 서비스는 이 예외(`QueryTimeoutException`)를 `PRODUCT_SEARCH_TIMEOUT`(40008, 503)으로 바꿔 던진다.

---

## 2. 왜 오프셋이 아니라 커서(keyset)인가

- 오프셋(`LIMIT/OFFSET`)은 뒤 페이지로 갈수록 앞의 행을 세어 건너뛰느라 비용이 커지고, 조회 사이에 삽입·삭제가 일어나면 행이 밀려 **중복·누락**이 생긴다.
- keyset은 마지막으로 본 행의 정렬 키를 앵커로 삼아 다음 페이지의 `WHERE`에 쓴다 — 인덱스를 그대로 타고, 목록이 변해도 견고하다.
- 대가는 임의 페이지 점프가 불가능하고(다음 방향만) 정렬 키가 커서에 실린다는 점이다. 후자는 내부 id 대신 `publicId`를 실어 완화한다(§4).

---

## 3. 커서 구조 (`ProductCursorCodec`)

- base64url(패딩 없음)로 감싼 keyset 앵커다. **위변조 방지 서명이 아니다** — 디코드 실패나 형식 불일치는 조용히 통과시키지 않고 `INVALID_CURSOR`(400)로 거절한다.
- 페이로드에 **정렬 태그와 카테고리**를 함께 싣는다. 다른 정렬이나 다른 카테고리에서 받은 커서를 재사용하면 keyset이 엉뚱한 집합에 걸려 조용한 오답을 내므로, 이를 400으로 차단한다.

| 정렬 | 페이로드 | 앵커 키 |
| --- | --- | --- |
| `LATEST` | `l:{category\|all}:{publicId}` | `publicId` (= id 최신순) |
| `PRICE_ASC` | `p:{category\|all}:{sortPrice}:{anchorMillis}:{publicId}` | (유효가격, `publicId`) + 앵커 시각 |

- 내부 id를 직접 싣지 않고 `publicId`를 쓴다 — `publicId`는 클라이언트에 노출하는 외부 식별자로, 내부 자동증가 id와 분리된 값이다. 순회 시 `ProductService`가 `publicId`를 내부 id로 되돌린다(§4). 앵커 시각(`anchorMillis`)은 UTC 오프셋 기준 epoch millis로 왕복한다.

---

## 4. keyset 순회 (`ProductService.getVisiblePage`)

- `limit = size + 1`로 한 건 더 조회해, 초과분의 존재로 `hasNext`를 판정한다 — 다음 페이지 유무를 위해 별도 `COUNT`를 돌리지 않는다.
- `totalCount`는 **첫 페이지(`cursor == null`)에서만** 센다. 이후 페이지는 `null`로 두어 매 페이지 `COUNT`를 피한다.
- 커서 해석 `resolveCursor`의 순서
  1. `ProductCursorCodec.decode`로 페이로드 해석
  2. 커서의 `categoryId`와 요청 `categoryId` 대조 — 불일치면 400
  3. 앵커 시각이 **미래**면 400 (조작 차단, 과거는 정상)
  4. `findIdByPublicId`로 내부 id 확보 — 없으면 400
- `nextCursor`는 페이지 마지막 항목으로 재인코딩한다. `CursorPageResult`의 `init`이 `(nextCursor != null) == hasNext` 불변식을 강제해, 둘이 어긋난 응답을 만들 수 없다.

---

## 5. 가격 변동 처리 ★

이 기능에서 가장 세밀하게 다룬 지점이다.

- **문제**: `PRICE_ASC`는 유효가격(할인 반영가)으로 정렬한다. 유효가격은 시간에 종속적이다 — 할인 시작·종료 경계를 지나면 값이 바뀐다. 페이지를 넘기는 사이 시간이 흘러 어떤 상품이 경계를 넘으면, 그 상품의 정렬가가 달라지면서 keyset 앵커가 어긋나 **중복이나 누락**이 생긴다.
- **결정**: 첫 페이지 시각을 커서에 담고(`anchorMillis`), 이후 페이지의 정렬·조회를 **그 시각의 유효가격 뷰로 고정**한다.
  - `ProductService`: `queryNow = anchor?.anchorNow ?: now`. 조회 조건의 `now`와 다음 커서의 시각에 `queryNow`를 쓴다.
  - 효과: 한 번 시작한 순회는 같은 가격 스냅샷 위를 걷는다 — 페이지 경계에서 흔들리지 않는다.
- **정렬가와 표시가의 시각 분리**: 화면에 찍히는 가격은 정렬가와 다른 시각을 쓴다. `ProductFacade`가 응답을 조립할 때 **현재 시각**(`now`)으로 `ProductSummaryResult`를 계산해, 만료된 할인가를 계속 보여주지 않는다.

| 목적 | 쓰는 시각 | 이유 |
| --- | --- | --- |
| 정렬·keyset | 첫 페이지 시각(앵커 고정) | 순회 중 중복·누락 차단 |
| 화면 표시가 | 현재 시각 | 만료된 할인 노출 방지 |

- 즉 **정렬 일관성**(과거 앵커 시각)과 **화면 정확성**(현재 시각)을 서로 다른 시각으로 각각 만족시킨다.

---

## 6. 유효가격 계산 (`ProductPricing`)

- `discountedPriceAt(at)`: 할인 기간 안이면 할인가, 아니면 `null`. 기간이 비어 있으면 상시 할인으로 본다.
- `sellingPriceAt(at)`: 할인가가 있으면 할인가, 없으면 정가.
- 등록 시점의 입력 불변식(할인가·기간의 정합성)은 `Product.register`가 소유한다. `ProductPricing`은 DB projection에서도 재구성되므로 **생성자에 새 검증을 넣지 않는다** — 넣으면 기존 데이터 조회가 깨진다.

---

## 7. 검증과 에러

- 커서 이상(디코드 실패, 형식·태그 불일치, 필터 불일치, 미래 시각, 없는 `publicId`)은 전부 `INVALID_CURSOR`(400)로 모은다 — 위조든 다른 정렬의 커서든 같은 응답.
- 카테고리 필터는 **중분류(depth 2)까지만** 허용한다(`validateCategoryFilter`). 활성 카테고리가 아니면 `CATEGORY_NOT_FOUND`.
- 키워드 검색 쿼리가 타임아웃되면 `PRODUCT_SEARCH_TIMEOUT`(40008, 503)이다. 과부하 때 생기는 실패라 잠시 뒤 다시 요청하면 된다.
- `size` 형식 검증(1~100)은 boot 계층(`ProductSearchRequest`의 `@field:Range` + `@Valid`)에서 끝난다. application의 `ProductSearchQuery`는 `init`에서 같은 범위를 자기 불변식으로 다시 지킨다 — 검증 3층(00 §3-5)의 형식/불변식 이중 방어.

---

## 8. 한계와 여지

- 커서에 서명이 없다. 무결성이 필요해지면 HMAC 등 서명 추가를 검토할 여지가 있다(지금은 오용 차단이 목적이라 태그·필터 일치 검사로 충분).
- 앵커 고정의 트레이드오프: 아주 오래 순회를 이어가면 그동안 가격이 바뀌어도 과거 스냅샷으로 정렬한다. 순회는 짧게 이어진다는 가정 위에 서 있으며, 세션이 길어질 여지가 크면 재조회 유도를 고려할 수 있다.
