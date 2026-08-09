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

### 1-1. PII 암호화 ⚠️ PENDING — 방식 미확정

PII(전화번호·계좌번호 등)의 저장 암호화(AES-256) 방식은 아직 팀 결정 전이다.
확정 전까지 리뷰에서 암호화 누락·방식을 지적하지 않는다 (REVIEW.md 참조).

논의 중 확인된 제약 (확정 시 반영):
- 엔티티에서 `@Convert`로 infra converter를 지목하면 domain→infra 의존 발생 → 00 §2 위반.
  후보안은 domain 값 타입(`data class Encrypted`) + infra의 `@Converter(autoApply=true)` 타입 매칭.
- 값 타입에 `@JvmInline` 금지 — 인라인 언박싱으로 JVM 필드가 String이 되어 Hibernate가
  converter를 매칭하지 못한다(조용한 평문 저장).
- 미결 쟁점: 암호화 로직의 위치 — jpa-persistence 내부 vs 독립 crypto 모듈(캐시·메시지 등
  JPA 외 경로에서 재사용 필요 시). // TODO: 멘토 자문 후 확정

## 2. infra/kafka

```
infra/kafka/src/main/kotlin/com/aechak/infra/kafka/
├── config/                         # MessagingJacksonConfig 등 직렬화 설정
├── consumer/                       # TraceIdRecordInterceptor, DLT 에러 핸들러
├── outbox/
│   ├── OutboxSweeper.kt            # PENDING 잔여분 재발행(50행 청크). 구동 스케줄은 boot/batch 소유
│   └── OutboxStatus.kt             # PENDING/PUBLISHED/DEAD/HOLD. 전이는 PENDING에서만
└── publisher/
    ├── MessagePublisherAdapter.kt  # 발행 포트의 유일 구현. 마커 타입으로 경로 선택
    ├── OutboxMessagePublisher.kt   # 유실 불가 레인: 같은 tx에 outbox 기록 + 커밋 직후 즉시 발행
    ├── DirectMessagePublisher.kt   # 유실 가능 레인: 브로커 직행 fire-and-forget
    ├── EnvelopeFactory.kt
    └── KafkaSender.kt              # 토픽 조립·파티션 키·헤더 규칙의 단일 지점
```

- **발행(프로듀서)은 여기, 소비(컨슈머)는 boot 소속** (30 문서 §4). 이 모듈은 리스너 컨테이너 설정만 제공.
- 페이로드는 message 모듈 클래스만 사용. 도메인 이벤트 클래스 직렬화 금지.

### 2-1. 이벤트 발행·소비 사용법

이벤트는 message 모듈에 선언하고 레인 마커를 고른다 — 유실이 사고면 `GuaranteedMessage`, 놓쳐도 되면 `BestEffortMessage`.

```kotlin
data class OrderPlacedMessage(
    val orderId: Long,
    override val aggregateId: String,                        // 파티션 키 = 순서 단위
    override val occurredAt: Instant = Instant.now(),
) : GuaranteedMessage {
    override val aggregateType = "order"                     // 토픽 라우팅
    override val eventId get() = "order-$orderId:placed"     // 자연 멱등키. 없으면 생성자 프로퍼티 UUID
    override val allowedDelay = 30.minutes                   // 생략 시 INFINITE(만료 없음)
}
```

발행은 트랜잭션 안에서 포트로: `messagePublisher.publish(OrderPlacedMessage(...))`.
유실 불가 레인은 비즈니스 커밋과 원자로 기록되고 커밋 직후 즉시 발행되며, 실패분은 batch 스위퍼가 재발행한다.
allowedDelay를 넘기면 HOLD로 전이돼 수동 재개를 기다린다(재개: `status=PENDING` 복귀 + `expired_at` 해제·연장).

소비는 핸들러 시작에서 `processedMessages.markProcessed(컨슈머명, eventId)`가 false면 스킵(중복 배달).
처리와 인박스 기록은 같은 트랜잭션으로 묶는다. 실물: boot/api의 SyntheticConsumer.

## 3. infra/client

- 구체 모듈은 용도별로 추가한다 — 현재 `client/pg-client`(:pg-client).

```kotlin
package com.aechak.infra.client.payment

/**
 * 외부 API 어댑터 (TossPayments 등).
 * [규칙]
 * - 포트(예: PaymentGatewayPort)는 application(또는 payment 도메인)이 정의, 구현만 여기.
 * - 외부사의 요청/응답 dto는 이 모듈 밖으로 새지 않는다 — 포트 시그니처는 우리 어휘로.
 * - 외부 장애는 BusinessException(결제 대역 60000번대 연동 실패 코드)으로 번역해 던진다.
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
