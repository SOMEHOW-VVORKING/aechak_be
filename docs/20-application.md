# application 모듈 (20-application)

> 성격: 유스케이스 조율 계층. 트랜잭션 경계, 도메인 간 조합, 입출력 계약(Command/Result)의 소유자.
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
    │   └── result/              # 출력
    ├── service/                 # 구현 — 외부에서 import 금지
    │   ├── OrderFacade.kt       # UseCase의 유일한 구현체. @Transactional 경계
    │   └── OrderService.kt      # 비즈니스 로직 보관함 (인터페이스 없음, 필요 시 세분화)
    └── listener/                # "다른 도메인" 이벤트를 수신하는 리스너
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
 * - @Transactional 경계는 여기 고정. Service/도메인 메서드에 트랜잭션 어노테이션 금지.
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
| 출력 | **항상 Result. 엔티티 반환 금지.** 존재 여부 등은 Boolean 허용 | `{대상}Result` / `{대상}SummaryResult` |
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
 */
data class PlaceOrderCommand(
    val buyerId: Long,
    val lines: List<OrderLineCommand>,
    // TODO
)
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

## 5. 리포지토리 위치 — A-1 결정(L2)

- 이 모듈에는 리포지토리 없음. domain의 포트(`domain/{도메인}/repository/`)를 주입받는다 — 구현은 infra/persistence (40 §1).
