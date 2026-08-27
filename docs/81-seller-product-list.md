# 셀러 상품 목록 조회 — 오프셋 페이지네이션과 재고 원천 판정 (81-seller-product-list)

> **성격**
> - 이 문서는 아키텍처 규칙이 아니라 **기능 설계 기록**이다. 셀러센터의 내 상품 목록(`GET /sellers/me/products`)과
>   옵션 재고 조회(`GET /sellers/me/products/{productId}/options`)를 왜 이렇게 지었는지를 남긴다.
> - 관련 코드
>   - `application/product/usecase/SellerProductUseCase` — 셀러 소비자 기준 계약
>   - `application/product/facade/ProductFacade` — 트랜잭션 경계·조회 자격 게이트
>   - `application/product/port/SellerProductQueryPort` — 목록·옵션 재고 조회 포트
>   - `application/support/OffsetPageResult` — 오프셋 출력 래퍼
>   - `infra/persistence/jpa/.../SellerProductQueryAdapter` — QueryDSL 구현

---

## 1. 개요

- **무엇**: 셀러 자신의 상품 목록. 상품명 키워드·판매 상태(복수)·검수 상태(복수)·카테고리(하위 포함)·등록일 범위·재고 유무 필터,
  정렬 `latest`(기본) / `price_asc` / `price_desc`, 오프셋 페이지네이션(`page`/`size`).
- **흐름**: `GET /sellers/me/products` → `SellerProductController` → `SellerProductUseCase`(구현 `ProductFacade`) → `ProductService`.
- **출력**: `OffsetPageResult<SellerProductSummaryResult>` — `items`, `totalCount`, `page`, `size`, 파생값 `totalPages`/`hasNext`.
- 공개 카탈로그(80)와 달리 **노출 조건을 걸지 않는다** — 미승인·판매중지·품절 상품도 본인에게는 전부 보이고, 상태는 필터로 거른다.

---

## 2. 왜 커서(80)가 아니라 오프셋인가

- 공개 카탈로그가 커서를 쓴 이유(80 §2 — 전체 상품 규모, 순회 중 목록 변동)가 여기 해당하지 않는다.
  조회 대상이 셀러 한 명의 상품으로 잠겨 규모가 작고, 조회 중 변동의 주체도 셀러 자신뿐이다.
- 셀러센터는 테이블 UI라 **임의 페이지 점프**가 필요하다 — 커서는 다음 방향만 가능해서 맞지 않는다.
- 필터 조합이 많아(6종) 커서에 필터 전부를 실어 일치 검증해야 하는 부담도 오프셋에는 없다.
- 매 페이지 `COUNT`가 도는 것이 오프셋의 비용이지만, 본인 상품 수 범위에서는 문제가 되지 않는다.

---

## 3. 조회 자격 게이트

| 셀러 상태 | 목록·옵션 조회 | 근거 |
| --- | --- | --- |
| ACTIVE / PAUSED / WITHDRAWAL_REQUESTED | 허용 | 휴점·탈퇴 신청 중에도 자기 상품은 관리 대상 |
| SUSPENDED / WITHDRAWN / 셀러 아님 | 403 (40006) | 제재·자격 소멸 상태는 셀러센터 이용 차단 |

- 등록(ACTIVE 전용, 40004)보다 넓고, 완전 개방보다 좁다 — 쓰기 자격과 조회 자격을 분리한 것.
- 게이트는 등록 선례를 따라 `ProductFacade`에 있다. 상태 판정 재료는 seller BC가 `SellerUseCase.getSellerStatus`로 제공하고,
  **어느 상태를 허용할지는 소비자인 product 쪽이 정한다** — 셀러 BC에 상품 정책을 심지 않기 위해서다.
- 옵션 조회의 소유권 검증은 서비스 계층에서 한 번의 조회로 한다(20 §6). 없는 상품 404(40000), 남의 상품 403(40007) 구분.

---

## 4. 재고 — 판매 상태가 아니라 원천 데이터로 판정 ★

- 재고의 원천은 **옵션 조합(`option_combinations`)의 `stock_quantity`** 다. 옵션 없이 등록해도 "기본" 조합 1행에 재고가 실린다.
- `totalStock`은 **활성(`is_active`) 조합의 재고 합**을 SQL 스칼라 서브쿼리로 계산한다. 비활성화 기능은 아직 쓰는 곳이 없지만,
  조합 비활성화가 생겨도 합계가 어긋나지 않도록 미리 활성 기준으로 자른다.
- 재고 필터(`stock=in_stock|sold_out`)도 같은 기준 — "재고가 남은 활성 조합의 존재 여부"(EXISTS)로 가른다.
- `saleStatus=OUT_OF_STOCK` 필터로 대신하지 않은 이유: 재고가 0이 되어도 판매 상태를 바꾸는 로직이 아직 없다(주문·재고 차감 미착수).
  상태는 갱신 시점의 스냅샷이라 어긋날 수 있고, 원천 데이터 판정은 항상 현재가 맞다.

---

## 5. 옵션 재고는 목록이 아니라 별도 조회로

- 목록 행에는 `totalStock`만 싣고, 조합별 재고는 행 클릭 시 모달이 `GET /sellers/me/products/{productId}/options`로 가져온다 —
  목록 응답 비대화를 피하면서 등록 시 입력한 조합별 재고를 그대로 보여준다.
- 구매자 옵션 조회(`GET /products/{id}/options`)를 재사용하지 않은 이유 — 노출 조건 말고도 차이가 있다.
  1. 구매자는 활성 그룹·값·조합만 + 노출 목록에 없는 값을 참조하는 조합 제외 규칙(선택 UI 정합성)이 붙는다. 셀러는 비활성 포함 전체.
  2. 구매자 응답은 재고를 임계치 이하에서만 노출(마스킹)하지만 셀러는 원값을 그대로 본다.
  3. 셀러 모달은 그룹/값 구조 자체가 필요 없다 — 조합명("화이트 / M")에 옵션값이 이미 박혀 있어 조합 행만 주면 된다.
- 그래서 셀러 쪽은 조합 단순 SELECT 하나로 끝난다. 동적 쿼리로 합치면 남는 건 flag 분기와
  구매자 규칙 변경이 셀러 응답을 건드리는 결합뿐이라 분리했다. 셀러 옵션 "수정" 화면이 그룹/값 구조까지 필요해지면 조립 로직 공유를 재검토한다.

---

## 6. 필터 값 표기

- 상태 필터(`saleStatus`·`inspectionStatus`)는 **응답에 나가는 enum 이름 그대로**(`ON_SALE` 등, 복수 지정 가능) 받는다 —
  FE가 응답 값을 그대로 되돌려 보내면 되고, 소문자 변환표를 양방향으로 유지할 필요가 없다.
- `sort`·`stock`은 응답에 등장하지 않는 표현용 어휘라 구매자 `sort=latest` 선례대로 소문자(`price_asc`, `in_stock` 등)를 쓴다.
- 등록일 필터는 `yyyy-MM-dd` 날짜로 받고 `[from 00:00, to+1일 00:00)` 반개구간으로 환산한다 — 상한 날짜가 통째로 포함된다.

---

## 7. 검증과 에러

- 형식 오류(모르는 enum 값·날짜 형식·잘못된 `sort`/`stock`·역전된 날짜 범위)는 boot 요청 객체가 전부 `INVALID_REQUEST`(90001)로 거절한다.
- `size`(1~100)·`page`(0 이상)는 boot 검증 + application `SellerProductSearchQuery` `init` 불변식의 이중 방어(00 §3-5).
- 카테고리 필터는 활성 카테고리면 **깊이 무관** 허용(하위 분류 포함 조회) — 구매자 목록의 중분류 제한(80 §7)과 달리
  셀러는 자기 상품이 속한 어느 분류로든 좁힐 수 있다. 없는 카테고리는 `CATEGORY_NOT_FOUND`(40101).
