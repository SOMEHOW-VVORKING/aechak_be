# domain 모듈 (10-domain)

> 성격: 순수 도메인 모델. 비즈니스 규칙의 본진.
> 허용 의존: `common` (BusinessException, ErrorCode 규약), kotlin-stdlib, `ulid-creator`(전이 의존 0 순수 Java — publicId 채번, support/Ulid 파사드로 격리).
> A-1 결정(L2): `jakarta.persistence-api` 허용 — @Entity는 domain 동거(§2·§5 유지).
> 리포지토리는 포트를 domain이 소유(§4)하고 구현은 infra/persistence(40 §1)에 둔다.

---

## 1. 패키지 구조 (도메인별 패키지, 모듈 분리 없음)

```
domain/src/main/kotlin/com/aechak/domain/
├── order/                      # BC 패키지 — 직속 파일 없음, 애그리거트 패키지 + error/만 가진다
│   ├── order/                  # BC 대표 애그리거트도 예외 없이 자기 패키지 (order.order — 스터터 수용)
│   │   ├── Order.kt            # 애그리거트 루트 + 자식 엔티티
│   │   ├── OrderItem.kt
│   │   ├── enums/              # 이 애그리거트 전용 enum (OrderStatus 등)
│   │   ├── event/              # 이 애그리거트가 "발행"하는 프로세스 내 이벤트
│   │   └── repository/         # A-1 결정(L2) — 포트 인터페이스 (구현은 infra/persistence)
│   ├── cart/ · group/ · stock/ · shipment/ · claim/   # 위성 애그리거트(군) — 동일 내부 구조
│   └── error/                  # error만 BC 레벨 — 대역(50000번대)은 BC 소유, 100번대 구분이 애그리거트 (05 §0-4)
├── user/ ...                   # 동일 구조 반복 (user/·social/·pet/·term/·privacy/·report/·point/·address/)
└── support/
    └── AggregateRoot.kt        # 이벤트 수집 베이스 (아래 §3)
```

- 새 도메인 추가 = 패키지 하나 추가. common/web-common은 건드리지 않는다.
- **애그리거트 패키지 규칙** (2026-07-10 도입, 세분화 확정):
  1. BC 패키지 직속에는 코드 파일을 두지 않는다 — 애그리거트 패키지들과 `error/`만.
  2. **모든 애그리거트(군)는 예외 없이 자기 패키지를 가진다.** BC 대표 애그리거트도 동명 하위
     패키지(`user.user`, `order.order`)로 — BC(컨텍스트)와 애그리거트는 다른 개념 레벨이므로
     스터터가 아니라 좌표다. 대표 애그리거트가 없는 BC는 의미명(`search.recent` 등).
  3. 애그리거트 패키지 내부: 루트·자식 엔티티는 직속, enum은 `enums/`(enum은 예약어라 복수형),
     포트는 `repository/`, 이벤트는 `event/`.
  4. 강하게 결합된 소형 애그리거트 묶음은 한 패키지를 공유한다
     (예: `pet/` PetProfile+Breed, `term/` Term+ConsentRecord, `shipment/` Shipment+CourierMaster).
     패키지 공유와 에러 코드 100번대 배정은 별개다 — 100번대는 애그리거트 루트마다 따로 받는다 (05 §0-4).
  5. `error/`는 BC 레벨 — 에러 코드 대역이 BC 단위 소유라서 애그리거트로 쪼개지 않는다.
     enum 파일은 BC당 하나이고, 대역 안의 100번대 하나가 애그리거트 루트 하나에 대응한다 (05 §0-4).
- 에러 코드 enum은 **발생하는 BC 패키지가 소유**한다 (05 문서 §5 템플릿 참조).

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

## 2-1. 연관관계 규칙 — 경계선은 BC (2026-07-10 아키텍트 결정)

> 목적: MSA 분리 시 BC 단위로 잘 쪼개지는 것. BC 내부는 어차피 한 DB로 남으므로 연관과 FK를 쓰고,
> BC 경계만 값참조로 끊어 분리 비용을 0에 수렴시킨다.

1. **애그리거트 내부** (루트→자식): `@OneToMany(cascade = ALL, orphanRemoval = true) + @JoinColumn`
   단방향. 자식은 부모 참조·FK 필드를 갖지 않는다 — 자식의 생성·변경·삭제는 루트 메서드가 유일한 경로.
2. **같은 BC의 다른 애그리거트**: 참조하는 쪽의 `@ManyToOne(fetch = LAZY)` **단방향** 연관.
   - 역방향 `@OneToMany` 컬렉션은 만들지 않는다 (루트 로딩 시 이력·원장류가 딸려오는 것 방지).
   - **연관 내비게이션으로 타 애그리거트의 상태를 변경하지 않는다** (`order.orderGroup.markPaid()` 금지).
     연관은 조회·정합 검증용이고, 다른 애그리거트의 수정은 여전히 자기 리포지토리로 로딩해서 한다.
   - 타 애그리거트의 **자식** 엔티티 직접 참조(ClaimItem→OrderItem, OptionCombinationValue→OptionValue)는
     ERD FK에 충실한 조회 전용 연관 — 대상의 수명주기·수정은 각 루트 경유(위 금지 조항 동일 적용).
   - FK 제약은 ddl-auto(추후 마이그레이션)가 이 연관에서 자동 생성 — BC 내부 무결성은 DB가 보장.
   - 쓰기 경로에서 참조 대상의 본문이 필요 없으면 어댑터가 `getReference()`(프록시)로 FK만 세팅해
     불필요한 SELECT를 없앤다 — 컨벤션 상세는 40 문서 작성 시 확정. // TODO
3. **BC 경계 밖**: `Long` id 값참조. JPA 연관 금지, **DB FK 제약도 걸지 않는다** (분리 대비).
4. **명시적 예외**:
   - `RetentionRecord.userRef` — 회원 파기 후에도 법정 보존 행이 남아야 하므로 soft-ref(연관·FK 금지).
   - `PointTransaction.sourceId` — 다형 참조(출처), 연관 불가.
   - `ProductStats.productId` — PK=FK 식별자 공유(assigned PK). 연관보다 강한 결합이 이미 있어 그대로 둔다.
   - **review는 별도 BC로 취급** — ERD가 리뷰→상품을 "작성 시점 복사(값참조)"로 명시, 리뷰는 order도 참조.
     대외 참조(product·order·user행 4건)만 값참조이고, review BC 내부(ReviewReport→Review)는 규칙 2대로 연관.

## 2-2. 공통 시각 컬럼과 외부 노출 식별자 (2026-07-10 결정)

1. **BaseEntity** (`support/BaseEntity`, @MappedSuperclass): created_at/updated_at 전 엔티티 공통 —
   @PrePersist/@PreUpdate 순수 JPA 콜백(도메인은 Spring Auditing 금지). AggregateRoot가 상속하므로
   루트는 자동, 자식·순수 레코드는 `BaseEntity()`를 직접 상속한다. 엔티티에 시각 필드를 직접 선언하지
   않는다(도메인 의미가 있는 커스텀 시각 — withdrawnAt·submittedAt 등 — 은 예외).
   common에 두지 않는 이유: common은 무의존 모듈이라 jakarta.persistence 반입 불가(05 문서).
2. **publicId(ULID)**: 내부 bigint id의 열거(enumeration) 노출이 치명적인 애그리거트는
   `@Column(nullable=false, updatable=false, length=26) val publicId: String = Ulid.generate()` + UNIQUE.
   생성기는 `support/Ulid` 파사드(ulid-creator 위임 — 엔티티가 라이브러리를 직접 부르지 않는다). 현재 적용: Product·OrderGroup(주문번호 역할)·Order·Claim.
   외부 API·URL에는 publicId만 노출하고 내부 id는 응답에 싣지 않는다.
   - 채번은 필드 초기화식(생성 시점 신원 확보 — DDD identity-at-creation). 엔티티를 도메인 밖에서
     new로 재구성해 detached merge하면 재채번되므로 **재구성 merge 금지, 항상 load 후 수정**.
   - 어노테이션은 필드에 직접 = **field access 전제** — property access로 바꾸면 `val` 주입이 깨진다.
   - MySQL 전환(DDL 확정) 시 publicId 컬럼은 `CHARACTER SET ascii` 지정 — utf8mb4 CHAR(26)은
     UNIQUE 인덱스 키 폭이 4배(104바이트)가 된다. 적재 후 ALTER는 인덱스 리빌드라 그때 하면 비싸다.
   - publicId는 추측 불가를 담보할 뿐 **인가를 대체하지 않는다** — 조회 API마다 소유권 검증(BOLA/IDOR 방지) 필수.

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
- **data class 금지** — 엔티티 동일성은 id 기반으로 판단한다.
  - equals/hashCode **override는 보류**한다. 현재 연관 컬렉션은 전부 `List`(Set/Map 키로 엔티티를 쓰지 않음)라
    JPA 기본 참조 동일성으로 충분하고, id 전략이 이종(IDENTITY vs @MapsId/assigned PK)이라 BaseEntity 중앙화가 깔끔히 안 되며,
    IDENTITY id는 영속 전 0이라 순진한 id-equals가 미영속 엔티티를 동일 판정하는 함정이 있다.
  - **엔티티를 Set/Map 키로 쓰기 시작하는 시점**에, 프록시-세이프(Hibernate.getClass)·미영속(id=0) 함정을 고려한
    id-equals를 그 엔티티에 도입한다(그때 값이 생긴다). 그 전까지 boilerplate override는 소비자 없는 코드라 두지 않는다.
- 위 플러그인 설정은 domain 모듈 build.gradle.kts에만 적용

## 6. payment 예외 (기존 결정 유지)

payment 도메인은 헥사고날 완전 적용(L3): 순수 도메인 모델과 JPA 엔티티 분리 + 매퍼.
이 문서의 §2, §5는 payment에 적용하지 않으며, payment 전용 규칙은 별도 문서로 분리한다. // TODO: 결제 구현 착수 시 작성
