# REVIEW.md — 코드 리뷰 가이드

자동 리뷰(Claude Code Review 워크플로우)와 사람 리뷰어가 공유하는 리뷰 기준이다.
각 항목의 근거는 `docs/`·`knowledge-base/`의 규범 문서에 있다 — 판단이 애매하면 출처 문서를 직접 확인한다.

## 심각도 정의

- **🔴 반려(Blocker)**: 아키텍처 경계 위반, 데이터 정합성 파괴 가능, 보안 결함(인가 누락·PII 평문 저장), 팀이 명시적으로 "반려"라고 못박은 규칙 위반. 하나라도 있으면 머지 불가.
- **🟡 중요(Important)**: 동작이 틀릴 수 있는 버그, 규범 위반이지만 경계 침범은 아닌 것(잉여 SQL, 규약 어긋난 시그니처, 필수 테스트 누락).
- **⚪ 니트(Nit)**: 네이밍·가독성 제안. **리뷰당 최대 5개까지만** 보고하고, 나머지는 "유사 N건"으로 요약한다.

## 즉시 반려 체크리스트 (🔴)

의존·경계 — `00-overview.md §2·§3`:
- [ ] domain·application이 `web-common`을 참조 (문서가 명시적으로 "리뷰 반려" 지정)
- [ ] domain에 Spring 의존 등장 (`AbstractAggregateRoot` 포함 — 자체 `AggregateRoot` 사용)
- [ ] batch 모듈이 `web-common` 의존
- [ ] infra 모듈끼리 상호 참조 (조합은 application 포트 뒤에서 boot가 조립)
- [ ] 타 도메인의 Service/Repository 직접 호출 (동기 호출은 상대 UseCase 인터페이스만)
- [ ] Controller/Consumer/Batch에 Facade·Service·Repository 주입 (UseCase만 허용)
- [ ] BC 경계 밖 JPA 연관 또는 DB FK 제약 (Long id 값참조만 허용, `10-domain.md §2-1`)
- [ ] 빌드 파일에 좌표/버전 문자열 하드코딩 (`50-build.md §2·§6` — toml 등록 후 `libs.*`)
- [ ] `00 §2` 의존 매트릭스에 없는 의존성 추가, 또는 쓰는 코드 없는 의존 선반입(YAGNI)

보안·데이터:
- [ ] publicId를 인가 대체로 사용 — 조회 API마다 소유권 검증(BOLA/IDOR) 필수 (`10-domain.md §2-2`)
- [ ] 내부 bigint id를 API 응답에 노출 (외부 노출은 publicId(ULID)만)
- [ ] PII(전화번호·계좌·배송지 연락처 등) 평문 저장 — AES-256 AttributeConverter(infra 소속) 경유

## 레이어별 체크리스트

### domain / 엔티티 — `10-domain.md`, `knowledge-base/61·62`
- 금액 필드는 **Long**(원 단위 정수). BigDecimal·Int 금지. count/quantity류는 Int 유지 (61 F17)
- Kotlin non-null 참조타입에 `@Column(nullable=false)` 명시 — Hibernate는 Kotlin null성을 DDL에 반영하지 않음 (61 P1)
- 엔티티는 anemic 금지: setter 노출 대신 의도 드러나는 메서드(cancel, confirm…) (10 §2)
- 엔티티에 `data class` 금지 (10 §5)
- 동시성 정합 규칙(재고 차감 등)은 엔티티 메서드가 아니라 저장소 조건부 원자 UPDATE(`WHERE stock >= ?`) (10 §2·§3-5)
- 자기 상태만으로 판단 가능한 불변식 = domain, 외부 지식(타 애그리거트·DB 조회) 필요 검증 = application (10 §2)
- created_at/updated_at은 BaseEntity 상속. Spring Data Auditing 금지, 순수 JPA 콜백 사용 (10 §2-2)
- 엔티티 재구성 merge 금지 — 항상 load 후 수정 (publicId 재채번 사고) (10 §2-2)
- 도메인 이벤트: 애그리거트가 `registerEvent` 수집 → **Facade가 발행 후 clearEvents()**. domain에서 ApplicationEventPublisher 금지 (10 §3)
- 에러 코드는 발생 도메인 패키지가 소유, 대역 준수(seller 10000~ … settlement 100000~ 잠정). 실제 던지는 것만 추가 (62)

### 연관관계(JPA) — `10-domain.md §2-1`
- 애그리거트 내부(루트→자식): `@OneToMany(cascade=ALL, orphanRemoval=true) + @JoinColumn` 단방향, 자식에 부모 참조·FK 필드 금지
- `@OneToMany @JoinColumn`에 **`updatable=false` 필수** — 누락 시 잉여 FK UPDATE 발생 (61, `AggregateChildSqlTest`가 회귀 단언)
- 같은 BC 다른 애그리거트: `@ManyToOne(fetch=LAZY)` 단방향. **역방향 @OneToMany 컬렉션 금지**
- 연관 내비게이션으로 타 애그리거트 상태 변경 금지 (`order.orderGroup.markPaid()` ❌) — 수정은 자기 리포지토리로 로딩
- fetch는 LAZY 기본
- 명시적 예외(연관 없이 값참조): RetentionRecord.userRef, PointTransaction.sourceId, ProductStats.productId, review BC의 대외 참조 4건 — 이들에 연관 추가 요구하지 말 것

### application — `20-application.md`
- `@Transactional`은 **Facade에만**. Service·도메인 메서드 부착 금지
- UseCase 구현은 항상 Facade. Service가 UseCase 직접 구현 금지. 도메인당 UseCase 1개
- 쓰기 입력은 항상 `{동사}{대상}Command` (인자 1개여도). **Command에 검증 어노테이션 금지** (형식 검증은 boot @Valid에서 종료)
- 출력은 항상 Result 계열 — **도메인 엔티티 반환 금지**. 변환은 `companion.from(entity)`
- 매핑 코드(Request.toCommand / Response.from)는 boot 소유 — application에 없어야 함
- 조회 조건 3개 이상/페이징이면 `{대상}SearchQuery`, 스칼라 1~2개는 그냥 인자 (과설계 금지)
- 포트 시그니처에 Spring Data 타입(Pageable 등)·외부사 DTO 역류 금지 (`40-infra.md §1·§3`)

### boot — `30-boot.md`
- Controller의 일은 3가지뿐: @Valid → Request→Command → Result→Response. 비즈니스 판단 등장 시 application으로
- 형식 검증 어노테이션은 전부 Request DTO에
- 응답 규격: 성공 `ApiResponse.of(...)`, 실패 `errorCode`(int)+message, `X-Trace-Id` (`05` 문서)
- 도메인 이벤트 클래스를 Kafka 메시지로 재사용 금지 — 필드가 같아 보여도 두 벌 (`00 §3-2`)

### 테스트 — `docs/70-testing.md`
- 모든 테스트 KDoc 첫 줄에 정체(계약/회귀/스모크/통합)와 "깨지면 무엇이 잘못인가" 명시
- 테스트 이름은 한글 백틱으로 행위·기대 서술
- 결과(상태·출력) 검증 — mock 후 `verify` 횟수 검증으로 끝나는 테스트 반려
- 통합 테스트는 `IntegrationTestBase` 상속 필수 (클래스별 `properties`/`@MockitoBean`/`@DirtiesContext`는 컨텍스트 캐시 파괴 → 반려)
- 이벤트/커밋 side-effect 테스트에 `@Transactional` 롤백 금지 (AFTER_COMMIT 미발화 → 거짓 통과)
- 저장 검증은 flush()+clear() 후 재조회 (1차 캐시 가짜 통과 방지)
- 동시성·SKIP LOCKED·DB 에러 분기를 H2로 검증 금지 (Testcontainers 영역)
- `@MockBean` 금지 (Boot 4에서 삭제 → `@MockitoBean`)

## 지적하지 말 것

- **equals/hashCode override 요구 금지** — 팀이 명시적으로 보류 결정. 엔티티를 Set/Map 키로 쓰기 시작할 때 도입 (10 §5)
- 커버리지 수치 — 팀은 커버리지 숫자를 목표로 삼지 않음 (70 §1.4)
- 린트/포맷 수준의 스타일 (도구 영역)
- `⚠️ PENDING(A-n)`·`TODO`·`F##` 마커가 붙은 미확정 항목의 구현 요구 (message 모듈, admin, 인증 등은 의도된 미도입)
- ArchUnit 등 아키텍처 테스트 도입 요구 — 패키지 규칙은 리뷰로 강제하기로 결정됨 (00 서문)

## 범위·형식 (참고 확인)

- PR 제목 `[SCRUM-No] 작업 내용` / 브랜치 `feature/SCRUM-###-...` 형식 확인 (`gh pr view`로 조회 가능)
- diff가 MVP 범위 밖(미도입 모듈 등)을 건드리는지 애매하면 `docs/60-mvp-scope.md` 참조
- 커밋 메시지 컨벤션(`90-commit-convention.md`)은 봇이 diff로 검증 불가 — 사람 리뷰어가 확인

> 규범 원문은 각 체크리스트 항목의 인라인 `(문서 §번호)` 인용을 따라가면 된다.
