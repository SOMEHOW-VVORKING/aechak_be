# 테스트 전략 (70-testing)

> - 테스트 코드를 작성/수정할 때 `00-overview` + 이 문서 + 해당 모듈 문서를 투입한다. 테스트 규칙의 단일 출처이며, 모듈 문서는 테스트 규칙을 중복 서술하지 않는다.
> - 스택: Spring Boot 4.0.5 / Kotlin 2.2 / JDK 21. 실 DB = MySQL 8.x, 통합 테스트 DB = Testcontainers MySQL — 로컬·CI 모두 Docker
    필요.
> - boot/api 테스트 베이스 실물: `boot/api/src/test/kotlin/com/aechak/api/support/` (IntegrationTestBase ·
    KafkaIntegrationTestBase · IntegrationTestConfig · DatabaseCleaner). 새 통합 테스트를 짜기 전에 원본을 먼저 읽는다.
> - 테스트 소스셋은 모듈 사설이다 — 위 베이스는 boot/api 밖에서 상속할 수 없다(공유 장치 없음, §9).

## 0. 핵심 요약 — 이것만 기억한다

1. 순수 로직은 스프링 없는 **단위 테스트**(모듈 무관), 스프링 배선(트랜잭션·JPA·이벤트·HTTP)이 필요하면 boot **통합 테스트**.
2. 통합 테스트는 **그 모듈의 베이스를 상속한다** — boot/api에는 둘(`IntegrationTestBase`, 내장 Kafka용 `KafkaIntegrationTestBase`)이 있고 MySQL 컨테이너를 공유한다. 베이스는 모듈 밖으로 못 나간다.
3. 격리는 롤백이 아니라 **커밋 + truncate**.
4. 더블은 **mock보다 Fake 우선** — 외부 경계만 가짜로 바꾸고, 검증은 호출 횟수가 아니라 결과로 한다.
5. 금지: **H2 · `@DirtiesContext` · 클래스별 properties/`@MockitoBean` · 자체 `@Testcontainers` · 이벤트 테스트에 `@Transactional` ·
   리포지토리 mock + verify**.
6. 목표는 커버리지 숫자가 아니라 **"깨지면 안 되는 계약이 테스트로 못 박혀 있는가"**다.

---

## 1. 전략

**규칙**: 통합 우선(integration-first)을 표준으로 채택한다. 순수 로직만 단위로 내리고, 나머지는 boot에서 실 배선(실 레포 + MySQL 컨테이너)으로 검증한다.

**근거**: 이 시스템의 위험한 버그는 배선 쪽에 몰려 있다 — JPA 매핑, 트랜잭션 경계, AFTER_COMMIT 이벤트, MySQL 고유 SQL(SKIP LOCKED, 조건부 원자 UPDATE). 이 부류는
mock으로 검증이 불가능하다. 트레이드오프(느린 CI, 실패 국소화 저하)는 알고 감수한다.

**재검토 트리거**: (a) 통합 스위트 로컬 벽시계 3분 초과, (b) 한 회귀에 통합 테스트 3개 이상 동시 레드. 관찰되면 슬라이스/단위 비중을 늘리는 방향으로 재검토한다.

| 계층              | 무엇을                               | 어디서                       | 도구                    |
|-----------------|-----------------------------------|---------------------------|-----------------------|
| 순수 단위           | 불변식·계산·상태 전이·포트 Fake 유스케이스·어댑터 로직 | 대상 코드가 있는 모듈의 `src/test`  | `kotlin.test`, 스프링 없음 |
| 유스케이스·매핑·이벤트 통합 | 실 배선 검증                           | `boot/api/src/test`       | JUnit5 + 공용 베이스       |
| 부팅 스모크          | 매핑·컨텍스트 안전망                       | `boot/*/src/test`         | 그 모듈의 베이스(contextLoads) |
| 외부 클라이언트        | 계약 검증                             | `infra/client/*/src/test` | stub 허용(§5)           |

### 1.1 비관 시나리오 — 성공 경로만 고정하지 마라

**규칙**: 계약마다 성공 경로 하나로 끝내지 않는다. 아래 다섯 각도로 "이게 깨지면 뭐가 터지나"를 묻고, 터지는 게 있으면 테스트로 고정한다. (각 각도의 실물 사례 = `EventBackboneIntegrationTest`)

1. **보장의 반대면** — 성공을 보장하는 장치가 실패했을 때도 성립하나. 예: 멱등 "스킵"의 반대면 = 처리 실패 시 인박스 기록도 롤백돼야 한다 — 아니면 재전달이 헛스킵되어 유실.
2. **크래시 지점별 재현** — 작업 도중 아무 지점에서 죽으면? DB 상태를 그 지점으로 되돌려 재현한다. 예: 발행 완료 기록 직전 크래시 → 재발행돼도 컨슈머 효과는 1회.
3. **불변식의 반대 조작** — 지키려는 조건을 일부러 깨보고 방어되는지 본다. 예: 스위퍼가 종결·보류 행(PUBLISHED·DEAD·HOLD)을 다시 집지 않는가 — "PENDING 조건쯤 없어도 되겠지"라는 선의의 수정이 매 주기 전량 재발행을 만드는 걸 이 테스트가 막는다.
4. **진화 호환** — 상대가 먼저 바뀌었을 때 내가 사나. 예: 모르는 필드가 든 엔벨로프도 소비된다(관용 리더 고정).
5. **시간·재시도** — 재시도가 실제로 미뤄지는지(핫루프 방지), 경계에서 정확히 전이하는지. 예: 만료 경계에서 발행 대신 HOLD로 전이 + 다시 보내도 실패할 행은 DEAD로 격리해 뒤 행이 계속 나간다(한 행이 큐 전체를 막지 않는가).

**근거**: 낙관 스위트는 리팩터링 안전망이 못 된다 — 위험한 회귀는 성공 경로가 아니라 실패 처리·경계·복구 코드로 들어온다.

**따라오는 요령**: 부정 단언("안 생긴다")은 잠깐 기다린 뒤 관찰한다 — 즉시 단언은 항상 통과한다. 재현 비용이 검증 가치를 넘는 시나리오(브로커 다운, 리밸런스, 멀티 인스턴스 경쟁)는 넣지 않되, 무엇을 안 다뤘는지 PR에 남긴다.

## 2. 단위 테스트

**규칙**: 순수 로직은 `kotlin.test`로 스프링 없이 검증한다. 단위 테스트의 기준은 모듈이 아니라 **테스트 성격**이다 — domain/common은 스프링이 클래스패스에 없어 구조적으로 강제되고,
application·boot의 코드도 스프링 없이 검증 가능하면 순수 단위로 쓴다(포트 In-Memory Fake 기반 유스케이스 로직, 어댑터 로직 등 — 예: `TokenServiceTest`). 테스트는 대상
코드가 있는 모듈에 둔다.

**함정 — 로깅 바인딩**: 라이브러리 모듈(`infra/*`, `message` 등)은 `slf4j-api`만 의존하는 경우가 많다. 바인딩이 없으면 MDC가 no-op이라 `MDC.get()`이 항상 null을 돌려주고, MDC를 검증하는 테스트가 조용히 무의미해진다(boot 모듈에서는 Boot가 logback을 공급해 통과하다가, 대상 모듈로 옮기는 순간 드러난다). 그런 테스트가 있는 모듈은 `testRuntimeOnly(libs.logback.classic)`을 건다.

**파일명**: 대상 프로덕션 **파일 기준으로 짓는다** — `PetProfile.kt` → `PetProfileTest.kt`. 시나리오명(`PetProfileRegistrationTest` 등)으로 짓지 않는다. 패키지 미러링과 같은 근거이고(짝 매칭·탐색성), 한 프로덕션 파일의 테스트가 여러 곳에 흩어지는 것도 막는다. 한 파일의 테스트가 커지면 파일을 쪼개지 말고 `@Nested`로 묶는다. 통합 테스트는 짝이 없으므로 대상 기능명 + `IntegrationTest`(예: `PetProfileIntegrationTest`).

**예외**: 실 레포지토리·트랜잭션·커밋 관찰이 필요해지면 단위 대상이 아니다 — 통합으로 올린다. Fake 단위는 남용하지 않는다 — 배선 검증을 대체하지 못한다.

역방향도 금지다: 순수 계산을 `@SpringBootTest`로 검증하지 않는다(부팅 비용 낭비 + 실패 국소화 저하).

### 2.1 테스트 패키지 — 미러링이 기본

**규칙**: 테스트는 대상 코드와 **같은 모듈, 같은 패키지**에 둔다(`com.aechak.api.product.controller`의 `ProductController` → `boot/api/src/test/.../api/product/controller/`, `com.aechak.infra.kafka.outbox`의 클래스 → `infra/kafka/src/test/.../infra/kafka/outbox/`).

**근거**: IDE·커버리지 도구의 짝 매칭과 탐색성. 그리고 `internal` 가시성은 같은 모듈이라야 열리므로 미러링이 곧 컴파일 조건이기도 하다.

**예외**: 특정 클래스를 겨냥하지 않는 통합 테스트는 미러링할 짝이 없다. 한 모듈 안의 흐름이면 상위 패키지에 평평하게 두고, 대상이 여러 모듈에 걸쳐 있으면 실행 모듈(boot/api)에 **주제 패키지**를 만들어 모은다. 주제 패키지의 현재 실물은 둘뿐이다 — `eventbackbone`(message·infra/kafka·boot/api를 관통하는 발행·소비 흐름), `support`(테스트 인프라). 새로 만드는 것은 이 조건을 만족할 때만이고, 그 커밋에서 여기 목록에 추가한다.

## 3. 통합 테스트 (boot)

**규칙**: 모든 통합 테스트는 **자기 모듈의 베이스**를 상속한다. boot/api에서는 `IntegrationTestBase`, 내장 Kafka가 필요하면 `KafkaIntegrationTestBase`(§3.0). 개별 테스트 클래스에서
`@SpringBootTest(properties=...)`·`@MockitoBean`·`@DirtiesContext`·자체 `@Testcontainers`/`@Container`를 선언하지 않는다.

**모듈 경계 주의**: 테스트 소스셋은 모듈 사설이라 baseline을 다른 모듈에서 상속할 수 없다(현재 `java-test-fixtures` 등 공유 장치 없음). 다른 실행 모듈에 첫 통합 테스트를 넣는 커밋에서는 **그 모듈의 베이스를 함께 만들거나** 공유 방식을 먼저 결정한다(§9). 베이스 수는 모듈당 최소로 유지한다.

**근거**: Spring 테스트 컨텍스트 캐시는 프로퍼티·bean override·`@AutoConfigure*` 등 설정 전부를 캐시 키에 넣는다. 클래스마다 설정이 조금이라도 다르면 컨텍스트가 갈라져 부팅이
반복된다(실행 시간 증가 + 같은 DB에 스키마 생성 중복). 전원이 같은 베이스를 상속하면 부팅은 JVM당 1회로 상수화된다. 캐시 논리 설명은 이 절에만 둔다 — 다른 절의 금지 규칙들은 전부 여기로 귀결된다.

**예외**: 정말 다른 설정이 필요하면 런타임 토글로 우회 가능한지 먼저 보고(예: Hibernate statistics는 `isStatisticsEnabled` 런타임 토글), 불가피할 때만 명시적 2번째
베이스를 만들어 여러 클래스가 재사용한다. 2번째 베이스를 만드는 커밋에서는 컨테이너를 빈 수명에서 분리(수동 start)하는 전환을 함께 한다 — 빈으로 등록된 컨테이너는 컨텍스트가 닫힐 때 함께 멈출 수 있다.

### 3.0 이벤트 백본(Kafka) 통합 — 2번째 베이스의 현재 실물

내장 Kafka 브로커·flyway 스키마가 필요한 테스트는 `KafkaIntegrationTestBase`를 상속한다
브로커 왕복 검증은 EmbeddedKafka로 결정했고, 비동기 대기는 Awaitility(카탈로그 배선 완료)를 쓴다.
아웃박스 계약 검증의 필수 단언: 성공 시 도메인 변경 + outbox 행 동반 커밋, 실패 주입 시 둘 다 롤백(유령 이벤트 차단),
컨슈머 멱등(같은 eventId 2회 → 효과 1회), DLT 격리 후 파이프 지속, 즉시 발행 격리(스위퍼 개입 없이 PUBLISHED),
스위퍼 재발행·다시 보내도 실패할 행의 DEAD 격리(뒤 행은 계속 발행), 만료 시 HOLD 전이(종결·보류 상태 불가침 포함).
실물: `EventBackboneIntegrationTest`. MySQL 컨테이너는 두 베이스가 공유하며 빈 수명에서 분리(수동 start)돼 있다.

### 3.1 컨트롤러 통합

현재 컨트롤러 통합 테스트는 도입하지 않았다. 착수 시점에 방식(standalone MockMvc vs `@AutoConfigureMockMvc`를 베이스에 붙이는 통합)을 §9 기준으로 결정한다. 통합으로 가면 `@AutoConfigureMockMvc`는 개별 클래스가 아니라 베이스에 붙이고(캐시 키 참여), `webEnvironment=RANDOM_PORT`는 쓰지 않는다.

### 3.2 부팅 스모크

`contextLoads()`는 전 엔티티 매핑 검증 + 스키마 생성을 겸하는 최소 안전망이다. 공용 베이스 위라 매핑 검증도 실 MySQL 기준이다.

### 3.3 쓰지 않는 것

- **`@DataJpaTest` 슬라이스** — 별도 컨텍스트를 만들어 단일 컨텍스트를 분할하고 얻는 게 적다.
- **상시 매핑 SQL 회귀 테스트** — 컬렉션 매핑의 SQL 특성은 엔티티 셋업 시점에 실측으로 검증을 마쳤고, SQL 문장 수 고정 단언은 배칭·채번·Hibernate 버전에 커플링돼 유지비가 이득을 넘는다.
  이후 N+1 등 SQL 개수가 계약인 테스트를 만들게 되면: 컬렉션 액션 UPDATE는 `entityUpdateCount`에 안 잡히므로 `prepareStatementCount`로 세고, 전제(
  배칭·채번·fetch)를 KDoc에 명시한다.

## 4. 격리와 정리

**규칙**: 기본 격리 = 커밋 + `@BeforeEach` 전 테이블 truncate(베이스가 수행). 테스트는 `tx.execute { }`로 실제 커밋한다.

**근거**: 이 아키텍처는 실 커밋과 `@TransactionalEventListener(AFTER_COMMIT)` 관찰이 필요하다. `@Transactional` 롤백 테스트에서는 커밋이 없어
AFTER_COMMIT 리스너가 발화하지 않고, 이벤트/outbox 흐름이 조용히 미검증 상태로 통과한다.

**예외**: 커밋 의미가 불필요한 순수 매핑/조회 테스트에만 `@Transactional`(롤백)을 명시적으로 허용한다. 단 그 경우에도 `@BeforeEach`의 TRUNCATE는 MySQL 암묵 커밋을
유발하므로 롤백 격리에 정밀한 의미를 싣지 않는다.

함정 목록:

- **1차 캐시 가짜 통과**: save 후 같은 트랜잭션에서 조회하면 DB가 아니라 영속성 컨텍스트가 답한다. 저장 검증은 커밋 후 별도 트랜잭션에서 재조회(또는 flush+clear)한다.
- **빈 테이블 가정은 클리너 위에서만**: 격리의 근거는 언제나 truncate다. 클리너를 우회하는 테스트에서 "이 id는 없겠지" 식 전제를 깔지 않는다. 유니크 제약 컬럼은 한 테스트 안에서도 중복 주의.
- **병렬 실행 OFF**: 공유 컨테이너 + 커밋 기반이라 클래스 병렬은 데이터 경합을 낳는다. `junit.jupiter.execution.parallel.enabled`를 켜지 않는다.

## 5. 테스트 더블

**규칙**: 기본은 진짜(실 레포·실 배선·실 MySQL)다. 외부 경계(PG·SMS 등)만 In-Memory Fake를 `IntegrationTestConfig`에 한 벌 등록해 전 테스트가 공유한다.

**근거**: 클래스별 `@MockitoBean`은 컨텍스트를 가른다(§3). 리포지토리를 mock하고 `verify { save() }`로 끝내는 테스트는 배선을 검증하지 못하므로 금지 — 유스케이스는 실 레포로
결과를 확인한다.

**예외**: 실제 외부 계약(PG 응답 스펙 등)은 Fake가 아니라 `infra/client`의 계약 테스트가 잡는다. 시간·랜덤이 로직에 들어오면 `Clock`/시드 랜덤을 주입 가능한 빈으로 설계하고
테스트에서 고정한다.

## 6. 통합 테스트 DB = Testcontainers MySQL

**규칙**: 통합 테스트는 운영과 같은 엔진(MySQL 8.x)의 컨테이너 위에서 돈다. H2 등 인메모리 DB는 test 클래스패스에조차 두지 않는다.

**근거**: 방언·락·에러 코드가 다른 DB로 통과한 테스트는 "테스트 그린, 프로덕션 레드"를 만든다. 이 시스템엔 그 부류(SKIP LOCKED, unique 위반 catch 멱등, 조건부 원자 UPDATE)가
처음부터 있다. H2가 클래스패스에 있으면 배선 실수가 조용히 H2로 폴백해 통과하므로 물리적으로 제거한다. 컨테이너 기동은 단일 컨텍스트 덕에 JVM당 1회라 비용이 상수다.

세부:

- 이미지 태그는 운영 DB 버전으로 고정한다(`latest` 금지). 현재 태그 값은 코드가 들고 있다.
- 스키마는 지금 `ddl-auto=create`로 만든다(ERD 확정 전 과도기).
- 알려진 예외: `boot/batch`는 아웃박스 스위퍼가 실 outbox 테이블을 읽어야 해서 런타임 DB가 MySQL이다(H2 제거 완료, SCRUM-143). 테스트는 아직 착수하지 않았다 — 착수하는 커밋에서 재사용 가능한 베이스(§3 예외)부터 만들고 같은 패턴으로 정렬한다. 그 전까지 batch 테스트 작성 금지.

## 7. 스타일

- 클래스 KDoc 첫 줄에 테스트 정체(계약/회귀/스모크/통합)와 존재 이유를 밝힌다 — "깨지면 무엇이 잘못된 것인가".
- 테스트 이름은 한글 백틱으로 행위·기대를 서술한다: `` fun `장바구니 저장 후 재조회하면 담긴 품목이 복원된다`() ``.
- 단언에는 실패 이유 메시지를 붙인다.
- import 출처는 테스트 성격 기준: 순수 단위는 `kotlin.test`, 통합(공용 베이스 상속)은 JUnit5. 한 파일에서 섞지 않는다.
- 행위를 고정하되 구현을 고정하지 마라 — 결과(상태·출력)를 검증하고, 상호작용 검증(메서드 호출 횟수)은 원칙적으로 금지.
- 아키텍처 테스트(ArchUnit 등)는 쓰지 않는다 — 모듈 경계는 Gradle 컴파일이 강제한다.

## 8. 예제

- 단위: `domain/src/test/.../UlidTest.kt`
- 통합(스모크): `boot/api/src/test/.../ApiApplicationSmokeTest.kt`
- 유스케이스 통합 골격 — 베이스 상속, 실 커밋, 별도 트랜잭션 재조회:

```kotlin
class CartPersistenceIntegrationTest : IntegrationTestBase() {
    @Test
    fun `장바구니 저장 후 재조회하면 담긴 품목이 복원된다`() {
        val cartId = tx.execute {
            val cart = Cart.create(buyerId = 1L)
            cart.addItem(CartItem.of(optionCombinationId = 10L, quantity = 2))
            em.persist(cart)
            cart.id
        }!!
        tx.execute {
            assertEquals(1, em.find(Cart::class.java, cartId).items.size, "담은 품목 1개가 복원돼야 한다")
        }
    }
}
```

## 9. Future — 도입 시점에 결정할 것들

현재 규칙이 아니다. 해당 기능이 랜딩하는 커밋에서 결정하고 이 절에서 본문으로 승격한다.

- ~~**Outbox/이벤트 검증**~~ → **본문 승격 완료(§3.0, 2026-07-23)**. EmbeddedKafka 채택, Awaitility 배선 완료.
- **Flyway**: `DatabaseCleaner` 이력 테이블 truncate 제외는 완료(2026-07-23). 남은 것 — `ddl-auto=create` 제거, 테스트 스키마 전체를 마이그레이션으로
  생성 + `ddl-auto=validate` 전환(전체 DDL 이관 시점에).
- **MockMvc**: 컨트롤러 통합 착수 시 `spring-boot-webmvc-test-autoconfigure` 배선(Boot 4에서 별도 아티팩트), `@AutoConfigureMockMvc`는 베이스에.
- **픽스처 빌더**: 유스케이스 테스트 착수 시 `OrderFixture.aCart()` 형태의 Object Mother 도입. 그 전까지 도메인 팩토리 직접 호출 허용.
- **인증**: Security가 컨트롤러 통합에 붙으면 `@WithMockUser`/커스텀 시큐리티 테스트 지원 구체화.
- **MockK**: Fake/`@MockitoBean`으로 안 풀리는 상호작용 검증이 실제로 필요해지면 카탈로그 추가.
- ~~**테스트 지원 공유**~~ → **결정 완료(SCRUM-192, 2026-08-10)**. 실행 모듈 2개째(seller-api)부터 통합 테스트 지원
  (IntegrationTestBase·IntegrationTestConfig·DatabaseCleaner·FakeFileStorage)은 `:api`의 **java-test-fixtures**가 소유하고
  소비 모듈은 `testImplementation(testFixtures(project(":api")))`로 공유한다. KafkaIntegrationTestBase는 api 전용(컨슈머가 api에만 있음)이라 api src/test 잔류.
- **MongoDB**: 도입 시 공용 베이스에 `@ServiceConnection` Mongo 컨테이너를 추가한다 — 별도 베이스를 만들지 않는다.
- **병렬 실행**: 테스트별 스키마/DB 분리로 격리가 강화되면 재검토.
- **모듈 간 테스트 베이스 공유**: boot/batch·infra에 통합 테스트가 필요해지는 커밋에서 결정한다 — 위 `:api` test-fixtures를 그대로 소비할지, 모듈마다 자체 베이스를 둘지. 공유 장치 자체는 SCRUM-192에서 `:api` java-test-fixtures로 도입됐고, 현재 소비자는 seller-api뿐이다.
- **운영 MySQL 버전 확정 시**: 컨테이너 태그를 그 버전으로 맞춘다.

## 10. PR 전 체크리스트

- [ ] 순수 로직은 스프링 없는 단위로, 배선은 boot 통합으로 갔는가
- [ ] 통합 테스트가 그 모듈의 베이스를 상속하는가 (boot/api면 `IntegrationTestBase` / `KafkaIntegrationTestBase`. 개별 properties/@MockitoBean/자체 컨테이너 없음)
- [ ] 이벤트/커밋 side-effect 테스트에 `@Transactional`을 붙이지 않았는가
- [ ] 저장 검증을 별도 트랜잭션 재조회로 했는가
- [ ] KDoc 첫 줄 정체 표기·한글 백틱 이름·단언 메시지가 있는가
