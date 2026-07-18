# 테스트 전략 — 전역 규칙 (70-testing)

> **문서 사용법**
> - 테스트 코드를 작성/수정하는 작업 시: `00-overview` + 이 문서 + 해당 모듈 문서를 투입한다.
> - 이 문서는 **"무엇을 어디서 어떻게 테스트하는가"의 단일 출처**다. 모듈 문서는 테스트 규칙을 중복 서술하지 않는다(중복 금지 원칙 — 00 §서문).
> - 전략 한 줄: **통합 우선(integration-first).** 순수 로직만 단위 테스트, 나머지는 boot 모듈에서 **공용 베이스 컨텍스트 + 실 레포 + Testcontainers(MySQL)**로 검증한다.
> - `⚠️ PENDING` 마커는 아직 도입하지 않은 도구/규칙이다 — 명시된 앵커 시점에 결정하고 마커를 치환한다.
> - **스택 고정 사실**: Spring Boot 4.0.5 / Kotlin 2.2 / JDK 21. 이벤트 드리븐(Kafka + Transactional Outbox, `@TransactionalEventListener(AFTER_COMMIT)`). 실 관계형 DB = **MySQL 8.x**. 통합 테스트 DB = **Testcontainers MySQL**(운영 패리티) — 로컬·CI에 Docker 필요. MongoDB(고쓰기 콘텐츠)는 후속 — §9.5.

---

## 1. 전역 원칙

1. **모든 테스트는 자기 정체를 밝힌다.** 클래스 KDoc 첫 줄에 이 테스트가 **계약(contract) / 회귀(regression) / 스모크(smoke) / 통합(integration)** 중 무엇이며 **왜 존재하는가**를 적는다. "이게 깨지면 무엇이 잘못된 것인가"를 남긴다. (정체가 겹치면 "회귀(통합)"처럼 병기한다.)
2. **테스트 이름은 한글 백틱**으로 행위·기대를 서술한다. `fun \`자식 2개 저장은 INSERT 3개로만 — 잉여 FK UPDATE 없음\`()`.
3. **행위를 고정하되 구현을 고정하지 마라.** 결과(상태·출력)를 검증한다. "어떤 메서드가 몇 번 불렸다"류 상호작용 검증은 원칙적으로 금지(§6 예외 참조).
4. **커버리지 숫자를 목표로 삼지 않는다.** 목표는 "깨지면 안 되는 계약이 테스트로 못 박혀 있는가"다.
5. **아키텍처 테스트(ArchUnit 등)는 쓰지 않는다** (00 §서문). 모듈 경계는 Gradle 컴파일이 강제하고, 같은 모듈 내 패키지 규칙은 코드 리뷰로 지킨다.
6. **import 출처를 계층에 맞춘다** — domain/common은 `kotlin.test`(`kotlin.test.Test`, `assertEquals`), boot는 JUnit5(`org.junit.jupiter.api.Test`). 섞지 않는다.

---

## 2. 계층별 전략 (핵심 표)

| 계층 | 테스트 종류 | 어디서 | 도구 | Spring | DB |
| --- | --- | --- | --- | --- | --- |
| common | 순수 단위 (규약·유틸) | `common/src/test` | `kotlin.test` | ✕ | ✕ |
| domain | 순수 단위 (불변식·계산·계약) | `domain/src/test` | `kotlin.test` | ✕ | ✕ |
| **유스케이스·통합** | **통합 (실 배선) ← 주력** | **`boot/api/src/test`** | JUnit5 + 공용 베이스(§3) | ✓ | MySQL(TC) |
| 이벤트·Outbox | 통합 (커밋 기반) | `boot/api/src/test` | 공용 베이스 + 실 커밋 | ✓ | MySQL(TC) |
| 부팅 안전망 | 스모크 | `boot/*/src/test` | 공용 베이스(contextLoads) | ✓ | MySQL(TC) |
| 외부 클라이언트 | 계약/상호작용 | `infra/client/*/src/test` | stub 허용 (§6) | 상황별 | ✕ |

> **강제 vs 선택 (정직하게)**: domain/common은 **구조상 순수 단위만 가능**하다 — 이 모듈들엔 Spring이 클래스패스에 없어 `@SpringBootTest`를 쓸 수 없다(강제). boot 계층에서는 슬라이스(`@DataJpaTest`)·Facade 단위 테스트도 기술적으로 가능하나, **우리는 회귀 내성을 위해 풀 통합을 *선택*한다**(트레이드오프: 느린 CI·실패 국소화 저하를 감수). 슬라이스를 미루는 이유는 §5.4.
>
> **DB 계층 구분이 없는 이유**: 우리 전략엔 "방언 무관 슬라이스" 계층이 없다 — DB를 만지는 테스트는 전부 통합이고, 통합은 전부 운영과 같은 MySQL(Testcontainers) 위에서 돈다. 따라서 H2는 쓰지 않는다(§9).
>
> **20 §4의 "Facade 단위 테스트" 이점과의 관계**: application 모듈엔 `spring-boot-test`가 없어 자체적으로 통합 테스트를 못 한다. Facade 로직은 boot/api 통합으로 검증하는 것이 기본이다. 순수 계산이 무거운 복잡 분기에 한해 §6의 Fake 단위 테스트를 예외로 둔다.

> **전략 재검토 트리거 (교조화 방지)**: 다음이 관찰되면 슬라이스/단위 비중을 늘리는 방향으로 이 전략을 재검토한다 — (a) 통합 스위트 벽시계 시간이 팀 합의 임계(예: 로컬 3분)를 넘음, (b) 한 유스케이스 회귀에 통합 테스트 3개 이상이 동시에 빨개짐(실패 국소화 실패 신호).

---

## 3. 공용 베이스 컨텍스트 (통합의 토대 — 1급 규칙)

**모든 통합 테스트는 단 하나의 공용 베이스를 상속한다.** 이것이 컨텍스트 캐싱·싱글턴 컨테이너·외부 경계 대체를 **동시에** 성립시키는 유일한 방법이다.

3.1 **왜 "단일 변형"이어야 하나.** Spring은 테스트 컨텍스트 캐시 키에 `@MockitoBean` override·`@TestConfiguration`·프로퍼티·프로파일·`webEnvironment`·`@AutoConfigure*`를 **전부 포함**한다. 즉 PG 클라이언트·Clock·random을 대체하는 *모든* 수단이 캐시 키를 바꿔 새 컨텍스트를 포크한다. 포크의 비용은 둘이다 — 컨텍스트 재부팅 시간, 그리고 **포크된 컨텍스트가 같은 DB에 `ddl-auto` DDL을 다시 실행**하는 것(컨테이너는 companion 싱글턴이라 재기동되지 않지만 스키마는 공유다). 해법은 반대다 — **MySQL 컨테이너·가짜 외부 경계·고정 Clock·시드 random을 베이스에 한 번 박고, 전 테스트가 그 하나를 공유**한다.

3.2 **공용 베이스 실물** — `boot/api/src/test/kotlin/com/aechak/api/support/` 3개 파일이 SoT다. 새 통합 테스트를 짜기 전에 반드시 원본을 읽는다:
   - `IntegrationTestConfig` — `@ServiceConnection` 싱글턴 MySQL 컨테이너(companion 단일 인스턴스, `mysql:8.4` 태그 고정). 가짜 외부 경계(PG·Clock·random)는 어댑터가 생기는 커밋에서 여기에 등록한다.
   - `IntegrationTestBase` — 모든 통합 테스트가 상속하는 **유일한 베이스**. `@SpringBootTest`(MOCK 환경 — §5.2) + `@Import(IntegrationTestConfig)` + `@BeforeEach` truncate(§4). `ddl-auto=create`는 ERD 확정 전 과도기 — 마이그레이션 도입 시 제거(§9.2).
   - `DatabaseCleaner` — FK 검사 끄고 전 테이블 truncate를 단일 커넥션에서 실행. `IntegrationTestConfig`가 빈으로 등록하고 베이스가 주입받아 쓴다.

   골격 요지 (TC 2.x — 제네릭 없는 `org.testcontainers.mysql.MySQLContainer`, 구 1.x `org.testcontainers.containers`와 다름):

```kotlin
@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestConfig {
    companion object { private val mysql = MySQLContainer("mysql:8.4") }  // 싱글턴 — JVM당 1회 기동

    @Bean @ServiceConnection      // datasource 배선 자동 — @DynamicPropertySource 불필요
    fun mysqlContainer(): MySQLContainer = mysql
}

@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create"])   // 과도기(§9.2)
@Import(IntegrationTestConfig::class)
abstract class IntegrationTestBase { /* em·tx·@BeforeEach truncate — 실물 참조 */ }
```

3.3 **금지 (베이스가 아닌 개별 테스트 클래스에서)**: `@SpringBootTest(properties=[...])` 직접 선언, 클래스별 `@MockitoBean`, `@DirtiesContext`(캐시 강제 축출), **자체 `@Testcontainers`/`@Container` 선언**(공용 컨테이너를 놔두고 새 컨테이너를 띄움). 컨텍스트를 바꿔야 할 진짜 이유가 생기면 우선 **런타임 토글로 해결 가능한지** 본다(SQL 통계가 그 예 — §5.1). 정말 프로퍼티 변형이 필요하면 **명시적 2번째 베이스**를 만들어 여러 클래스가 재사용하게 한다. 현재 베이스는 `IntegrationTestBase` 하나뿐이다.
   > ⚠️ **2번째 베이스를 만드는 순간의 함정**: 빈으로 등록된 컨테이너는 **컨텍스트 close/캐시 축출 시 stop될 수 있다**. companion 싱글턴은 중복 기동만 막을 뿐 stop을 막지 못하므로, 컨텍스트가 2개가 되면 "한쪽 컨텍스트가 닫히며 공유 컨테이너를 죽이는" 시나리오가 열린다. 2번째 베이스 도입 커밋에서 컨테이너를 빈 수명에서 분리(수동 `start()` + `@DynamicPropertySource` 배선)하는 전환을 함께 한다.

3.4 **의존성** (`libs.versions.toml` 등록·boot/api testImplementation 배선 완료 — 전부 Boot BOM이 버전 관리): `spring-boot-testcontainers`·`org.testcontainers:testcontainers-mysql`(**2.x부터 아티팩트명이 `mysql`에서 개명됨**). JDBC 드라이버 `com.mysql:mysql-connector-j`는 메인 runtimeOnly에 이미 있어 test 배선 불필요. `org.testcontainers:junit-jupiter`는 쓰지 않는다 — `@Testcontainers`/`@Container` 자체가 금지(§3.3)라 불필요. **H2는 test 클래스패스에 두지 않는다** — 클래스패스에 있으면 배선 실수가 조용히 H2로 폴백해 통과한다(§9).

> **주의**: `@MockBean`은 Spring Boot 3.4 deprecated → **4.0에서 삭제**됐다. 이 레포(Boot 4.0)에선 `org.springframework.test.context.bean.override.mockito.@MockitoBean`을 쓴다. 단 위 방침상 클래스별 mock 자체가 예외다(§6).

---

## 4. 테스트 격리·정리 (1급 규칙)

**기본 격리 = 커밋 + truncate.** 이 아키텍처는 실 커밋과 `AFTER_COMMIT`(outbox relay) 관찰이 필요하므로 `@Transactional` 롤백을 기본으로 쓰지 않는다.

4.1 **정리는 공용 베이스의 `@BeforeEach` truncate로** 한다(§3.2). 커밋된 데이터가 테스트 간 번지지 않게 각 테스트 시작 시 청소한다. MySQL에선 `SET FOREIGN_KEY_CHECKS=0` → 전 테이블 `TRUNCATE` → `=1` 순서를 **한 커넥션에서** 실행한다(FK 검사 설정이 커넥션 단위라 갈라지면 풀이 오염된다). 컨테이너는 살리고 데이터만 지운다 — 컨테이너 재기동으로 정리하지 않는다.

4.2 **`@Transactional`(롤백)은 예외로만.** 커밋 의미가 불필요한 **순수 매핑/조회 테스트에만** 명시적으로 붙인다.
   > ⚠️ **경고**: 테스트에 `@Transactional`을 붙이면 커밋이 안 일어나 `@TransactionalEventListener(AFTER_COMMIT)`가 **발화하지 않는다** → 이벤트/outbox 흐름을 조용히 미검증한다. 이벤트·커밋 side-effect를 보는 테스트엔 절대 붙이지 않는다.
   > 또 하나: `@Transactional` 테스트에서도 `@BeforeEach`의 `TRUNCATE`는 MySQL **암묵 커밋**을 유발한다 — "전부 롤백되는 깨끗한 트랜잭션"이라는 직관을 truncate 지점이 한 번 끊는다. 롤백 격리에 정밀한 의미를 싣지 마라.

4.3 **빈 테이블을 클리너 없이 가정하지 않는다.** 격리의 근거는 언제나 §4.1 truncate다. 하드코딩 값 자체(`buyerId = 1L`)는 클리너 격리 위에서 허용되지만, **클리너를 우회하는 테스트(§4.2 롤백 등)에서 "이 id는 없겠지" 식 count 전제를 깔면** 다른 테스트의 커밋이 오염시키는 순간 깨진다. 유니크 제약 컬럼(예: `carts.buyer_id`)은 한 테스트 안에서도 중복 주의.

4.4 **병렬 실행은 지금 OFF.** 공유 컨테이너 + 커밋 기반이라 클래스 병렬은 데이터 경합을 낳는다. 누구도 `junit.jupiter.execution.parallel.enabled`를 켜지 않는다. `⚠️ PENDING`: 격리가 테스트별 스키마/DB 분리로 강화되면 클래스 병렬을 재검토한다.

4.5 **JPA 1차 캐시 함정 회피.** `save` 후 같은 트랜잭션에서 조회하면 DB가 아니라 영속성 컨텍스트가 답한다(가짜 통과). 저장을 검증할 땐 **`flush()` + `clear()` 후 재조회**하거나, 커밋 후 **별도 트랜잭션에서 조회**한다.

---

## 5. 통합 테스트 세부 규칙

5.1 **매핑·SQL 회귀 테스트는 상시로 두지 않는다.** 컬렉션 매핑(단방향 `@OneToMany` + `updatable=false`)의 SQL 특성은 엔티티 셋업 시점에 실측으로 검증을 마쳤다. SQL 문장 수를 고정하는 테스트는 배칭·채번·Hibernate 버전에 커플링돼 유지비가 이득을 넘어서므로 제외했고, 매핑 규칙 준수는 코드 리뷰로 지킨다.
   - 이후 SQL 개수 자체가 계약인 테스트(N+1 등)를 도입하게 되면: 엔티티 레벨 카운트를 1순위로 쓰되, **컬렉션 액션 UPDATE(단방향 @OneToMany의 FK write/null-out)는 `entityUpdateCount`에 잡히지 않는다** — 그 부류는 `prepareStatementCount`로 세고 전제(배칭·채번·fetch)를 KDoc에 명시한다.
   - `generate_statistics` **프로퍼티 변형은 만들지 않는다** — 켜야 하면 런타임 토글(`sessionFactory.statistics.isStatisticsEnabled = true`)로 켠다. 프로퍼티로 켜면 별도 컨텍스트가 생겨 §3 단일 컨텍스트가 깨진다.

5.2 **컨트롤러 통합은 MockMvc로.** `@AutoConfigureMockMvc` + `MockMvc`로 HTTP 계층(`@Valid`, 에러 응답 규격 — 05 문서)까지 검증한다. `⚠️ PENDING`: Boot 4.0은 MockMvc 테스트 지원이 별도 아티팩트(`spring-boot-webmvc-test-autoconfigure`)로 분리됐다 — 컨트롤러 통합 착수 시 카탈로그에 배선하고, **`@AutoConfigureMockMvc`는 개별 클래스가 아니라 `IntegrationTestBase`에 붙인다**(`@AutoConfigure*`도 캐시 키에 참여 — §3.1). **`webEnvironment=RANDOM_PORT`는 쓰지 않는다** — 컨텍스트 캐시 키를 바꿔 §3의 단일 컨텍스트를 깬다. 실제 서블릿 왕복이 꼭 필요한 소수 케이스만 예외로 별도 베이스.

5.3 **부팅 스모크는 최소 안전망.** `contextLoads()` 하나로 전 엔티티 Hibernate 매핑 검증 + 스키마 생성이 돈다(`ApiApplicationSmokeTest.kt`). 컴파일로 안 잡히는 매핑 오류(`@MapsId`, `@JoinColumn`, UNIQUE)를 잡는다. 공용 베이스 위에서 돌므로 **매핑 검증도 실 MySQL 방언 기준**이다.

5.4 **@DataJpaTest는 미룬다(PENDING, 근거 명시).** 슬라이스는 **별도 컨텍스트 캐시 엔트리**를 만들어 §3 단일 컨텍스트를 분할하고, 공유 `@SpringBootTest` 대비 얻는 게 적다. 도입하게 되면 내장 DB 자동 대체를 끄도록 `@AutoConfigureTestDatabase(replace=NONE)`를 반드시 명시한다(안 끄면 조용히 인메모리 DB로 되돌아가 §9를 우회한다).

---

## 6. 테스트 더블 규칙

기본 전제: **통합 우선이라 대부분 진짜(실 레포·실 배선·실 MySQL)를 쓴다. 더블은 예외다.**

6.1 **외부 경계는 공용 베이스의 Fake로 고정.** PG 클라이언트·SMS 본인인증 등 외부 어댑터는 In-Memory Fake 구현체를 `IntegrationTestConfig`(§3.2)에 **한 벌** 등록해 전 테스트가 공유한다. 클래스별 `@MockitoBean` 주입은 캐시를 깨므로 금지.
   - 실제 외부 계약 위반(PG 응답 스펙 등)은 이 Fake가 아니라 **`infra/client`의 계약 테스트**(§2 표)가 잡는다 — 책임 분리.

6.2 **시간·랜덤은 주입 가능한 경계로.** `java.time.Clock`을 Spring 빈으로 등록하고, 테스트 베이스에선 `Clock.fixed`로 치환한다(§3.2). random은 시드 고정 빈으로.
   > `⚠️ PENDING`: 현재 코드에 `Clock` 사용처가 없다(정산 D+N·주문 10분 TTL·재고 예약 등 시간 로직 구현 시점에 도입). static `Ulid.generate()`는 통제 불가하므로 **ID 값 자체는 단언하지 않는다**(형식·유일성만 — `UlidTest` 참조).

6.3 **금지**: 리포지토리를 mock한 뒤 `verify { save() }`로 끝내는 테스트. 유스케이스 검증은 실 레포 + MySQL로 결과를 확인한다.

6.4 **Fake 포트(In-Memory)**: 통합으로 검증하기 지나치게 무거운 복잡 분기 로직에 한해 domain 포트를 In-Memory로 구현해 application 단위 테스트로 쓸 수 있다. 남용 금지 — 기본은 통합이다.

6.5 **MockK**: `⚠️ PENDING`. 현재 카탈로그에 없다. 위 Fake/`@MockitoBean`으로 안 풀리는 순수 상호작용 검증이 실제로 필요해지면 `libs.versions.toml`에 추가하고 마커를 치환한다.

---

## 7. 테스트 데이터 빌더 (픽스처 규약)

- `⚠️ PENDING`: 통합 테스트의 데이터 셋업은 **Object Mother / Test Data Builder**로 통일한다: `com.aechak.api.fixture.OrderFixture.aCart()` 형태. 도메인 팩토리(`Cart.create`, `CartItem.of`)를 재사용하되 테스트 전용 기본값을 채운다. **유스케이스 테스트가 착수되는 커밋에서 도입** — 그 전까지는 도메인 팩토리 직접 호출을 허용한다(현 §12 예제가 그 형태).
- 각 세션이 셋업 코드를 새로 짜지 않는다 — 스키마 변경 시 빌더 한 곳만 수정. 과설계 금지("빌더를 위한 빌더" 지양).

---

## 8. 이벤트 · Outbox 정합 (아키텍처 중추)

이 시스템은 Kafka + Transactional Outbox + `@TransactionalEventListener(AFTER_COMMIT)`를 코어로 쓴다(00 §3-2, 20 §3, 60 §1). 아래는 **outbox/이벤트 코드가 랜딩하는 순간 blocker로 필수**가 된다(현재는 미구현 — 규칙만 선반영).

8.1 **Outbox 원자성 회귀.** outbox를 쓰는 유스케이스는: 성공 시 (도메인 행 + `outbox_events` 행) **동반 커밋**, **실패 주입 시 둘 다 롤백**을 outbox 테이블 직접 조회로 단언한다. "롤백인데 발행됨"(BEFORE_COMMIT/직접 발행 오배선) 고전 버그를 이 테스트가 차단한다.

8.2 **컨슈머 멱등성.** 동일 메시지 2회 처리 → 부수효과 1회 + `processed_events` 1행. 컨슈머는 boot 소속이라(40 §2) **브로커 없이 리스너 메서드 직접 호출**로 테스트한다.

8.3 **프로세스 내 이벤트 발행 사실**은 `@RecordApplicationEvents` + `ApplicationEvents` 조회로 검증한다(컨텍스트 캐시 안 깸).

8.4 **AFTER_COMMIT 부수효과**는 커밋 기반 테스트(§4) + `Awaitility`(`⚠️ PENDING` — outbox 착수 시 카탈로그 배선)로 리스너 완료를 대기해 검증한다. `@Transactional` 롤백 테스트로는 못 잡는다(§4.2).

8.5 **relay 폴링·경합도 실 DB로 검증 가능.** MySQL 8은 `SELECT ... FOR UPDATE SKIP LOCKED`를 지원하므로 relay 폴링과 동시 relay 경합을 **공용 컨테이너(MySQL) 위에서 그대로 검증**한다 — H2였다면 불가능했을 부류다(§9). 브로커 왕복 end-to-end가 필요하면 `spring-kafka-test` EmbeddedKafka 또는 Testcontainers Kafka(`⚠️ PENDING`: Kafka 어댑터 착수 시 택1).

---

## 9. 통합 DB = Testcontainers MySQL (1급 규칙)

**통합 테스트는 운영과 같은 엔진(MySQL 8.x) 컨테이너 위에서 돈다. H2는 쓰지 않는다.**

> **근거**: 운영 DB와 다른 인메모리 DB로 통과한 테스트는 방언·락·에러 코드 차이로 "테스트 그린 → 프로덕션 레드"(false confidence)를 만든다. 이 MVP엔 그 부류(SKIP LOCKED relay, unique 위반 catch 멱등, 재고 조건부 원자 UPDATE — 00 §3-5, 네이티브 검색)가 처음부터 포함돼 있다. Spring Boot 3.1+가 `@ServiceConnection`으로 Testcontainers를 공식 1급 지원하고(업계 컨센서스: Thoughtworks Radar Adopt), 우리 단일 컨텍스트 전략(§3) 덕에 컨테이너 기동 비용은 **JVM당 1회(~수 초)**로 상수화된다 — 도입 비용이 유난히 싼 구조다.

9.1 **컨테이너는 §3.2의 싱글턴 하나뿐.** `IntegrationTestConfig`의 `@ServiceConnection` 빈이 유일한 DB다. 테스트 클래스가 자체 컨테이너를 띄우지 않는다(§3.3). 이미지 태그는 고정한다(`mysql:8.4` — `latest` 금지). `⚠️ PENDING`: 운영 MySQL 버전 확정 시 태그를 그 버전으로 맞춘다.

9.2 **스키마는 운영과 같은 경로로.** 지금은 Hibernate `ddl-auto`가 스키마를 만든다(ERD 확정 전 과도기). 마이그레이션 도구(Flyway 등)가 도입되면 통합 테스트도 **실제 마이그레이션 스크립트로 스키마를 만든다** — 마이그레이션 자체가 테스트 대상이 된다. 운영과 다른 스키마 경로를 테스트하지 않는다. 도입 시 `DatabaseCleaner`에 마이그레이션 이력 테이블(`flyway_schema_history` 등) truncate 제외를 함께 넣는다 — 안 넣으면 매 테스트가 재마이그레이션을 유발한다.

9.3 **속도 규율.** 컨테이너 기동은 단일 컨텍스트라 1회뿐이므로 최적화의 초점은 §3(캐시 사수)·§4(정리 비용)다. 로컬 반복 실행이 답답하면 `.withReuse(true)` + `~/.testcontainers.properties`(`testcontainers.reuse.enable=true`)를 **개인 로컬에서만** 써도 된다 — experimental이며 CI에선 금지.

9.4 **CI 전제 = Docker.** GitHub Actions ubuntu 러너는 Docker 내장이라 추가 설정 불필요. 로컬은 Docker Desktop/OrbStack 등 아무 Docker 호환 런타임이면 된다. Docker가 없는 환경에서 통합 테스트는 실행 불가가 정상이다 — H2로 우회하지 않는다.
   > **알려진 예외**: `boot/batch`는 ERD 확정 전 임시로 `runtimeOnly(h2)`를 쓴다. batch에 테스트가 착수되는 커밋에서 H2를 제거하고 api와 같은 패턴(공용 베이스·컨테이너)으로 정렬한다 — 그 전까지 batch 테스트 작성 금지.

9.5 **MongoDB(고쓰기 콘텐츠)**: `⚠️ PENDING`. 도입 시점에 같은 패턴(공용 베이스에 `@ServiceConnection` Mongo 컨테이너 추가)으로 확장한다 — 별도 베이스를 만들지 말고 단일 컨텍스트에 컨테이너만 추가(§3.1).

---

## 10. 순수 단위 테스트 규칙

**대상**: domain/common의 불변식·계산·계약 **만**(값 객체 검증, 금액·수량 계산, 상태 전이, 포맷 계약).

- 스프링을 띄우지 않는다(클래스패스에 없다). 밀리초 단위로 빠르게 유지한다.
- **순수 로직을 `@SpringBootTest`로 검증하지 마라** — 부팅 비용 낭비 + 실패 국소화 저하.
- 외부 협력자(레포·시간·랜덤)가 필요하면 순수 단위 대상이 아니다 → §5 통합으로 올린다.
- 정석: `domain/.../UlidTest.kt`. (파사드 계약 테스트라 static 시계에 의존하는 예외 — §6.2. 일반 도메인 로직은 시계·랜덤을 주입받게 설계한다.)

---

## 11. 인증 통합 (선반영)

컨트롤러 통합에 Spring Security가 붙으면 인증 없는 요청이 401로 막힌다. 인증 컨텍스트에서 `buyerId`를 꺼내는 흐름(30 §toCommand)을 테스트하려면 `@WithMockUser`/커스텀 시큐리티 테스트 지원을 쓴다.
> `⚠️ PENDING`: 소셜 로그인 3종·본인인증은 60 후속 범위. 인증 배선이 들어오는 시점에 이 절을 구체화한다.

---

## 12. 정석 예제 (기존 파일 인용)

새 테스트는 아래 패턴 중 하나를 따른다. **짜기 전에 원본을 먼저 읽는다.**

- **순수 단위 (domain)** → `domain/src/test/kotlin/com/aechak/domain/support/UlidTest.kt`
- **부팅 스모크 (boot)** → `boot/api/src/test/kotlin/com/aechak/api/ApiApplicationSmokeTest.kt`
- **유스케이스 통합 (신규 표준 골격)** — 공용 베이스 상속, 실재 API(`Cart.create`/`CartItem.of`)만 사용, flush/clear 후 재조회:

```kotlin
package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.cart.Cart
import com.aechak.domain.order.cart.CartItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 통합(회귀) — 장바구니에 담긴 품목이 실 레포(JPA→MySQL)에 저장되고 재조회로 복원되는지 검증한다.
 * 배선(엔티티 매핑·영속성)이 깨지면 여기서 잡힌다. 베이스가 truncate로 격리(70 §4), 외부 경계는 베이스 Fake(70 §6.1).
 * (예제는 실재 API만 사용한다 — 미구현 유스케이스 API는 구현 후 이 골격으로 채운다.)
 */
class CartPersistenceIntegrationTest : IntegrationTestBase() {

    @Test
    fun `장바구니 저장 후 재조회하면 담긴 품목이 복원된다`() {
        // 준비+실행: 실 커밋
        val cartId = tx.execute {
            val cart = Cart.create(buyerId = 1L)
            cart.addItem(CartItem.of(optionCombinationId = 10L, quantity = 2))
            em.persist(cart); em.flush()
            cart.id
        }!!

        // 단언: 1차 캐시가 아닌 DB에서 — 새 트랜잭션으로 재조회(70 §4.5)
        tx.execute {
            val found = em.find(Cart::class.java, cartId)
            assertEquals(1, found.items.size, "담은 품목 1개가 복원돼야 한다")
        }
    }
}
```

---

## 13. 하지 말 것 (안티패턴)

- ❌ 리포지토리를 mock하고 `verify`로 끝내기 → §6.3 위반. 실 레포로 결과 검증.
- ❌ 순수 계산·불변식을 `@SpringBootTest`로 검증 → §10 위반. domain 단위로 내려라.
- ❌ 클래스마다 `properties`/`@MockitoBean`/`@DirtiesContext` → §3.3 컨텍스트 캐시 파괴.
- ❌ 테스트 클래스가 자체 `@Testcontainers`/`@Container`로 컨테이너를 따로 띄움 → §9.1. 공용 싱글턴 하나뿐.
- ❌ H2 등 인메모리 DB로 통합 테스트 우회 → §9. false confidence.
- ❌ 이벤트/커밋 side-effect 테스트에 `@Transactional`(롤백) → §4.2. AFTER_COMMIT 미발화로 거짓 통과.
- ❌ `save` 후 같은 트랜잭션에서 조회로 "저장됨" 단언 → §4.5. 1차 캐시가 답하는 가짜 통과.
- ❌ 하드코딩 id로 빈 테이블 가정 / 병렬 실행 켜기 → §4.3·§4.4.
- ❌ `@MockBean` 사용(Boot 4에서 삭제) → §3.3. `@MockitoBean`(단 클래스별 주입 자체가 예외).
- ❌ 이미지 태그 `mysql:latest` / CI에서 `withReuse(true)` → §9.1·§9.3.
- ❌ KDoc 없이 "왜 존재하는지" 불명한 테스트 / 커버리지 채우기용 무의미 테스트 → §1.

---

## 14. PR 전 체크리스트

- [ ] 테스트 종류(계약/회귀/스모크/통합)를 KDoc 첫 줄에 밝혔는가 (§1.1)
- [ ] 순수 로직은 domain/common 단위로, 나머지는 boot 통합으로 갔는가 (§2)
- [ ] 통합 테스트가 `IntegrationTestBase`를 상속해 단일 컨텍스트·공용 컨테이너·truncate 격리를 쓰는가 (§3·§4·§9)
- [ ] 클래스별 `properties`/`@MockitoBean`/`@DirtiesContext`/자체 컨테이너로 캐시를 깨지 않았는가 (§3.3)
- [ ] 이벤트/커밋 side-effect 테스트에 `@Transactional` 롤백을 붙이지 않았는가 (§4.2)
- [ ] 저장 검증에 flush/clear 후 재조회(또는 새 트랜잭션)를 썼는가 (§4.5)
- [ ] 데이터 셋업을 fixture 빌더로 했는가, 하드코딩 id로 빈 테이블을 가정하지 않았는가 (§7·§4.3)
- [ ] 단언에 실패 이유 메시지를 붙였는가 / 한글 백틱 이름인가 (§1.2)
