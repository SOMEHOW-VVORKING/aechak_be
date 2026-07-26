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
- `size` 형식 검증(1~100)은 boot 계층(`ProductSearchRequest`의 `@field:Range` + `@Valid`)에서 끝난다. application의 `ProductSearchQuery`는 `init`에서 같은 범위를 자기 불변식으로 다시 지킨다 — 검증 3층(00 §3-5)의 형식/불변식 이중 방어.

---

## 8. 한계와 여지

- 커서에 서명이 없다. 무결성이 필요해지면 HMAC 등 서명 추가를 검토할 여지가 있다(지금은 오용 차단이 목적이라 태그·필터 일치 검사로 충분).
- 앵커 고정의 트레이드오프: 아주 오래 순회를 이어가면 그동안 가격이 바뀌어도 과거 스냅샷으로 정렬한다. 순회는 짧게 이어진다는 가정 위에 서 있으며, 세션이 길어질 여지가 크면 재조회 유도를 고려할 수 있다.
