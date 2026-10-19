# 셀러 대시보드 집계 — 네 BC를 모으는 읽기 조립 (83-seller-dashboard)

> **성격**
> - 이 문서는 아키텍처 규칙이 아니라 **기능 설계 기록**이다. 셀러센터 첫 화면의 집계
>   (`GET /sellers/me/dashboard/summary`)를 왜 이렇게 지었는지를 남긴다.
> - 관련 코드
>   - `application/dashboard/seller/usecase/SellerDashboardUseCase` — 화면 기준 계약
>   - `application/dashboard/seller/facade/SellerDashboardFacade` — 트랜잭션 경계·자격 게이트·네 BC 조립
>   - `application/dashboard/seller/usecase/query/DashboardPeriod` — 기간 코드 → 현재·직전 구간
>   - `application/order/seller/port/SellerOrderMetricsQueryPort` — 주문 3지표
>   - `application/order/claim/port/SellerClaimQueryPort.countByClaimType` — 클레임 3지표(목록 배지와 공유)
>   - `application/settlement/seller/port/SellerSettlementSummaryQueryPort` — 미지급 정산 2금액
>   - `application/product/product/port/SellerProductQueryPort.countStatuses` — 상품 상태 4건수

---

## 1. 개요

- **무엇**: 셀러센터 첫 화면이 한 번에 묻는 네 묶음. 처리 대기 건수 7종, 정산 요약 3금액,
  상품 상태 요약 4종, 그리고 기간 필터(`all`/`7d`/`30d`/`90d`).
- **흐름**: `GET /sellers/me/dashboard/summary` → `SellerDashboardController` → `SellerDashboardUseCase`
  (구현 `SellerDashboardFacade`) → 주문·클레임·정산·상품 네 BC의 진입점.
- **랜딩 전제**: 셀러 로그인 직후 떨어지는 화면이다. 기간 파라미터 없이 들어오고(전체 기간),
  데이터가 하나도 없는 새 셀러도 전부 0으로 200을 받아야 한다.

---

## 2. 조립 지점을 BC 밖에 둔 이유

집계는 order·claim·settlement·product 네 BC에 걸쳐 있어 **어느 BC도 주인이 아니다.** 그래서 조립만 하는
`application/dashboard/seller`를 따로 두고, 그 패키지는 애그리거트도 리포지토리도 갖지 않고 다른 BC의
UseCase만 주입받는다.

검토하고 버린 자리 둘.

| 자리 | 버린 이유 |
| --- | --- |
| `application/seller`(입점 신청 BC) | 어드민이 같은 패키지를 스캔한다 — 셀러센터 진입점 네 개를 조립해야 해 어드민이 부팅에서 깨진다 |
| 컨트롤러 | 기간 코드를 현재·직전 구간으로 환산하는 판정이 표현 계층에 남는다. 컨트롤러는 검증·변환만 한다 |

`boot/seller`만 이 패키지를 스캔하므로 api·admin·batch의 조립은 바뀌지 않는다. 정산도 셀러가 조회하는
하위(`application.settlement.seller`)만 스캔한다 — 원장 생성·계좌 등록은 구매확정과 심사 승인 경로의 것이다.

---

## 3. 쿼리 수 — 묶음당 하나

지표마다 질의를 따로 돌리면 화면 하나에 집계 쿼리가 14번 붙는다. 묶음마다 **조건부 합계 하나**로 모았다.

| 쿼리 | 내용 |
| --- | --- |
| 1 | 셀러 상태(자격 게이트) |
| 2 | 주문 3지표 — 신규 주문·발송 대기·발송 지연 |
| 3 | 클레임 3지표 — 취소·반품·교환 요청(유형별 group by) |
| 4 | 정산 2금액 — 대기·보류 |
| 5 | 상품 4건수 — 검수 대기·반려·판매 중지·품절 |
| 6, 7 | 직전 기간의 주문·클레임 재질의 (`period=all`이면 돌지 않는다) |

- 집계 스냅샷 테이블이 없어 `previousValue`는 같은 쿼리를 한 번 더 돌려 구한다.
- 통합 테스트가 Hibernate 통계로 이 상한을 고정한다 — 데이터를 1배치에서 5배치로 늘려도 statement 수가
  같은지, 기간 필터를 걸어도 한 자릿수인지 본다. 쿼리 수만 세면 조립이 터져 응답이 비어도 통과하므로
  같은 자리에서 값까지 단언한다.
- **더 줄여야 하면 캐시할 자리**: 정산 요약과 상품 상태 요약이다. 둘은 기간과 무관한 현재 스냅샷이라
  셀러별 짧은 TTL 캐시가 바로 들어맞고, 구매확정·검수 결정·재고 변경 때 무효화하면 된다. 주문·클레임
  지표는 셀러가 발주 확인을 누른 직후의 숫자를 보려는 화면이라 캐시하면 "처리했는데 안 줄었다"가 된다.

---

## 4. 자격 게이트를 조립 파사드에서 한 번만 본다

| 셀러 상태 | 대시보드 조회 | 근거 |
| --- | --- | --- |
| ACTIVE / PAUSED / WITHDRAWAL_REQUESTED | 허용 | 휴점·탈퇴 신청 중에도 이미 받은 주문·정산은 처리 대상 |
| SUSPENDED / WITHDRAWN / 셀러 아님 | 403 (10003) | 제재·자격 소멸 상태는 셀러센터 이용 차단 |

주문 목록·상품 목록의 조회 게이트와 같은 기준이다. 집계 계약 네 개(`getOrderMetrics`,
`getClaimMetrics`, `getSummary`, `getMyProductStatusCounts`)는 **자격 게이트를 두지 않고** 호출자가
통과시킨다는 전제를 KDoc에 명시했다.

- 넘겨받은 `sellerId` 범위만 읽고 식별자를 돌려주지 않아 소유권(BOLA) 판정 대상이 아니다.
  `sellerId`는 인증 주체의 userId 그 자체여서 남의 자원을 가리킬 길이 없다.
- 같은 판정을 BC마다 되풀이하면 화면 한 번에 셀러 상태 조회가 네 번 더 붙고, 거절 코드도 어느 BC가
  먼저 돌았는지에 따라 갈린다.
- 대가: 이 네 계약은 HTTP로 직접 노출하면 안 된다. 노출하려면 그 EP가 자기 게이트를 가져야 한다.

---

## 5. 지표별 판정

| 지표 | 판정 |
| --- | --- |
| `newOrders` | `orders.status = PAID` |
| `awaitingShipment` | `orders.status IN (PREPARING, DISPATCH_PENDING)` |
| `shippingDelayed` | 위 상태이고 **주문일 < now − 발송 기한** |
| `cancelRequests` | `claims.claim_type = CANCEL AND status = CLAIM_REQUESTED` |
| `returnRequests` / `exchangeRequests` | 위와 같고 유형만 다름 |
| `unansweredInquiries` | **계산 불가** — `supported: false` |

- 기간 축은 주문 지표가 **주문일**(`order_groups.created_at`), 클레임 지표가 **요청일**
  (`claims.requested_at`)이다. 각 지표가 "언제 생긴 일인지"를 세는 축이 다르다.
- `shippingDelayed`의 기준점을 `orders.updated_at`이 아니라 주문일로 잡았다. `updated_at`은 구매확정
  기한 부여처럼 뒤따르는 쓰기에도 갱신돼 **지연 건이 조용히 지연에서 빠진다.** 셀러가 약속한 발송
  기한의 기준도 결제 시각이다. 기한 일수는 `order.dispatch.sla-days`(기본 3일, 역일)다 —
  셀러별 평균 발송 소요일 컬럼이 들어오면 그 값이 전역 기본값을 덮는다.
- `shippingDelayed ⊆ awaitingShipment`다. 화면이 두 카드를 따로 세우므로 겹쳐도 된다.
- `PENDING_PAYMENT`는 어느 지표에도 들어가지 않는다 — 결제 전 주문도 테이블에 남는 모델이라
  넣으면 셀러에게 미결제 건이 처리 대기로 보인다. 셀러 주문 목록과 같은 규약.
- 클레임 지표는 목록 탭 배지가 쓰는 집계 쿼리(`countByClaimType`)를 그대로 재사용한다. 두 화면이 같은
  라벨에 다른 숫자를 띄우지 않으려면 판정 규칙이 한 곳이어야 한다.
- `unansweredInquiries`는 셀러에게 귀속되는 문의 모델이 아직 없다(`admin_inquiries`는 어드민용).
  0을 그냥 내리면 "문의가 없다"와 "집계할 수 없다"가 구분되지 않아 `supported: false`로 내리고
  화면이 카드를 흐리게 표시한다.

---

## 6. 정산 요약과 상품 상태 — 기간 무관 스냅샷

- `pendingAmount` = `SUM(net_amount)` of `PENDING`, `onHoldAmount` = 같은 합의 `ON_HOLD`.
  `totalAmount` = **그 둘의 합**이다. 화면이 "정산 예정 금액" 아래에 대기·보류를 쪼개 보여 주므로
  합이 맞아야 하고, `PAID`·`CANCELLED`는 앞으로 받을 돈이 아니다.
- 상품 상태 4건수는 서로 배타적이지 않다 — 판매 중지된 상품의 재고가 0이면 중지와 품절에 함께 센다.
  화면이 네 pill을 나란히 세우는 요약이라 합이 상품 수와 같을 필요가 없다.
- **품절 판정은 상품 목록의 재고 필터와 서브쿼리를 공유한다**(활성 조합 중 재고가 남은 것이 없음).
  `products.sale_status = OUT_OF_STOCK`은 이벤트로 뒤늦게 동기화되는 파생 캐시라 재입고 직후 어긋나고,
  두 화면이 같은 라벨에 다른 숫자를 띄우면 그 자체가 결함 신고가 된다.
- 둘 다 기간 필터를 받지 않는다. 프론트 목업도 같은 전제다(정산·상품 상태는 기간별 값이 하나뿐).

---

## 7. 증감은 서버가 만들지 않는다

- 응답은 지표마다 `value`와 `previousValue`만 싣는다. 증감 방향·퍼센트는 화면이 계산한다 —
  서버가 비율을 만들면 0으로 나누기 규칙과 표기 자릿수가 서버 계약에 박혀, 화면 문구가 바뀔 때마다
  서버가 바뀐다.
- `period=all`이면 비교할 직전 구간이 없어 `previousValue = null`이다. 0으로 내리면 "직전에 0건"과
  "비교 대상 없음"이 구분되지 않는다.
- 직전 구간은 현재 구간과 **같은 길이로 맞붙는** 반개구간이다(`7d`면 `[now−14d, now−7d)`).
  경계가 어긋나면 그 경계의 주문이 두 번 세어지거나 빠진다.
