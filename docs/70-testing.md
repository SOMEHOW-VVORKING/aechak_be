# 테스트 전략 (70-testing)

> - 테스트 코드를 작성/수정할 때 `00-overview` + 이 문서 + 해당 모듈 문서를 투입한다. 테스트 규칙의 단일 출처이며, 모듈 문서는 테스트 규칙을 중복 서술하지 않는다.
> - 스택: Spring Boot 4.0.5 / Kotlin 2.2 / JDK 21. 실 DB = MySQL 8.x, 통합 테스트 DB = Testcontainers MySQL — 로컬·CI 모두 Docker 필요.
> - 공용 베이스 실물: `boot/api/src/test/kotlin/com/aechak/api/support/` (IntegrationTestBase · IntegrationTestConfig · DatabaseCleaner). 새 통합 테스트를 짜기 전에 원본을 먼저 읽는다.

## 0. 핵심 요약 — 이것만 기억한다

1. 순수 로직은 domain/common **단위 테스트**, 배선이 필요하면 boot **통합 테스트**.
2. 통합 테스트는 `IntegrationTestBase` **상속이 전부다** — 컨텍스트 하나, MySQL 컨테이너 하나를 전원이 공유한다.
3. 격리는 롤백이 아니라 **커밋 + truncate**.
4. 금지: **H2 · `@DirtiesContext` · 클래스별 properties/`@MockitoBean` · 자체 `@Testcontainers` · 이벤트 테스트에 `@Transactional` · 리포지토리 mock + verify**.
5. 목표는 커버리지 숫자가 아니라 **"깨지면 안 되는 계약이 테스트로 못 박혀 있는가"**다.

---

## 1. 전략

**규칙**: 통합 우선(integration-first)을 표준으로 채택한다. 순수 로직만 단위로 내리고, 나머지는 boot에서 실 배선(실 레포 + MySQL 컨테이너)으로 검증한다.

**근거**: 이 시스템의 위험한 버그는 배선 쪽에 몰려 있다 — JPA 매핑, 트랜잭션 경계, AFTER_COMMIT 이벤트, MySQL 고유 SQL(SKIP LOCKED, 조건부 원자 UPDATE). 이 부류는 mock으로 검증이 불가능하다. 트레이드오프(느린 CI, 실패 국소화 저하)는 알고 감수한다.

**재검토 트리거**: (a) 통합 스위트 로컬 벽시계 3분 초과, (b) 한 회귀에 통합 테스트 3개 이상 동시 레드. 관찰되면 슬라이스/단위 비중을 늘리는 방향으로 재검토한다.

| 계층 | 무엇을 | 어디서 | 도구 |
| --- | --- | --- | --- |
| domain/common 단위 | 불변식·계산·상태 전이 | `{domain,common}/src/test` | `kotlin.test`, 스프링 없음 |
| 유스케이스·매핑·이벤트 통합 | 실 배선 검증 | `boot/api/src/test` | JUnit5 + 공용 베이스 |
| 부팅 스모크 | 매핑·컨텍스트 안전망 | `boot/*/src/test` | 공용 베이스(contextLoads) |
| 외부 클라이언트 | 계약 검증 | `infra/client/*/src/test` | stub 허용(§5) |

## 2. 단위 테스트 (domain / common)

**규칙**: 도메인 규칙(불변식·계산·상태 전이·값 객체 검증)은 `kotlin.test`로 스프링 없이 검증한다. 이 모듈들엔 스프링이 클래스패스에 없어 구조적으로 강제된다.

**예외**: 레포지토리·시간·랜덤 같은 협력자가 필요해지면 단위 대상이 아니다 — 통합으로 올린다. 통합으로 검증하기 지나치게 무거운 순수 분기 로직에 한해 domain 포트의 In-Memory Fake로 application 단위 테스트를 허용한다(남용 금지).

역방향도 금지다: 순수 계산을 `@SpringBootTest`로 검증하지 않는다(부팅 비용 낭비 + 실패 국소화 저하).

## 3. 통합 테스트 (boot)

**규칙**: 모든 통합 테스트는 `IntegrationTestBase`를 상속한다. 현재 프로젝트는 이 단일 베이스 방식을 표준으로 채택한다. 개별 테스트 클래스에서 `@SpringBootTest(properties=...)`·`@MockitoBean`·`@DirtiesContext`·자체 `@Testcontainers`/`@Container`를 선언하지 않는다.

**근거**: Spring 테스트 컨텍스트 캐시는 프로퍼티·bean override·`@AutoConfigure*` 등 설정 전부를 캐시 키에 넣는다. 클래스마다 설정이 조금이라도 다르면 컨텍스트가 갈라져 부팅이 반복되고, 갈라진 컨텍스트가 같은 DB에 DDL을 다시 실행한다. 전원이 같은 베이스를 상속하면 부팅은 JVM당 1회로 상수화된다. 캐시 논리 설명은 이 절에만 둔다 — 다른 절의 금지 규칙들은 전부 여기로 귀결된다.

**예외**: 정말 다른 설정이 필요하면 런타임 토글로 우회 가능한지 먼저 보고(예: Hibernate statistics는 `isStatisticsEnabled` 런타임 토글), 불가피할 때만 명시적 2번째 베이스를 만들어 여러 클래스가 재사용한다. 2번째 베이스를 만드는 커밋에서는 컨테이너를 빈 수명에서 분리(수동 start)하는 전환을 함께 한다 — 빈으로 등록된 컨테이너는 컨텍스트가 닫힐 때 함께 멈출 수 있다.

세부:
- **컨트롤러 통합**은 MockMvc로 HTTP 계층까지 검증한다. `@AutoConfigureMockMvc`는 개별 클래스가 아니라 베이스에 붙인다(캐시 키 참여). `webEnvironment=RANDOM_PORT`는 쓰지 않는다.
- **부팅 스모크**(`contextLoads`)는 전 엔티티 매핑 검증 + 스키마 생성을 겸하는 최소 안전망이다. 공용 베이스 위라 매핑 검증도 실 MySQL 기준이다.
- **`@DataJpaTest` 슬라이스는 쓰지 않는다** — 별도 컨텍스트를 만들어 단일 컨텍스트를 분할하고 얻는 게 적다.
- **매핑 SQL 회귀 테스트는 상시로 두지 않는다** — 컬렉션 매핑의 SQL 특성은 엔티티 셋업 시점에 실측으로 검증을 마쳤고, SQL 문장 수 고정 단언은 배칭·채번·Hibernate 버전에 커플링돼 유지비가 이득을 넘는다. 이후 N+1 등 SQL 개수가 계약인 테스트를 만들게 되면: 컬렉션 액션 UPDATE는 `entityUpdateCount`에 안 잡히므로 `prepareStatementCount`로 세고, 전제(배칭·채번·fetch)를 KDoc에 명시한다.

## 4. 격리와 정리

**규칙**: 기본 격리 = 커밋 + `@BeforeEach` 전 테이블 truncate(베이스가 수행). 테스트는 `tx.execute { }`로 실제 커밋한다.

**근거**: 이 아키텍처는 실 커밋과 `@TransactionalEventListener(AFTER_COMMIT)` 관찰이 필요하다. `@Transactional` 롤백 테스트에서는 커밋이 없어 AFTER_COMMIT 리스너가 발화하지 않고, 이벤트/outbox 흐름이 조용히 미검증 상태로 통과한다.

**예외**: 커밋 의미가 불필요한 순수 매핑/조회 테스트에만 `@Transactional`(롤백)을 명시적으로 허용한다. 단 그 경우에도 `@BeforeEach`의 TRUNCATE는 MySQL 암묵 커밋을 유발하므로 롤백 격리에 정밀한 의미를 싣지 않는다.

함정 목록:
- **1차 캐시 가짜 통과**: save 후 같은 트랜잭션에서 조회하면 DB가 아니라 영속성 컨텍스트가 답한다. 저장 검증은 커밋 후 별도 트랜잭션에서 재조회(또는 flush+clear)한다.
- **빈 테이블 가정은 클리너 위에서만**: 격리의 근거는 언제나 truncate다. 클리너를 우회하는 테스트에서 "이 id는 없겠지" 식 전제를 깔지 않는다. 유니크 제약 컬럼은 한 테스트 안에서도 중복 주의.
- **병렬 실행 OFF**: 공유 컨테이너 + 커밋 기반이라 클래스 병렬은 데이터 경합을 낳는다. `junit.jupiter.execution.parallel.enabled`를 켜지 않는다.

## 5. 테스트 더블

**규칙**: 기본은 진짜(실 레포·실 배선·실 MySQL)다. 외부 경계(PG·SMS 등)만 In-Memory Fake를 `IntegrationTestConfig`에 한 벌 등록해 전 테스트가 공유한다.

**근거**: 클래스별 `@MockitoBean`은 컨텍스트를 가른다(§3). 리포지토리를 mock하고 `verify { save() }`로 끝내는 테스트는 배선을 검증하지 못하므로 금지 — 유스케이스는 실 레포로 결과를 확인한다.

**예외**: 실제 외부 계약(PG 응답 스펙 등)은 Fake가 아니라 `infra/client`의 계약 테스트가 잡는다. 시간·랜덤이 로직에 들어오면 `Clock`/시드 랜덤을 주입 가능한 빈으로 설계하고 테스트에서 고정한다.

## 6. 통합 테스트 DB = Testcontainers MySQL

**규칙**: 통합 테스트는 운영과 같은 엔진(MySQL 8.x)의 컨테이너 위에서 돈다. H2 등 인메모리 DB는 test 클래스패스에조차 두지 않는다.

**근거**: 방언·락·에러 코드가 다른 DB로 통과한 테스트는 "테스트 그린, 프로덕션 레드"를 만든다. 이 시스템엔 그 부류(SKIP LOCKED, unique 위반 catch 멱등, 조건부 원자 UPDATE)가 처음부터 있다. H2가 클래스패스에 있으면 배선 실수가 조용히 H2로 폴백해 통과하므로 물리적으로 제거한다. 컨테이너 기동은 단일 컨텍스트 덕에 JVM당 1회라 비용이 상수다.

세부:
- 이미지 태그는 운영 DB 버전으로 고정한다(`latest` 금지). 현재 태그 값은 코드가 들고 있다.
- 스키마는 지금 `ddl-auto=create`로 만든다(ERD 확정 전 과도기). CI는 GitHub Actions ubuntu 러너에 Docker가 내장이라 추가 설정이 없다.
- 알려진 예외: `boot/batch`는 ERD 확정 전까지 임시로 H2를 쓴다. batch에 테스트가 착수되는 커밋에서 제거하고 같은 패턴으로 정렬한다 — 그 전까지 batch 테스트 작성 금지.

## 7. 스타일

- 클래스 KDoc 첫 줄에 테스트 정체(계약/회귀/스모크/통합)와 존재 이유를 밝힌다 — "깨지면 무엇이 잘못된 것인가".
- 테스트 이름은 한글 백틱으로 행위·기대를 서술한다: `` fun `장바구니 저장 후 재조회하면 담긴 품목이 복원된다`() ``.
- 단언에는 실패 이유 메시지를 붙인다.
- import 출처: domain/common은 `kotlin.test`, boot는 JUnit5. 섞지 않는다.
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

- **Outbox/이벤트 검증** (outbox 코드 랜딩 시 필수): 성공 시 도메인 행 + outbox 행 동반 커밋, 실패 주입 시 둘 다 롤백을 직접 조회로 단언. 컨슈머 멱등성은 브로커 없이 리스너 직접 호출로. AFTER_COMMIT 부수효과는 커밋 기반 + Awaitility(카탈로그 배선 필요) 대기로. relay 폴링·SKIP LOCKED 경합은 공용 MySQL 컨테이너에서 그대로 검증 가능. 브로커 왕복이 필요하면 EmbeddedKafka 또는 Testcontainers Kafka 택1.
- **Flyway**: 도입 시 `ddl-auto=create` 제거, 테스트 스키마도 마이그레이션 스크립트로 생성 + `ddl-auto=validate`로 엔티티-스키마 드리프트 감지. `DatabaseCleaner`에 마이그레이션 이력 테이블 truncate 제외 추가.
- **MockMvc**: 컨트롤러 통합 착수 시 `spring-boot-webmvc-test-autoconfigure` 배선(Boot 4에서 별도 아티팩트), `@AutoConfigureMockMvc`는 베이스에.
- **픽스처 빌더**: 유스케이스 테스트 착수 시 `OrderFixture.aCart()` 형태의 Object Mother 도입. 그 전까지 도메인 팩토리 직접 호출 허용.
- **인증**: Security가 컨트롤러 통합에 붙으면 `@WithMockUser`/커스텀 시큐리티 테스트 지원 구체화.
- **MockK**: Fake/`@MockitoBean`으로 안 풀리는 상호작용 검증이 실제로 필요해지면 카탈로그 추가.
- **MongoDB**: 도입 시 공용 베이스에 `@ServiceConnection` Mongo 컨테이너를 추가한다 — 별도 베이스를 만들지 않는다.
- **병렬 실행**: 테스트별 스키마/DB 분리로 격리가 강화되면 재검토.
- **운영 MySQL 버전 확정 시**: 컨테이너 태그를 그 버전으로 맞춘다.

## 10. PR 전 체크리스트

- [ ] 순수 로직은 domain/common 단위로, 배선은 boot 통합으로 갔는가
- [ ] 통합 테스트가 `IntegrationTestBase`를 상속하는가 (개별 properties/@MockitoBean/자체 컨테이너 없음)
- [ ] 이벤트/커밋 side-effect 테스트에 `@Transactional`을 붙이지 않았는가
- [ ] 저장 검증을 별도 트랜잭션 재조회로 했는가
- [ ] KDoc 첫 줄 정체 표기·한글 백틱 이름·단언 메시지가 있는가
