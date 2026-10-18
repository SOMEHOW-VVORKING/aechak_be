# 셀러 주문 목록·상세 조회 — 상태 세그먼트와 묶음 조립 (82-seller-order-query)

> **성격**
> - 이 문서는 아키텍처 규칙이 아니라 **기능 설계 기록**이다. 셀러센터의 주문 목록(`GET /sellers/me/orders`)과
>   주문 상세(`GET /sellers/me/orders/{orderPublicId}`)를 왜 이렇게 지었는지를 남긴다.
> - 관련 코드
>   - `application/order/seller/usecase/SellerOrderUseCase` — 셀러 소비자 기준 계약
>   - `application/order/seller/facade/SellerOrderFacade` — 트랜잭션 경계·조회 자격 게이트·묶음 조립
>   - `application/order/seller/port/SellerOrderQueryPort` — 목록·상세·품목·배송·클레임 조회 포트
>   - `application/order/seller/port/SellerOrderStatusFilter` — 화면 세그먼트 → 주문 상태 집합
>   - `infra/persistence/jpa/.../order/SellerOrderQueryAdapter` — QueryDSL 구현

---

## 1. 개요

- **무엇**: 셀러 자신의 주문. 상태 세그먼트(단일 선택)·키워드·주문일 범위 필터와 오프셋 페이지네이션.
  목록 행과 상세는 **주문 단위 필드가 같고** 상세에만 품목이 붙는다.
- **흐름**: `GET /sellers/me/orders` → `SellerOrderController` → `SellerOrderUseCase`(구현 `SellerOrderFacade`) → `SellerOrderService`.
- **단위**: 구매자 주문 목록이 결제 단위(주문그룹)인 것과 달리 **셀러 주문(`orders`) 행 단위**다. 금액도 그 셀러 몫만 싣는다 —
  `totalProductAmount`는 이 주문 품목의 `단가 스냅샷 × 수량` 합이고, `payableAmount = totalProductAmount + sellerShippingFee − allocatedCouponDiscount`.
  주문그룹 합계(적립금·최종 결제금액)는 다른 셀러의 몫이 섞이므로 내리지 않는다.

---

## 2. 상태 세그먼트 — 화면 어휘를 서버가 받는다

| 세그먼트 | 주문 상태 |
| --- | --- |
| `all` | 결제대기를 뺀 전부 |
| `new` | `PAID` |
| `awaiting-dispatch` | `PREPARING`, `DISPATCH_PENDING` |
| `shipping` | `SHIPPING` |
| `delivered` | `DELIVERED`, `PURCHASE_CONFIRMED` |
| `cancelled` | `CANCELLED` |

- 상태 enum 이름을 그대로 받지 않는 이유: 셀러 화면의 탭 하나가 상태 **여러 개**를 묶는다. 묶음 판정을 FE에 두면
  상태가 늘 때마다 FE가 바뀌고, 페이지네이션 경계도 클라이언트에서 다시 계산해야 한다.
- 구매자 목록의 `OrderStatusFilter`를 확장하지 않고 `SellerOrderStatusFilter`를 따로 만들었다 —
  세그먼트 구성이 다르고, 한쪽을 늘리면 다른 쪽 목록이 함께 바뀐다.
- `PENDING_PAYMENT`는 **어느 세그먼트에도 없다.** 결제 전 주문도 테이블에 남는 모델이라(만료 배치가 지운다)
  넣으면 셀러에게 결제되지 않은 건이 주문으로 보인다. 상세도 같은 이유로 결제대기 건을 404로 가린다.

---

## 3. 셀러 스코프와 조회 자격 게이트

| 셀러 상태 | 주문 조회 | 근거 |
| --- | --- | --- |
| ACTIVE / PAUSED / WITHDRAWAL_REQUESTED | 허용 | 휴점·탈퇴 신청 중에도 이미 받은 주문은 처리 대상 |
| SUSPENDED / WITHDRAWN / 셀러 아님 | 403 (50003) | 제재·자격 소멸 상태는 셀러센터 이용 차단 |

- 쓰기(발주 확인)는 ACTIVE만 허용하는데 조회는 더 넓다 — 셀러 상품 목록이 등록(ACTIVE)과 조회(휴점 포함)를 나눈 것과 같은 결이다.
- 소유권은 게이트가 아니라 쿼리 조건이다 — `orders.seller_id`로 잠그고, 타 셀러 주문은 **403이 아니라 404**로 가린다
  (존재 여부 비노출). 발주 확인(`POST .../accept`)의 404와 같은 규약.

---

## 4. 왜 커서가 아니라 오프셋인가

- 셀러센터는 테이블 UI라 임의 페이지 점프가 필요하고, 조회 대상이 셀러 한 명의 주문으로 잠겨 규모가 작다.
  셀러 상품 목록과 같은 판단이며 응답 래퍼(`OffsetPageResult` → `totalCount`/`page`/`size`/`totalPages`/`hasNext`)도 공유한다.
- 정렬은 최신순 고정이다(`orders.id DESC`). 화면에 정렬 컨트롤이 없고, 주문 번호는 ULID라 id 역순이 곧 주문 시각 역순이다.

---

## 5. 조립 — 쿼리 수를 주문 수와 무관하게 ★

한 번의 목록 요청은 **주문 수와 무관하게** 고정 개수의 쿼리로 끝난다.

1. 페이지 조회 1회 — 품목 수와 상품금액은 **상관 서브쿼리로 쿼리에서 집계**한다(행마다 품목을 다시 읽지 않는다).
2. 카운트 1회.
3. 배송 1회, 클레임 1회, 구매자 닉네임 1회 — 페이지의 주문 id·구매자 id를 묶어 `IN`으로 받고 Facade가 메모리에서 묶는다.

- 배송·클레임은 주문당 여러 행일 수 있어(교환 재발송, 취소 후 반품) **최신 한 건**을 고른다.
- 구매자 닉네임은 user BC의 `UserUseCase.getAuthors`를 쓴다 — 주문 쿼리가 `users`/`user_profiles`를 직접 조인하면
  BC 경계를 넘고, 탈퇴 표기 규칙(탈퇴한 사용자 치환)이 두 곳으로 갈린다.
- 이 계약은 통합 테스트가 Hibernate 통계로 고정한다 — 구매자를 주문마다 다르게 두고 배송·클레임까지 붙인 뒤
  주문 1건과 5건의 statement 수가 같은지 본다. 쿼리 수만 세면 조립이 터져 응답이 비어도 통과하므로
  응답 성공과 행 수도 같은 자리에서 단언한다.

---

## 6. 진행 중인 클레임의 범위

- `activeClaim`은 **종결되지 않은** 클레임이다. 판정은 두 겹이다 — `claims.resolved_at`이 비어 있고,
  상태가 `REJECTED`·`WITHDRAWN`이 아닐 때만 진행 중으로 본다.
  종결 시각이 1차 기준인 이유: 승인(`APPROVED`)은 환불·정산이 남았는지(처리 중)와 다 끝났는지(종결)를 구분하지 못한다.
  상태 제외를 함께 두는 이유: 거부·철회는 종결 시각을 찍지 않고 끝나도 더 진행될 수 없는 상태다.
  승인·수거·환불실패·어드민 개입은 종결 시각이 찍히기 전까지 셀러가 계속 봐야 한다.
- 상태를 화면 어휘(대기/승인/반품중…)로 접지 않고 BE 상태를 그대로 내린다. 클레임은 상태가 네 축으로 분산돼 있어
  서버가 접으면 화면이 바뀔 때마다 서버가 바뀐다 — 접는 책임은 FE 한 곳이다.

---

## 7. PII — 서버는 복호만, 마스킹은 화면

- `order_groups.receiver_name_enc`·`contact_number_enc`는 AES 암호문이다. 서비스가 `PiiStringCodec`으로 복호해 **평문**으로 내린다
  (`recipientName`, `contactNumber`). 마스킹은 심사·회원 조회와 통일해 화면이 한다 — 서버가 마스킹하면 송장 출력·CS 대응처럼
  원문이 필요한 화면이 같은 EP를 다시 요구한다.
- 그래서 **키워드 검색에 수령인명을 넣을 수 없다** — 암호문 컬럼은 LIKE가 불가능하고, 검색 가능한 해시 컬럼(`phone_last4_hmac` 류)이
  `order_groups`에 없다. 검색 대상은 주문번호·상품명 스냅샷·옵션명 스냅샷뿐이고, FE 플레이스홀더도 그에 맞춰야 한다.

---

## 8. 필터 값 표기와 검증

- `status`는 세그먼트 소문자 어휘(`awaiting-dispatch` 등)다 — 응답에 나가는 값이 아니라 표현용 어휘라서
  구매자 `sort=latest` 선례를 따른다. 응답의 `status`는 주문 상태 enum 이름 그대로다.
- 주문일 필터는 `yyyy-MM-dd`로 받고 `[from 00:00, to+1일 00:00)` 반개구간으로 환산한다 — 상한 날짜가 통째로 포함된다.
- 형식 오류(모르는 세그먼트·날짜 형식·역전된 범위)는 boot 요청 객체가 `INVALID_REQUEST`(90001)로 거절하고,
  `page`(0 이상)·`size`(1~100)는 boot 검증 + `SellerOrderSearchQuery` `init` 불변식의 이중 방어다.
