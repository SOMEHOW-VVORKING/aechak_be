# infra 모듈들 (40-infra)

> infra는 그룹핑 폴더다. 기술 분류 폴더(persistence/client/kafka/redis) 아래에 구체 모듈이 산다
> (예: persistence/jpa = :jpa-persistence, client/pg-client = :pg-client). 어댑터 코드가 생길 때 모듈을 추가한다.
> 방향(DIP): 포트는 domain 또는 application이 정의하고, **infra가 구현하며 application을 향해 의존**한다.
> 조립은 boot에서만. infra 모듈끼리는 서로 모른다. web-common 의존 금지.

---

## 1. persistence — A-1 결정(L2)

- domain 포트의 어댑터가 여기 산다. 현재 구체 모듈은 `persistence/jpa`(:jpa-persistence).
- Elasticsearch 등 새 저장 기술은 `persistence/elasticsearch`처럼 형제 모듈로 추가해 어댑터 교체로 흡수한다.

```kotlin
// 어댑터 템플릿
package com.aechak.infra.persistence.order

/** Spring Data는 이 모듈 밖으로 노출되지 않는다. 어댑터의 내부 부품. */
interface OrderJpaRepository : JpaRepository<Order, Long>

/**
 * domain 포트(OrderRepository)의 JPA 어댑터.
 * [규칙] 포트 시그니처(도메인 타입)를 그대로 구현. Pageable 등 Spring 타입을 포트로 역류시키지 않는다.
 */
@Repository
class OrderRepositoryAdapter(
    private val jpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun findById(id: Long): Order? = jpaRepository.findByIdOrNull(id)
    override fun save(order: Order): Order = jpaRepository.save(order)
}
```

- payment(L3)의 영속 모델·매퍼는 채택 레벨과 무관하게 항상 이 모듈에 위치한다. // TODO: 결제 착수 시

## 2. infra/kafka

```
infra/kafka/{모듈}/src/main/kotlin/com/aechak/infra/kafka/
├── config/                      # producer/consumer factory, 직렬화 설정
├── outbox/
│   └── OutboxRelay.kt           # outbox 테이블 폴링 → message 모듈 클래스로 발행
└── publisher/
    └── KafkaMessagePublisher.kt # application이 정의한 발행 포트의 어댑터
```

- **발행(프로듀서)은 여기, 소비(컨슈머)는 boot 소속** (30 문서 §4). 이 모듈은 리스너 컨테이너 설정만 제공.
- 페이로드는 message 모듈 클래스만 사용. 도메인 이벤트 클래스 직렬화 금지.
- Outbox/Inbox 상세(스키마, 폴링 주기, DLT 정책)는 별도 문서로. // TODO: EDA 구현 착수 시

## 3. infra/client

- 구체 모듈은 용도별로 추가한다 — 현재 `client/pg-client`(:pg-client).

```kotlin
package com.aechak.infra.client.payment

/**
 * 외부 API 어댑터 (TossPayments 등).
 * [규칙]
 * - 포트(예: PaymentGatewayPort)는 application(또는 payment 도메인)이 정의, 구현만 여기.
 * - 외부사의 요청/응답 dto는 이 모듈 밖으로 새지 않는다 — 포트 시그니처는 우리 어휘로.
 * - 외부 장애는 BusinessException(70001 등 연동 실패 코드)으로 번역해 던진다.
 */
@Component
class TossPaymentsClient( /* RestClient 등 */ ) : PaymentGatewayPort { /* TODO */ }
```

## 4. infra/redis

- 캐시/분산락/토큰 저장 등 어댑터. 마찬가지로 application 포트 구현 형태.
- // TODO: 사용처(세션? 재고 캐시? 락?) 확정 시 포트 목록 정의

## 5. message 모듈 ⚠️ PENDING(A-2) — infra 아님, 참고로 여기 기술

```kotlin
package com.aechak.message.order   // 발행자 기준 패키징

/**
 * Kafka 통합 메시지 (컨슈머와의 계약).
 * [규칙] 스키마 호환성 유지 — 필드 삭제/타입 변경 금지, 추가는 nullable로.
 *        프로세스 내 도메인 이벤트와 별개 클래스 (00 §3-2). 케이스마다 구체 data class로 명시.
 */
data class OrderPlacedMessage(
    val orderId: Long,
    val occurredAt: Instant,
    // TODO
)
```
