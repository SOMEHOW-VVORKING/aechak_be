# 셀러 대시보드 집계 — 네 BC를 모으는 읽기 조립 (83-seller-dashboard)

> **성격**
> - 이 문서는 아키텍처 규칙이 아니라 **기능 설계 기록**이다. 셀러센터 첫 화면의 집계
>   (`GET /sellers/me/dashboard/summary`)를 왜 이렇게 지었는지를 남긴다.
> - 관련 코드
>   - `application/dashboard/seller/usecase/SellerDashboardUseCase` — 화면 기준 계약
>   - `application/dashboard/seller/facade/SellerDashboardFacade` — 트랜잭션 경계·자격 게이트·네 BC 조립
>   - `application/dashboard/seller/usecase/query/DashboardPeriod` — 기간 코드 → 현재·직전 구간
>   - `application/order/seller/port/SellerOrderMetricsQueryPort` — 주문 3지표
>   - `application/order/claim/port/SellerClaimQueryPort.countPendingMetrics` — 클레임 처리 대기 6지표
>   - `application/order/claim/port/SellerClaimPendingStage` — 셀러가 할 일이 남은 단계 축(목록 필터와 공유)
>   - `application/settlement/seller/port/SellerSettlementSummaryQueryPort` — 미지급 정산 2금액
>   - `application/product/product/port/SellerProductQueryPort.countStatuses` — 상품 상태 4건수

---

## 1. 개요

- **무엇**: 셀러센터 첫 화면이 한 번에 묻는 네 묶음. 처리 대기 건수 10종, 정산 요약 3금액,
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

스캔은 `boot/seller`에만 **추가로** 넣었다(`application.dashboard`, `application.settlement.seller`). 다만
다른 실행 모듈이 이 빈을 전혀 조립하지 않는다는 뜻은 아니다 — `boot/api`는 루트(`com.aechak`) 스캔이라
대시보드 조립 파사드까지 올리고, `boot/admin`·`boot/batch`는 `application.settlement` 상위를 스캔하므로
셀러 정산 조회 파사드도 함께 올린다. 이 빈들은 도메인 리포지토리 포트만 요구하고 그 어댑터는 네 모듈이
이미 스캔하는 `infra.persistence`에 있어 조립이 깨지지 않고, 조립 스모크 테스트로 확인했다.
`Admin*` 제외 필터처럼 이름 규약으로 가리지 않은 이유는 그 필터가 **조립이 실제로 깨지거나 권한이
섞이는** 빈을 막는 장치이고, 여기 걸리는 것은 호출자 없는 읽기 전용 파사드뿐이기 때문이다.
정산을 상위가 아니라 셀러 하위(`application.settlement.seller`)로 나눈 것은 그래도 의미가 있다 —
셀러센터가 원장 생성·계좌 등록 경로를 끌고 오지 않는다.

---

## 3. 쿼리 수 — 묶음당 하나

지표마다 질의를 따로 돌리면 화면 하나에 집계 쿼리가 14번 붙는다. 묶음마다 **조건부 합계 하나**로 모았다.

| 쿼리 | 내용 |
| --- | --- |
| 1 | 셀러 상태(자격 게이트) |
| 2 | 주문 3지표 — 신규 주문·발송 대기·발송 지연 |
| 3 | 클레임 6지표 — 요청 대기 3종(유형별)·회수 송장 등록 대기·회수 완료 처리 대기·재발송 대기 |
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
| `collectionInvoicePending` | `status = COLLECTING AND collection_started_at IS NULL` |
| `collectionCompletionPending` | `status = COLLECTING AND collection_started_at IS NOT NULL` |
| `reshipPending` | `claim_type = EXCHANGE AND status = APPROVED AND collection_started_at IS NOT NULL AND resolved_at IS NULL` |
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
- **클레임 처리 대기는 요청 상태만 세지 않는다.** 반품·교환은 셀러가 승인하면 회수 중(`COLLECTING`)으로
  넘어가고, 그 뒤로도 회수 송장 등록 → 회수 완료 처리 → (교환이면) 재발송이 셀러 몫으로 남는다.
  요청 상태만 세던 집계는 승인한 순간 그 건이 처리 대기에서 사라져, 셀러가 할 일이 남았는데 숫자가 0이 됐다.
  병합 전에는 반품·교환의 요청 가능 주문 상태가 빈 집합이어서 드러나지 않던 전제다.
- **단계를 합치지 않고 나눠 센다.** 회수 중 한 상태에 송장 등록과 완료 처리라는 서로 다른 할 일이 들어 있어,
  합쳐 세면 셀러가 숫자를 보고도 무엇을 해야 할지 모른다. 상태만으로 갈리지 않는 두 자리는 이렇게 가른다 —
  회수 중은 **회수 시작 시각**(`collection_started_at`, 회수 송장 등록과 같은 트랜잭션에서 찍힌다)으로,
  승인됨은 **유형과 종결 시각**으로(반품의 환불 대기는 셀러가 할 일이 없고, 교환의 재발송 대기만 남는다).
  `reshipPending`의 조건은 도메인의 재발송 가능 판정(`Claim.isReshippable`)과 같다 — 대기로 센 건은
  셀러가 실제로 재발송할 수 있는 건이어야 한다.
- 처리 대기 판정은 **클레임 목록의 `pendingStage` 필터와 같은 식 하나**에서 나온다
  (`SellerClaimQueryAdapter.pendingStage`). 대시보드 카드에서 목록으로 넘어온 셀러가 같은 라벨에 다른 숫자를
  보지 않으려면 판정이 한 곳이어야 한다. 목록의 유형 탭 배지(`countByClaimType`)도 이 축을 반영하므로,
  카드 숫자의 반품·교환 내역이 목록에서 그대로 쪼개진다. 상태 축만으로는 회수 중 한 상태에 들어 있는
  두 단계를 가를 수 없어, 목록에 단계 축을 따로 열었다.
- 집계 쿼리는 WHERE에서 `status IN (CLAIM_REQUESTED, COLLECTING, APPROVED)`로 읽을 행을 먼저 좁힌다. 네 단계의
  상태 축 합집합이라 결과는 바뀌지 않는다. 상태 조건을 조건부 합계(CASE) 안에만 두면 거부·철회·개입·환불 실패처럼
  셀러가 할 일이 없는데도 끝없이 쌓이는 이력까지 첫 화면 질의가 전수로 읽는다. 합집합은 단계 enum
  (`SellerClaimPendingStage.statuses`)에서 뽑으므로 단계를 더하면 좁히는 조건도 함께 넓어진다.
  남은 비용은 `APPROVED`다 — 끝난 취소·반품이 모두 이 상태로 남으므로 셀러당 이력이 커지면 여전히 많이 읽는다.
  `(seller_id, status, id)` 인덱스에는 유형·회수 시각·종결 시각이 없어 커버링도 아니다. 실측 실행계획을 보고
  인덱스를 더할지 판단한다.
- 승인 이후 지표도 기간 축은 **요청일**이다 — 승인·회수 시각이 아니라 그 클레임이 생긴 날로 자른다.
  요청일이 기간 밖이면 회수가 지금 진행 중이어도 그 기간의 숫자에는 들어가지 않는다.
  따라서 `previousValue`는 "직전 기간 끝에 남아 있던 대기 잔량"이 아니라 **직전 기간에 요청됐고 지금도 그 단계인 건수**다.
  열흘 전 요청을 오늘 승인하면 `7d`의 직전 값이 올라간다. 화면이 두 값의 차이를 "지난주보다 밀린 일"로 읽으면 뜻이 어긋난다.
- [한계] 단계 판정은 카드와 목록이 같지만 **기간 축의 단위는 다르다.** 대시보드는 `now`를 끝으로 하는 시각 단위
  반개구간(`7d` = `[now−7일, now)`)이고, 목록 필터(`requestedFrom`/`requestedTo`)는 날짜만 받아 시작일 00:00부터
  종료일 다음 날 00:00까지다. 그래서 기간을 건 카드의 건수를 목록 필터로 정확히 재현할 수는 없다 — 목록이 양쪽
  경계에서 더 넓게 잡는다. 카드에서 목록으로 넘길 때 단계 축만 넘기고 기간은 넘기지 않으면 어긋나지 않는다.
  정확히 맞춰야 한다면 목록에도 시각 단위 구간을 열어야 한다.
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
- 기준 시각은 요청당 **한 번만** 뜬다. 구간 환산과 발송 기한 계산이 다른 시계를 쓰면 기한 경계에 걸린
  주문이 현재 기간과 직전 기간에서 다르게 판정된다 — 그래서 조립 파사드가 뜬 시각이 주문 집계 명세에
  실려 내려간다.
- `previousValue`는 **과거 시점의 스냅샷이 아니다.** 지표는 지금의 주문 상태를 지금의 발송 기한 기준으로
  세고 기간만 과거로 옮긴다 — 집계 이력 테이블이 없어 그때의 상태를 복원할 수 없다. 그래서 오래된
  구간에서는 `shippingDelayed`가 `awaitingShipment`에 수렴한다(그만큼 오래 묵은 미발송 건이다).
