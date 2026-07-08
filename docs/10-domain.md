# domain 모듈 (10-domain)

> 성격: 순수 도메인 모델. 비즈니스 규칙의 본진.
> 허용 의존: `common` (BusinessException, ErrorCode 규약), kotlin-stdlib.
> A-1 결정(L2): `jakarta.persistence-api` 허용 — @Entity는 domain 동거(§2·§5 유지).
> 리포지토리는 포트를 domain이 소유(§4)하고 구현은 infra/persistence(40 §1)에 둔다.

---

## 1. 패키지 구조 (도메인별 패키지, 모듈 분리 없음)

```
domain/src/main/kotlin/com/aechak/domain/
├── order/
│   ├── Order.kt                # 애그리거트 루트
│   ├── OrderLine.kt
│   ├── OrderStatus.kt
│   ├── event/                  # 이 도메인이 "발행"하는 프로세스 내 이벤트
│   │   └── OrderPlacedEvent.kt
│   ├── error/
│   │   └── OrderErrorCode.kt   # 50000번대 (05 문서 코드 체계)
│   └── repository/             # A-1 결정(L2) — 포트 인터페이스 (구현은 infra/persistence)
│       └── OrderRepository.kt
├── user/ ...                   # 동일 구조 반복
└── support/
    └── AggregateRoot.kt        # 이벤트 수집 베이스 (아래 §3)
```

- 새 도메인 추가 = 패키지 하나 추가. common/web-common은 건드리지 않는다.
- 에러 코드 enum은 **발생하는 도메인 패키지가 소유**한다 (05 문서 §5 템플릿 참조).

## 2. Rich Domain Model 규칙

@Entity는 "JPA 파일"이 아니라 **도메인 모델 그 자체**다. anemic(getter/setter 껍데기) 금지.

```kotlin
package com.aechak.domain.order

/**
 * 주문 애그리거트 루트.
 *
 * [규칙]
 * - 상태 변경은 의도가 드러나는 메서드로만 (cancel, confirm...). setter 노출 금지.
 * - 자기 상태만으로 판단 가능한 불변식/상태 전이 규칙은 전부 여기에 산다.
 *   외부 지식(다른 애그리거트, DB 조회)이 필요한 검증은 application 몫 (00 문서 §3-5).
 * - 규칙 위반 시 BusinessException(도메인 ErrorCode)을 던진다.
 * - 상태 변경이 다른 도메인의 관심사이면 registerEvent()로 이벤트를 수집한다.
 */
@Entity
@Table(name = "orders")
class Order protected constructor(          // JPA용 기본 생성자는 plugin.jpa가 생성
    val buyerId: Long,
) : AggregateRoot() {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    var status: OrderStatus = OrderStatus.PENDING
        protected set                        // 외부 직접 조작 금지

    /** 상태 전이 규칙 예시: 배송 시작 이후 취소 불가 */
    fun cancel() {
        if (status.isShippedOrLater()) {
            throw BusinessException(OrderErrorCode.CANNOT_CANCEL_SHIPPED)
        }
        status = OrderStatus.CANCELLED
        registerEvent(OrderCancelledEvent(id))
    }

    companion object {
        /** 생성 시점 불변식은 팩토리에서 강제한다 */
        fun place(buyerId: Long, lines: List<OrderLine>): Order {
            // TODO: 불변식 검증 후 생성 + OrderPlacedEvent 수집
            TODO()
        }
    }
}
```

**동시성 예외 조항**: "재고 ≥ 주문수량"처럼 동시성 정합성이 걸린 규칙은 엔티티 메서드가 아니라
**저장소 레벨**(조건부 원자 UPDATE `WHERE stock >= ?`)로 강제한다. 엔티티에 중복 구현하지 않는다.

## 3. 이벤트 수집 패턴 (도메인은 발행 메커니즘을 모른다)

domain은 Spring을 모르므로 ApplicationEventPublisher를 직접 못 쓴다.
**애그리거트가 수집 → Facade가 발행.** `AbstractAggregateRoot`(spring-data-commons) 사용 금지 — domain에 Spring이 딸려온다.

```kotlin
package com.aechak.domain.support

/** 도메인 이벤트 수집 베이스. 발행은 application(Facade)의 책임. */
abstract class AggregateRoot {
    @Transient
    private val _events = mutableListOf<Any>()
    val events: List<Any> get() = _events.toList()

    protected fun registerEvent(event: Any) { _events += event }
    fun clearEvents() { _events.clear() }
}
```

```kotlin
package com.aechak.domain.order.event

/**
 * 프로세스 내 도메인 이벤트. 발행자(order) 패키지가 소유한다.
 * 수신 리스너(application/{수신도메인}/listener)가 이 클래스를 import하는 방향은 정상 —
 * 수신자는 발행자의 어휘를 알지만, 발행자는 수신자를 모른다.
 * Kafka로 나가는 메시지와 재사용 금지 (00 문서 §3-2).
 */
data class OrderCancelledEvent(val orderId: Long)
```

## 4. 리포지토리 — A-1 결정(L2)

- 포트 인터페이스를 여기(`domain/{도메인}/repository/`)에 둔다.
  시그니처는 도메인 타입만 사용 — Pageable 등 Spring 타입 노출 금지. 구현은 infra/persistence (40 문서).
- 결정 배경: Elasticsearch 등 저장 기술 도입·교체를 포트 뒤 어댑터 교체로 흡수한다.

```kotlin
// 포트 템플릿
interface OrderRepository {
    fun findById(id: Long): Order?
    fun save(order: Order): Order
    // TODO: 애그리거트 로딩/저장 중심으로 최소하게. 복잡한 조회는 별도 논의(CQRS-lite)
}
```

## 5. JPA 동거 비용 (빌드 설정)

- `kotlin("plugin.jpa")` — no-arg 생성자 자동 생성
- `kotlin("plugin.allopen")` + @Entity/@MappedSuperclass/@Embeddable 지정 — lazy 프록시용 open
- **data class 금지** — equals/hashCode는 id 기반으로 직접 정의
- 위 플러그인 설정은 domain 모듈 build.gradle.kts에만 적용

## 6. payment 예외 (기존 결정 유지)

payment 도메인은 헥사고날 완전 적용(L3): 순수 도메인 모델과 JPA 엔티티 분리 + 매퍼.
이 문서의 §2, §5는 payment에 적용하지 않으며, payment 전용 규칙은 별도 문서로 분리한다. // TODO: 결제 구현 착수 시 작성
