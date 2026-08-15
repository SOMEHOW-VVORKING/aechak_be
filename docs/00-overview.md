# 애착 백엔드 아키텍처 — 전역 규칙 (00-overview)

> **문서 세트 사용법**
> - 빈 프로젝트 스캐폴딩 시: 이 문서 + 05/10/20/30/40/50을 **전부** AI 에이전트에 투입한다. 90(안건)은 투입하지 않는다.
> - 이후 모듈 단위 작업 시: 이 문서 + 해당 모듈 문서만 투입한다.
> - `⚠️ PENDING(A-n)` 마커는 90-agenda.md의 안건 번호다. 팀 결정 후 마커를 결정 내용으로 치환한다.
> - **중복 금지 원칙**: 전역 규칙은 이 문서에만 존재한다. 모듈 문서는 자기 모듈 내부 규칙만 다룬다.
> - ArchUnit 등 아키텍처 테스트는 사용하지 않는다. 모듈 경계는 Gradle 컴파일이 강제하지만,
>   **같은 모듈 내 패키지 규칙은 코드 리뷰로만 지켜진다.** 따라서 패키지 규칙은 최대한 단순하게 유지한다.

---

## 1. 모듈 지도

```
aechak/                              # A-4 확정: 루트 패키지 com.aechak
├── gradle/libs.versions.toml        # 버전 카탈로그 — 좌표·버전의 단일 출처 → 50 문서
├── build-logic/                     # 컨벤션 플러그인 (includeBuild — 애플리케이션 코드 아님) → 50 문서
├── common/                          # 순수 규약. Spring 의존 없음 → 05 문서
├── web-common/                      # HTTP 번역 계층 (응답 규격, 전역 핸들러, TraceId) → 05 문서
├── pii/                             # PII 암호화 엔진·키 조립 공용 모듈 (web-security 계열) → 40 문서 §1-1
├── domain/                          # 도메인 모델 (단일 모듈, 내부는 도메인별 패키지) → 10 문서
├── application/                     # UseCase/Facade/Service, Command/Result → 20 문서
├── message/                         # Kafka 통합 메시지 계약 모듈 (의존 0 — 순수 계약)
├── boot/                            # 그룹핑 디렉토리 (자체는 모듈 아님) → 30 문서
│   ├── api/                         # 실행 모듈(구매자 앱). 컨트롤러 + consumer 패키지 동거
│   ├── seller/                      # :seller-api — 셀러센터 실행 모듈(웹). 호스트 분리 전제, 선별 스캔
│   ├── admin/                       # A-5 결정: MVP 제외 — 필요 시점에 생성
│   └── batch/                       # 실행 모듈. Spring Batch
└── infra/                           # 그룹핑 폴더 — 기술 분류 폴더 아래 구체 모듈 (40 문서)
    ├── persistence/jpa/             # :jpa-persistence — JPA 어댑터 (A-1 결정 L2)
    ├── client/pg-client/       # :pg-client — PG 외부 API 어댑터
    ├── kafka/                       # 어댑터 코드가 생기면 하위에 구체 모듈 추가
    └── redis/                       # 〃
```

```kotlin
// settings.gradle.kts (스케치 — 전체 형태와 build-logic 연결은 50 문서 §3)
pluginManagement { includeBuild("build-logic") }
rootProject.name = "aechak"          // A-4 확정
include(
    "common", "web-common", "pii",
    "domain", "application",
    "message",                       // 통합 메시지 계약 (의존 0)
    "api", "seller-api", "batch",    // "admin" — A-5: MVP 제외
    "jpa-persistence", "pg-client",     // A-1 결정(L2). kafka·redis는 어댑터 생길 때 추가
)
// boot/·infra/{분류}는 모듈이 아닌 폴더 — 모듈 이름은 평평하게, projectDir로 위치만 매핑
project(":api").projectDir = file("boot/api")
project(":seller-api").projectDir = file("boot/seller")
project(":batch").projectDir = file("boot/batch")
project(":jpa-persistence").projectDir = file("infra/persistence/jpa")
project(":pg-client").projectDir = file("infra/client/pg-client")
```

---

## 2. 의존 매트릭스 (컴파일 강제)

| 모듈 | 의존 가능 | 명시적 금지 |
| --- | --- | --- |
| common | (없음) | 모든 Spring |
| web-common | common, spring-web/webmvc, servlet-api, slf4j | domain, application |
| pii | application(포트·라벨), spring-context/boot, spring-security-crypto | web-common, infra/* |
| domain | common, jakarta.persistence-api (불활성 어노테이션 스펙 — A-1 결정) | 모든 Spring, web-common |
| application | common, domain, message(순수 계약 — 발행 포트 시그니처), spring-context, spring-tx (A-1 결정 L2 — 리포지토리 포트는 domain 소유, 구현은 infra/persistence) | web-common, spring-web, infra/* |
| message | (없음 — 순수 DTO) | 전부 |
| infra/* | common, domain, application, message, 각 기술 스택 | web-common, 다른 infra 모듈, boot |
| api / seller-api / admin | web-common, application, domain, infra/* (조립), message | — |
| batch | common, application, domain, infra/* (조립), spring-batch | **web-common** |

**전역 불변 규칙**
1. 화살표는 항상 안쪽(common/domain)을 향한다. boot를 의존하는 모듈은 없다. infra를 의존하는 모듈은 boot뿐이다(조립 지점).
2. 도메인/애플리케이션 코드가 web-common을 참조하는 순간 리뷰에서 반려한다.
3. infra 모듈끼리는 서로 모른다. 조합이 필요하면 application의 포트 뒤에서 boot가 조립한다.

---

## 3. 전역 컨벤션

### 3-1. 호출·통신 규칙
- **BC(바운디드 컨텍스트) 내부 = 동기 호출.** Controller/Consumer/Batch → UseCase 인터페이스만.
- **도메인 간 동기 호출 = 상대 도메인의 UseCase만.** 상대 Service/Repository 직접 호출 금지.
  UseCase 간 순환 의존이 생기면 코드로 풀지 말고 설계 신호로 취급한다(이벤트 전환 또는 경계 재검토).
- **BC를 넘는 처리 = 비동기.** 프로세스 내 도메인 이벤트 또는 Kafka.

### 3-2. 이벤트는 두 벌이다 (혼용 금지)
| 구분 | 프로세스 내 도메인 이벤트 | Kafka 통합 메시지 |
| --- | --- | --- |
| 위치 | `domain/{발행자}/event/` | `message` 모듈 |
| 소비 | @TransactionalEventListener | Kafka Consumer (boot 소속) |
| 변경 자유도 | 도메인 리팩토링 따라 자유 | 스키마 호환성 유지 (컨슈머와의 계약) |
| 패키징 | 발행자 기준 | 발행자 기준 |
- 두 클래스는 필드가 같아 보여도 **재사용하지 않는다.** 사는 모듈이 다르므로 구조적으로도 섞이지 않는다.

### 3-3. 에러·응답 규약
- 05 문서(common/web-common 스펙)를 따른다. 성공 = `data` 래핑 + HTTP Status, 실패 = `errorCode`(int) + `message`, 추적 = `X-Trace-Id`.

### 3-4. 언어/네이밍
- Kotlin 2.2 / JDK 21 / Spring Boot 4.0 (Framework 7, Jakarta EE 11). 패키지 루트 `com.aechak` (A-4 확정).
- 도메인은 모듈이 아닌 **패키지**로 구분한다: `domain/order/`, `application/order/`, `boot/api/.../order/`.
- 계층별 클래스 접미사: `~UseCase`(인터페이스) / `~Facade` / `~Service` / `~Command` / `~SearchQuery` / `~Result` / `~Request` / `~Response` / `~ErrorCode` / `~Event`(내부) / `~Message`(Kafka).

### 3-5. 트랜잭션·검증 책임 (요약 — 상세는 각 모듈 문서)
- @Transactional 경계 = **Facade 고정.**
- 검증 3층: 형식(boot, @Valid) / 외부 지식 필요(application) / 자기 상태 불변식(domain).
  단, **동시성 정합성이 걸린 규칙은 저장소 레벨 강제가 우선**(예: 재고 조건부 원자 UPDATE).
