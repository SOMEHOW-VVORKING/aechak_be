# application 모듈 (20-application)

> 성격: 유스케이스 조율 계층. 트랜잭션 경계, 도메인 간 조합, 입출력 계약(Command/Result/View)의 소유자.
> 허용 의존: common, domain, spring-context, spring-tx.
> A-1 결정(L2): 리포지토리는 domain의 포트를 주입받는다 — 이 모듈에 Spring Data 없음 (§5).
> 금지: web-common, spring-web, infra/*. — HTTP도 기술 구현도 모른다.

---

## 1. 패키지 구조

```
application/src/main/kotlin/com/aechak/application/
└── order/
    ├── usecase/                 # 계약 — 외부는 이 패키지 아래만 import한다
    │   ├── OrderUseCase.kt      # 인터페이스 — 이 도메인의 유일한 진입점
    │   ├── command/             # 쓰기 입력 (계약의 일부)
    │   ├── query/               # 복잡 조회 입력
    │   └── result/              # 출력 (Result — 유스케이스 최종 출력 DTO)
    ├── port/                    # (복잡 조회를 위임할 때만) 조회 포트 계약
    │   ├── {대상}QueryPort.kt   # 읽기 전용 포트 인터페이스 — 구현은 infra/persistence
    │   └── view/                # 포트가 돌려주는 읽기 모델 (View, §4-1)
    ├── facade/                  # UseCase 구현 — 외부에서 import 금지
    │   └── OrderFacade.kt       # UseCase의 유일한 구현체. @Transactional 경계
    ├── service/                 # 구현 — 외부에서 import 금지
    │   └── OrderService.kt      # 비즈니스 로직 보관함 (인터페이스 없음, 필요 시 세분화)
    ├── listener/                # "다른 도메인" 이벤트를 수신하는 리스너
    └── error/                   # 에러코드 enum — 애그리거트가 없는 컨텍스트(auth·file)만 갖는다.
                                 #   도메인 BC의 enum은 domain 소유 (05 §0-4)
```

## 2. UseCase / Facade / Service 역할 계약

```kotlin
package com.aechak.application.order.usecase

/**
 * order 도메인의 진입점 계약.
 *
 * [규칙 — 진입점]
 * - 도메인당 인터페이스 1개. 메서드가 10개를 넘어 비대해지면 그때 기능별 분리를 논의한다.
 * - 이 인터페이스만이 외부(Controller / Consumer / Batch / 타 도메인 Facade)에 노출된다.
 * - 타 도메인이 order를 부를 때도 반드시 이 인터페이스로만 — OrderService 직접 호출 금지.
 *
 * [규칙 — 시그니처 어휘] (상세: §4)
 * - 입력: Command(쓰기) / SearchQuery(복잡 조회) / 스칼라 인자(단순 조회)
 * - 출력: Result 계열. 도메인 엔티티 반환 금지.
 * - boot의 Request/Response 타입은 이 모듈이 모르므로 등장할 수 없다(컴파일로 강제됨).
 */
interface OrderUseCase {
    fun placeOrder(command: PlaceOrderCommand): OrderResult
    fun cancelOrder(command: CancelOrderCommand)
    fun getOrder(orderId: Long): OrderResult                      // 단순 조회 = 스칼라 인자
    fun searchOrders(query: OrderSearchQuery): List<OrderSummaryResult>
}
```

```kotlin
/**
 * OrderUseCase의 유일한 구현체.
 *
 * [규칙]
 * - UseCase 구현은 항상 Facade다. Service가 UseCase를 직접 구현하는 것 금지 (규칙은 하나만).
 * - 트랜잭션 경계의 소유자는 Facade (상세 §2-1). Service/도메인 메서드에 트랜잭션 어노테이션 금지.
 * - 도메인이 수집한 이벤트(aggregate.events)를 커밋 전 publisher로 발행하고 clearEvents().
 * - 타 도메인 협력이 필요하면 그쪽 UseCase를 주입받는다. 순환 의존 발생 = 이벤트 전환 신호.
 */
@Service
class OrderFacade(
    private val orderService: OrderService,
    private val eventPublisher: ApplicationEventPublisher,
    // private val productUseCase: ProductUseCase,   // 타 도메인 협력 예시
) : OrderUseCase {

    @Transactional
    override fun placeOrder(command: PlaceOrderCommand): OrderResult {
        // TODO: 외부 지식 필요한 검증(재고/회원 상태 등) → 도메인 팩토리 호출 → 저장
        //       → aggregate.events 발행 → clearEvents → Result 변환
        TODO()
    }
    // ...
}
```

```kotlin
/**
 * 비즈니스 로직 메서드 보관함.
 * [규칙] 인터페이스 없음. Facade에서만 호출된다 — Controller/Consumer/타 도메인이 직접 호출 금지.
 */
@Service
class OrderService( /* repository 등 */ ) { /* TODO */ }
```

### 2-1. 트랜잭션 경계 — "Facade 소유"의 정확한 의미

- 규칙의 본질은 **경계를 여는 코드가 Facade에만 있다**는 소유권이다. 메서드당 `@Transactional` 1개 강제가 아니다 —
  선언형(`@Transactional`)이든 프로그램형(`TransactionTemplate`)이든 Facade 안이면 규칙 준수.
- 쓰기 유스케이스 Facade 메서드에는 트랜잭션 **필수**(누락도 리뷰 지적 대상). 조회는 `@Transactional(readOnly = true)`.
- **외부 네트워크 호출(infra client — PG 등)을 트랜잭션 범위 안에 두지 않는다.** 외부 I/O 동안 DB 커넥션·락을
  점유해 풀 고갈로 이어진다. 흐름이 [DB 쓰기 → 외부 호출 → DB 쓰기]면 Facade가 경계를 쪼갠다:

```kotlin
// Facade — 메서드 자체는 트랜잭션 없음. 경계 2개를 Facade가 소유
override fun confirmPayment(command: ConfirmPaymentCommand): PaymentResult {
    val order = tx.execute { orderService.markPending(...) }   // tx1: 커밋 후 커넥션 반환
    val approval = pgPort.approve(...)                          // 트랜잭션 밖에서 외부 호출
    return tx.execute { orderService.applyApproval(approval) }  // tx2: 결과 반영
}
```

- 후속 작업이 비동기여도 되면(알림 등) 트랜잭션 분리 대신 §3의 AFTER_COMMIT 리스너를 쓴다.
- 이 규칙의 대상 아님: `@TransactionalEventListener`(리스너 위치가 정상), Spring Batch Step/chunk 트랜잭션(framework 관리).

## 3. 이벤트 리스너 — 수신자 위치

```kotlin
package com.aechak.application.notification.listener

/**
 * 타 도메인 이벤트 수신 리스너. "수신하는 도메인" 패키지에 산다.
 * 발행자의 이벤트 클래스(domain/order/event/*)를 import하는 것은 정상 방향.
 * AFTER_COMMIT + 별도 트랜잭션 필요 시 REQUIRES_NEW — 기존 전파 학습 내용 적용.
 */
@Component
class OrderEventListener( /* notificationUseCase 등 */ ) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: OrderCancelledEvent) { TODO() }
}
```

## 4. Command / Query / Result 규약 ★

> 이 규약이 존재하는 근본 이유: boot → application 단방향이라 UseCase 시그니처에
> http dto가 들어갈 수 없다. 따라서 **application이 자기 입출력 어휘를 소유**한다.
> 부수 이점: 다중 진입점(api/admin/batch/consumer)이 같은 계약 공유, API 스펙 변경으로부터 격리,
> 엔티티 노출 차단, 시그니처 자체가 유스케이스 문서, MockMvc 없는 Facade 단위 테스트.

| 구분 | 규칙 | 네이밍 |
| --- | --- | --- |
| 쓰기 입력 | **항상 Command — 인자 1개여도.** 쓰기는 필드가 자라므로 시그니처 변경 대신 필드 추가로 흡수 | `{동사}{대상}Command` |
| 조회 입력 | 스칼라 1~2개 → 그냥 함수 인자. 조건 3개 이상 / 페이징 / 옵셔널 필터 → Query 객체 | `{대상}SearchQuery` |
| 출력(유스케이스) | **유스케이스 반환은 항상 Result. 엔티티 반환 금지.** 존재 여부 등은 Boolean 허용 | `{대상}Result` / `{대상}SummaryResult` |
| 출력(조회 포트) | 복잡 조회를 조회 포트에 위임할 때 **포트가 돌려주는 읽기 모델은 View** (§4-1). Result와 혼동 금지 | `{대상}View` |
| 매핑 | boot 소유 (Request.toCommand / Response.from). application에는 매핑 코드 없음 | — |
| 검증 | Command에는 검증 어노테이션 없음. **"Command가 생성됐다 = 형식 검증(@Valid)은 boot에서 통과했다"** | — |

```kotlin
package com.aechak.application.order.usecase.command

/**
 * [Command 규칙]
 * - 쓰기 유스케이스 입력은 인자 개수와 무관하게 항상 Command 객체.
 *   (요구사항 추가 시 시그니처가 아니라 필드가 늘어난다 → 호출부 전파 최소화)
 * - 필드는 "이 유스케이스 수행에 필요한 전부이자 최소" — request의 안 쓰는 필드를 실어 나르지 않는다.
 * - 형식 검증 어노테이션 금지. 형식은 boot(@Valid)에서 끝났다는 것이 이 객체의 계약이다.
 * - Command 필드 → 엔티티 생성 매핑은 Command의 toEntity가 소유한다 — 서비스에서 팩토리 인자를
 *   직접 나열하지 않는다. toEntity는 "필드 옮겨 담기"만 하고(생성 규칙·불변식은 도메인 팩토리 소유),
 *   서비스가 계산·조회한 값(엔티티 참조, 인코딩·정규화 결과)은 파라미터로 받는다.
 *   Result.from(entity)의 쓰기 방향 대칭이다. 선례: AddDeliveryAddressCommand.toEntity(user, encodedContact).
 */
data class PlaceOrderCommand(
    val buyerId: Long,
    val lines: List<OrderLineCommand>,
    // TODO
) {
    fun toEntity(buyer: User): Order = Order.place(buyer = buyer, lines = TODO())
}
```

```kotlin
package com.aechak.application.order.usecase.query

/**
 * [Query 객체 규칙]
 * - 단순 조회(식별자 등 스칼라 1~2개)는 이 객체를 만들지 않고 함수 인자로 받는다. 과설계 금지.
 * - 조건 3개 이상 / 페이징 / 옵셔널 필터 조합이 등장하는 순간 Query 객체로 승격한다.
 */
data class OrderSearchQuery(
    val buyerId: Long,
    val status: OrderStatus? = null,      // 옵셔널 필터
    val page: Int = 0,
    val size: Int = 20,
)
```

```kotlin
package com.aechak.application.order.usecase.result

/**
 * [Result 규칙]
 * - 모든 UseCase 반환은 Result 계열. 도메인 엔티티를 밖으로 내보내지 않는다
 *   (lazy 직렬화 사고 / 내부 필드 유출 / JPA 모델 = API 계약 참사 차단).
 * - 목록/요약용은 SummaryResult로 분리해 상세용과 필드 규모를 다르게 가져간다.
 * - 엔티티 → Result 변환은 companion의 from(entity)로 이 파일에 둔다 (application 내부 변환은 허용).
 */
data class OrderResult(
    val orderId: Long,
    val status: String,
    // TODO
) {
    companion object {
        fun from(order: Order): OrderResult = TODO()
    }
}
```

### 4-1. 읽기 모델 (`port/view`) vs 결과 DTO (`usecase/result`)

> 복잡 조회는 유스케이스가 엔티티를 직접 다루지 않고 조회 포트(`{도메인}/port/`)에 위임한다.
> 이때 등장하는 두 타입은 계층도 역할도 다르므로 이름으로 구분한다.

| 타입 | 사는 곳 | 역할 | 채우는 주체 |
| --- | --- | --- | --- |
| `~View` | `{도메인}/port/view/` | 조회 포트가 돌려주는 **읽기 모델(projection)**. DB 조회 형태에 가깝다 | infra/persistence (SQL 결과) |
| `~Result` | `{도메인}/usecase/result/` | 유스케이스가 `View`를 가공해 최종적으로 내보내는 **출력 DTO** | 유스케이스 |

```kotlin
package com.aechak.application.product.port.view

/**
 * [읽기 모델(View) 규칙]
 * - 조회 포트(port/*QueryPort)의 반환 타입. infra/persistence가 SQL 결과로 채워 반환한다.
 * - Facade는 이 View를 재료로 Result를 조립한다. View → Result 변환은 Result의 companion from(view)에
 *   둔다 (§4 Result 규칙 "엔티티 → Result 변환은 companion from"과 같은 방식 — 재료가 엔티티 대신 View일 뿐).
 * - 이름은 접미사 View로 통일 — 패키지 view와 접미사가 1:1로 맞아 usecase/result와 폴더로 구분된다.
 */
data class ProductCatalogView(
    val id: Long,
    val sortPriceAtAnchor: Long,   // SQL이 조회 기준 시각으로 계산한 정렬·커서 경계용 값
    // ...
)
```

> 둘 다 예전엔 `result`였으나 패키지 이름을 클래스 접미사(`View`)에 맞춰 `port/view`로 분리했다.
> 폴더 이름만으로 읽기 모델과 결과 DTO가 갈린다.

## 5. 리포지토리 위치 — A-1 결정(L2)

- 이 모듈에는 리포지토리 없음. domain의 포트(`domain/{도메인}/repository/`)를 주입받는다 — 구현은 infra/persistence (40 §1).

## 6. 인가 배치 — 자격 게이트 vs 소유권 ★

> 10-domain §2-2의 "조회 API마다 소유권 검증(BOLA/IDOR 방지) 필수"의 이행 규칙.
> 원문이 조회를 들지만 이 절의 적용 범위는 조회에 그치지 않는다 — 수정·삭제처럼 리소스를 식별해
> 다루는 경로 전부가 대상이다(아래 예제도 수정·삭제 경로에서 쓰인다).
> 인가를 두 겹으로 나누되, 겹마다 사는 계층이 다르다.

| 겹 | 질문 | 판정 재료 | 위치 |
| --- | --- | --- | --- |
| 자격 게이트 | "이 자격이 있는 유저인가?" (활성 셀러·ADMIN 등) | userId만 (리소스 불필요) | **컨트롤러 @PreAuthorize** |
| 소유권 | "이게 그 유저의 리소스인가?" (내 배송지인가) | 리소스 로드 필요 | **서비스 계층** |

- **자격**: `@PreAuthorize("@sellerGuard.isActive(#principal.userId)")` — 리소스가 없어 선언적으로 걸린다. SellerGuard는 `isActive`(sellers 행 ACTIVE) / `isSeller`(존재만) 분리 — 정지 셀러 허용 여부는 API 성격별 선택.
- **소유권**: 어노테이션에 넣지 않는다. 서비스가 수정 대상을 어차피 `findById`로 꺼내므로, 그 김에 판정하면 조회 1회로 끝난다.

```kotlin
// 서비스 — userId를 첫 인자로 받는 시그니처 = 소유권 검증 책임을 진다는 계약
// 실제 구현: DeliveryAddressService.loadOwnedActive
private fun loadOwnedActive(userId: Long, addressId: Long): DeliveryAddress {
    val address =
        deliveryAddressRepository.findActiveById(addressId)
            ?: throw BusinessException(UserErrorCode.DELIVERY_ADDRESS_NOT_FOUND)   // 404
    if (address.user.id != userId) {
        throw BusinessException(UserErrorCode.DELIVERY_ADDRESS_ACCESS_DENIED)     // 403 — 남의 리소스
    }
    return address
}
```

**왜 소유권을 @PreAuthorize에 넣지 않나** — `@deliveryAddressGuard.isOwner(...)`를 어노테이션에 두면 판정용으로 리소스를 한 번 더 조회한다(어노테이션은 불린만 반환 → 꺼낸 리소스를 서비스에 못 넘김). 결과: ① 같은 리소스 2회 조회 ② 소유권 규칙이 어노테이션·서비스 두 곳으로 갈라져 변경 시 어긋남(이중 진실) ③ "없음(404)"을 "권한 없음(403)"으로 뭉갬. 서비스 단일 판정이 조회 1회·단일 출처·정확한 상태코드를 준다.

- SELLER를 UserRole enum에 넣지 않는 이유는 domain의 UserRole 주석 참조(셀러는 상태를 가진 애그리거트 → sellers 행으로 판정).
