# boot 실행 모듈 (30-boot)

> boot는 그룹핑 디렉토리이며 자체는 모듈이 아니다. 실행(runnable) 모듈은 api / seller-api / admin / batch.
> 여기가 유일한 **조립 지점**: infra/* 모듈들을 의존해 구현체를 스프링 컨테이너에 꽂는다.
> 의존: api/seller-api/admin → web-common + application + domain + infra/* + message.
>       batch → common + application + domain + infra/* + spring-batch. **web-common 금지.**

---

## 1. api 패키지 구조

```
boot/api/src/main/kotlin/com/aechak/api/
├── ApiApplication.kt            # @SpringBootApplication — 컴포넌트 스캔에 webcommon 패키지 포함
├── config/                      # TraceIdFilter 등록, 보안, Jackson 등
├── order/
│   ├── OrderController.kt
│   ├── request/                 # http 요청 dto + toCommand 확장함수 (같은 파일)
│   └── response/                # http 응답 dto + from 팩토리
└── consumer/                    # Kafka 컨슈머 동거 구역 (§4)
    └── order/
```

### 1-1. 계정 상태 게이트 (UserStatusFilter)

JWT에 상태를 박지 않고 매 요청 `users.status`를 프로젝션 조회한다 — 정지·탈퇴·제재가 재로그인 없이 즉시 반영된다.
판정 메커니즘은 web-security의 필터가, **어느 경로를 어떻게 막을지는 각 실행 모듈의 SecurityConfig가** 소유한다.

| 유효 상태 | 처리 |
|---|---|
| ACTIVE | 통과 |
| RESTRICTED | 제한 경로의 쓰기만 403(20013). 로그인·조회·주문은 통과 |
| PENDING_ONBOARDING | 온보딩 허용 경로 외 403(20006) |
| SUSPENDED · BANNED · WITHDRAWN · 유저 없음 | 403(20005) |

- **유효 상태**는 저장값이 아니다 — 기간이 지난 임시 정지는 ACTIVE로 본다(`UserStatus.effective`). 이 판정은 `UserStatusReader` 포트가 책임지므로 호출부(필터·토큰 회전)가 반복하지 않는다.
- RESTRICTED의 제한 경로는 api의 `restrictedWritePathPatterns` — 리뷰·SNS·문의(앱 서비스 문의와 상품 문의) 등 커뮤니티성 쓰기다. 주문·결제는 넣지 않는다: 이용 제한은 거래를 끊는 제재가 아니고, 거래를 끊어야 하는 제재는 임시/영구 정지다. 커뮤니티성 쓰기를 새로 열면 이 목록에 함께 넣는다(빠지면 게이트가 조용히 통과한다).
- **이 목록은 거부 목록이라 판정이 라우팅과 같은 경로를 봐야 한다.** 그래서 접두 문자열·AntPathMatcher가 아니라 `PathPattern`(MVC 매핑과 같은 판정)으로 견준다. 이유 둘 — ① 상품 문의 등록이 `/products/{id}/inquiries`로 가변 세그먼트를 중간에 끼고 있어 접두로 표현되지 않는다. ② MVC는 경로 세그먼트를 **디코딩해** 핸들러를 찾으므로 `/products/{id}/%69nquiries`도 문의 등록으로 라우팅되는데, 원문 `requestURI`를 문자열로 견주면 걸리지 않아 제재가 우회됐다(`SanctionedAccountGateIntegrationTest`가 인코딩 요청까지 고정한다). 허용 목록인 `onboardingAllowedPaths`는 어긋나면 막는 쪽으로 실패하므로 원문 비교로 둔다.
- **셀러센터·어드민은 제한 경로가 비어 있다**(두 보트의 `UserStatusFilter`에 `emptySet()`). 의도된 정책이다 — 셀러의 문의 답변·리뷰 답글은 응대이고, 응대에는 답변 기한과 지표가 걸려 있다. 구매자로서의 커뮤니티 제재(RESTRICTED)로 응대를 끊으면 기다리는 구매자가 손해를 보고 셀러는 자기 잘못이 아닌 기한을 넘긴다. 셀러의 공개 글을 끊어야 하는 제재는 임시/영구 정지(SUSPENDED·BANNED)이고 그것은 모든 보트에서 403(20005)으로 전면 차단된다.
- 게이트는 필터 하나가 아니다. 소셜 로그인(`SocialLoginService`)과 refresh 회전(`TokenService`)도 같은 상태를 보고 SUSPENDED·BANNED·WITHDRAWN을 끊는다 — 필터만 막으면 토큰 수명이 연장된다.

## 2. Controller 규칙

```kotlin
package com.aechak.api.order

/**
 * [Controller 규칙]
 * - 주입은 UseCase 인터페이스만. Facade/Service/Repository 타입 주입 금지.
 * - 하는 일 세 가지뿐: ①형식 검증(@Valid) ②Request→Command 변환 ③Result→Response 변환.
 *   비즈니스 판단이 controller에 등장하면 application으로 내린다.
 * - 응답 규격은 05 문서: 성공 = ApiResponse.of(...) / 반환할 데이터가 없는 생성·삭제만 빈 본문 + Status.
 * - 자격 기반 인가(활성 셀러·ADMIN 등)는 @PreAuthorize, 소유권 검증은 서비스 계층 — 20-application §6.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderUseCase: OrderUseCase,
) {
    @PostMapping
    fun placeOrder(@Valid @RequestBody request: PlaceOrderRequest): ResponseEntity<ApiResponse<OrderResponse>> {
        val result = orderUseCase.placeOrder(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(OrderResponse.from(result)))
    }
}
```

## 3. Request / Response dto — 매핑 소유권은 boot

```kotlin
package com.aechak.api.order.request

/**
 * [Request 규칙]
 * - 형식 검증 어노테이션(@NotNull, @Size...)은 전부 여기. Command에는 없다 (20 문서 §4).
 * - toCommand() 확장/멤버 함수를 이 파일에 함께 둔다 — API 스펙과 매핑이 한 화면에.
 * - application은 boot 타입을 모르므로 역방향 매핑은 애초에 불가능(컴파일 강제).
 */
data class PlaceOrderRequest(
    @field:NotNull val lines: List<OrderLineRequest>,
    // TODO
) {
    fun toCommand(/* 인증 컨텍스트에서 buyerId 등 */): PlaceOrderCommand = TODO()
}
```

```kotlin
package com.aechak.api.order.response

/** [Response 규칙] Result → Response 변환은 companion.from(result). 프론트 계약 변경은 이 파일에서 흡수. */
data class OrderResponse(
    val orderId: Long,
    // TODO
) {
    companion object {
        fun from(result: OrderResult): OrderResponse = TODO()
    }
}
```

### 3-1. 제약 상수의 소유와 참조

**요청 DTO의 제약값은 리터럴로 두지 않고 원본을 참조한다.** 값을 복사하면 한쪽만 고쳐져 어긋나고,
타입이 같아 컴파일도 테스트도 안 잡는다. (사고: 펫 체중 상한을 100→200으로 올릴 때 등록 DTO만
고쳐져 등록 200 / 수정 100으로 갈림)

**원본 위치는 그 값이 누구의 규칙인지로 정한다.**

| 값의 성격 | 원본 | 예 |
| --- | --- | --- |
| 도메인 불변식 | 엔티티 companion | `CartItem.MAX_QUANTITY` — 병합 합산 상한에도 같은 값을 씀 |
| 저장 한계(컬럼 길이) | 그 컬럼을 선언한 엔티티 companion | `ProductImage.STORAGE_KEY_MAX` — `@Column(length = ...)`과 DTO가 같은 값을 봄 |
| 조회 계약 | application의 공용 상수나 Query companion | `CursorPageSize.MIN` — 목록마다 다를 이유가 없어 공용으로 두고, 도메인은 알 이유가 없음 |

**개념의 규칙인지 저장의 한계인지를 먼저 가른다.** 상품이 성립하는 가격 범위나 가질 수 있는
이미지 수는 상품이라는 개념의 규칙이므로 애그리거트가 소유하고 팩토리가 거절한다. 반면 상품명
255자, S3 키 1024자는 컬럼이 못 담는다는 저장 한계일 뿐이라 요청 DTO에서 미리 거절한다.
가르는 질문은 **다른 입구(배치·어드민·컨슈머)로 들어와도 지켜야 하는가**다.

**Command companion에는 제약값을 두지 않는다.** Command는 형식 검증이 boot에서 끝났다는 것이
계약인 객체다(20 문서 §4). 검증 수치를 들고 있으면 그 계약과 어긋나고, 원본이 도메인인지
요청인지도 흐려진다.

원본이 application에 있으면 boot가 자기 의존만 보면 되므로 문제가 없다. **원본이 도메인에 있으면
boot가 domain을 참조하게 된다.** `application`이 domain을 `api`로 걸어 전이 노출하므로 컴파일은
되지만 boot가 직접 선언한 의존은 아니다. 값이 어긋나는 쪽을 더 큰 위험으로 보아 현재는 이 참조를
허용한다. 참조는 상수만 하고 도메인 메서드는 호출하지 않는다.

**도메인이 소유한 규칙은 DTO에 거울을 두지 않는다.** 어노테이션이 먼저 걸리면 도메인이 던질
에러코드 대신 검증 실패 코드가 나가고, 계약이 그 코드를 약속했다면 그대로 거짓말이 된다.
필드별 메시지를 잃는 대신 클라이언트가 분기할 수 있는 코드를 얻는 쪽을 택한다.
저장 한계는 반대다. 던질 도메인 코드가 없고 통과시키면 저장 단계에서 500이 되므로 DTO가 막는다.

- 어노테이션 인자는 컴파일 상수여야 하므로 원본은 `const val`이어야 한다.
- `@Max`·`@Range`의 속성은 `long`이라 Int 상수는 그대로 못 넘긴다. 원본 타입을 바꾸지 말고
  참조 지점에서 변환하거나(`.toLong()`) 원본을 `Long`으로 두고 이유를 주석으로 남긴다.
- 참조가 늘어 boot가 도메인 엔티티를 여럿 열게 되면 **제약 상수 전용 모듈을 분리한다.**
  그때까지는 위 규칙을 따른다.

### 3-2. 생성 문서(OpenAPI)와 실제 응답을 벌어지지 않게 두기

문서는 springdoc이 **자기 ObjectMapper로** 만들고 응답은 컨테이너의 ObjectMapper가 만든다. 둘은 같은
매퍼가 아니다 — 컨테이너 쪽에는 jackson-module-kotlin이 붙어 있고 문서 쪽에는 붙지 않는다. 또 문서
생성기는 핸들러 본문을 읽지 못하므로 애노테이션이 없으면 성공 코드를 200으로 가정한다. 그래서 아래
일곱은 코드만 읽어서는 어긋난 것이 보이지 않고, 문서를 믿는 클라이언트에서만 터진다.

| 상황 | 두는 것 |
| --- | --- |
| `is`로 시작하는 Boolean 프로퍼티 | 게터에 `@get:JsonProperty("isXxx")`. 없으면 응답은 `isXxx`인데 문서는 `xxx`로 적힌다 |
| 한 실행 모듈 안에서 단순 이름이 겹치는 DTO(중첩 클래스 포함) | 한쪽에 `@Schema(name = "...")`. 문서는 단순 이름으로 스키마를 등록해서 겹치면 한 벌만 남고 나머지가 사라진다 |
| 요청이 스스로를 검사하는 계산 프로퍼티(`@get:AssertTrue`) | `@get:Schema(hidden = true)`. 클라이언트가 채워 보낼 값이 아니다 |
| 상태·종류를 내리는 필드 | 문자열 대신 **enum 타입 그대로** 선언한다(`.name` 변환을 두지 않는다). 와이어 어휘가 enum 이름이 아니면(화면용 소문자 세그먼트 등) `@field:Schema(allowableValues = [...])`로 못박고, 그 목록이 서버가 실제로 받는 값과 같은지는 계약 테스트가 지킨다 |
| 쿼리 파라미터를 객체로 묶어 받는 핸들러(`@ModelAttribute`) | 파라미터에 `@ParameterObject`. 없으면 문서에는 필수 객체 파라미터 `request` 하나만 적히고 개별 쿼리 이름과 허용값이 사라진다 |
| 기본값이나 널 허용으로 받고 생략하면 서버가 직접 거절하는 쿼리 파라미터 | `@field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)`. 생성기는 본문의 판정을 읽지 못해 선택으로 적는다 |

`lastTrackingStatus`처럼 외부(택배사)가 준 자유 문자열은 허용값이 없다 — 억지로 enum을 만들지 말고
`@field:Schema(description = ...)`로 자유 문자열임을 적는다.

**성공 상태코드는 핸들러에 `@ResponseStatus`로 명시한다.** 문서 생성기는 애노테이션이 없으면 성공 코드를
200으로 가정한다 — `ResponseEntity.noContent()`로 204를 돌려줘도 문서에는 200으로 적힌다. 반환형만으로는
가릴 수 없다(본문 없는 핸들러 중에도 201·302·200이 섞여 있다). 200이 아닌 코드를 돌려주는 핸들러는
`ResponseEntity`가 세우는 코드와 **같은 값**을 애노테이션으로 함께 적는다 — 둘이 같으니 어느 쪽이 이기든
응답은 바뀌지 않고, 문서만 실제를 따라온다. 본문을 돌려주지 않는 핸들러는 코드를 **반드시** 명시한다(계약
테스트가 누락을 잡는다).

구매자·셀러 실행 모듈의 계약 테스트(`OpenApiContractIntegrationTest`)가 `/v3/api-docs`를 받아 그 보트의
요청·응답 DTO와 핸들러를 전수로 검사하고, 받은 스펙을 `build/openapi/`에 남긴다 — 문서를 다시 보려고 앱을
따로 띄울 필요가 없다. 어드민 계약 테스트(`AdminOpenApiContractIntegrationTest`)는 쿼리 파라미터가 접히거나
빠지는지만 보므로, 나머지 규칙은 어드민에서 사람이 지켜야 한다.

성공 코드 검사가 보는 것은 "핸들러가 선언한 코드와 문서가 같은지"다. 선언이 핸들러가 실제로 세우는 코드와
같은지는 본문을 읽어야 알 수 있어, 그쪽은 통합 테스트의 상태코드 단언이 지킨다.

## 4. Consumer 동거 구역 (결정: api 내 consumer 패키지 — 승격 조건은 A-3)

```kotlin
package com.aechak.api.consumer.order

/**
 * [Consumer 규칙 — worker 승격 대비]
 * - 컨트롤러와 동급의 "진입점"이다. 호출은 UseCase만 — Service/Repository 직접 접근 금지.
 * - message 모듈의 Message 클래스로 역직렬화한다. 도메인 이벤트 클래스 재사용 금지 (00 §3-2).
 * - consumer 패키지 밖(api의 controller 등)을 참조하지 않는다.
 *   → 이 세 규칙이 지켜지면 worker 승격 = 패키지 이동 + build 수정 수준으로 끝난다.
 * - 멱등 처리(processed_events / Inbox)는 application 계층 책임으로 위임 — TODO: 구현 시 결정
 */
@Component
class OrderMessageConsumer(private val someUseCase: SomeUseCase) {
    // TODO: @KafkaListener — 리스너 설정 자체는 infra/kafka가 제공
}
```

## 5. seller-api — 셀러센터 실행 모듈 (SCRUM-192)

- 셀러센터(웹) 전용 실행 모듈. 상품 등록·주문 관리가 붙기 전에 선분리 — 엔드포인트가 늘어난 뒤 분리하면 운영 트래픽 이전이 된다.
- 라우팅은 **호스트 분리**(`seller-api.<도메인>`) 전제 — `api.base-path`(/api/v1)와 경로는 api 시절 그대로라 FE 경로 수정이 없다.
- **선별 스캔**: 루트(com.aechak) 스캔은 auth·kafka 등 api 전용 조립까지 요구해 부팅이 깨진다.
  스캔 목록은 `SellerApiApplication` 소유 — application 패키지를 추가로 쓰면 그 패키지가 요구하는 infra 어댑터·config 조립도 함께 늘린다.
- **셀러센터 전용 유스케이스는 BC 패키지의 `seller` 하위에 둔다**(`application.order.seller`, `application.settlement.seller`,
  `application.review.seller`).
  구매자와 셀러가 **같은 파사드를 공유**하는 애그리거트는 `seller` 하위로 내리지 않고 애그리거트 패키지째
  스캔에 넣는다(`application.order.claim`, `application.inquiry.product`) — 파사드를 쪼개면 같은 서비스 주입이
  양쪽에 복제된다(20 문서 §2). 이때도 **BC 패키지 전체를 넣지 않는다**: `application.inquiry`를 통째로 넣으면
  앱 서비스 문의의 메일 발송 조립(ses-client)까지 딸려온다.
  BC 패키지 전체를 스캔하면 셀러센터가 안 쓰는 파사드가 딸려온다 — `application.order`는 구매자 주문서·장바구니가 배송지·적립금
  조립까지 요구하고, `application.settlement`는 구매확정 원장 생성과 계좌 이관이 딸려온다. `application.review`는 리뷰 작성이
  금칙어 판정 포트(구현은 api 모듈의 아호코라식)와 메시지 발행을 요구해, 상위를 스캔하면 셀러센터가 둘을 조립해야 한다.
  뒤집어, batch는 두 BC 패키지를 스캔하므로 이 하위 패키지들을 excludeFilters로 뺀다 — 셀러 자격(`application.seller`)을
  배치가 끌고 오지 않게 한다.
- **셀러와 배치가 같은 전이를 일으키는 로직은 `application.order.{하위}` 를 따로 파고 양쪽 스캔에 넣는다**(`application.order.delivery`).
  `application.order.seller` 에 두면 batch의 제외 필터에 걸려 배치가 쓸 수 없고, 복제해 두면 전이 순서·이력 기록이 갈린다.
- **Flyway 미탑재**: 스키마 관리는 api가 유일 소유자. seller 단독 배포 시 새 마이그레이션은 api 선배포가 전제.
- 인증: 토큰 발급은 api 소관 — 여기는 RS256 검증만 한다(web-security JwtConfig 공유, 같은 키 주입).
- 온보딩 허용 경로 없음 — 온보딩 미완(PENDING_ONBOARDING) 계정은 셀러센터 전 EP에서 403(UserStatusFilter 화이트리스트 empty).
  제한 경로도 비어 있어 RESTRICTED는 셀러센터를 통과한다 — 의도된 정책이고 근거는 §1-1에 적었다.
- 배포: ECS 서비스 `aechak-seller-api-dev` + ALB host 규칙(`seller-api-<env>` 도메인). JWT는 공개키만 주입한 검증 전용 모드 — 발급은 api 소관.

## 6. batch

```
boot/batch/src/main/kotlin/com/aechak/batch/
├── BatchApplication.kt          # 선별 스캔(@ComponentScan) — seller-api와 같은 규약
├── config/                      # JpaConfig(@EntityScan에 L3 엔티티 패키지 포함) · SchedulingConfig
├── outbox/                      # OutboxSweepSchedule — 아웃박스 재발행(5초 주기)
├── payment/                     # PaymentReconciliationSchedule — 결제 대사(매일 04:30 KST, 빠진 날 따라잡기)
├── order/                       # OrderExpirySchedule — 주문서 TTL 만료와 재고·적립금 복원(매분, 기본 꺼짐)
│                               # OrderDeliverySchedule — 집화 지연 경고·배송완료 자동 전이(매일 03:00 KST, 기본 꺼짐)
│                               # ShipmentTrackingSchedule — 택배사 추적 상태 갱신(6시간마다, 기본 꺼짐, 상태 전이 없음)
│                               # OrderAutoConfirmSchedule — 구매확정 자동화(매일 02:00 KST, 사전 안내 기록 포함, 기본 꺼짐)
│                               # ClaimRefundSchedule — 승인된 취소 클레임의 환불 집행(매분, 기본 꺼짐)
│                               # ClaimAutoApproveSchedule — 셀러 미응답 클레임의 자동승인과 환불(매시, 기본 꺼짐)
│   └── config/                  # 배치별 정책값(@ConfigurationProperties) — 켜고 끄는 플래그 포함
├── settlement/                  # SettlementExecutionSchedule — 정산 집행(매일 05:00 KST, 기준일 1회, 기본 꺼짐)
├── review/                      # ReviewSummaryRequestSchedule — 리뷰 AI 요약 요청(매시 15분, 회차당 상품 30개, 기본 꺼짐)
├── job/{도메인}/                 # (예정) Spring Batch Job/Step — ItemProcessor 등에서 UseCase 호출
└── support/
    └── BusinessSkipPolicy.kt    # (예정) BusinessException → errorCode 로깅 + 스킵 판단 (HTTP 개념 없음)
```

- 스케줄 클래스에는 **UseCase만 주입**한다(Facade·Service·Repository 금지 — api 컨트롤러와 같은 규칙). 시간 계산은 KST를 명시한다.
  모듈 자체 설정값은 `@ConfigurationProperties` 로 받는다 — `@ComponentScan` 을 직접 선언한 탓에 자동 등록이 꺼지므로 `BatchApplication` 의 `@ConfigurationPropertiesScan` 이 등록을 맡는다.
- 회차 안에서 여러 건을 처리하는 스케줄은 **id 커서 페이징**으로 끝까지 밀고, 건별로 예외를 격리한다 — offset 페이징은 앞자리 실패 건이 다음 회차의 같은 자리를 계속 막는다.
  되돌릴 수 없는 처리(구매확정 등)는 `enabled` 설정값으로 환경별로 켠다.
- **선별 스캔**: 루트(com.aechak) 스캔은 인증·SMS·Redis 등 api 전용 조립까지 요구해 부팅이 깨지므로, 쓰는 application 패키지와
  그 Facade가 요구하는 어댑터 모듈(`:pg-client`·`:pii`·`:s3-client`…)을 함께 등재한다. 어드민 전용 구현체(`Admin*`)는 §7의 제외 필터로 뺀다.
  **전역 lazy-init은 쓰지 않는다** — `@Scheduled` 빈이 지연 생성되면 스케줄 자체가 등록되지 않는다. 조립은 `BatchApplicationSmokeTest`가 고정한다.
- 예외 소비 방식: web-common의 핸들러가 아니라 스케줄/SkipPolicy에서 errorCode 기준 처리. 한 건의 실패가 회차를 멈추지 않게 건별로 격리한다.
- 배치가 자체 발신하는 에러 코드의 status는 500 고정 (05 문서 ErrorCode 컨벤션).
- 만료 배치가 쓰는 application 패키지는 `order` 외에 `product.option`(재고 변이 UseCase)이다 — 선별 스캔에 함께 등재돼 있다.
- **상태를 바꾸는 스케줄은 `@ConditionalOnProperty` 로 기본 off**로 올리고, 배포가 끝난 환경에서 설정으로 켠다.
  꺼진 상태에서는 스케줄 빈 자체가 조립되지 않는 것을 `BatchApplicationSmokeTest` 가 고정한다.
- **클레임 제외 필터는 패키지가 아니라 클래스 단위다.** 구매자·셀러 클레임 파사드는 셀러 자격 검증(`application.seller`)을 요구해
  배치가 조립하면 쓰지도 않는 의존이 딸려오지만, 같은 패키지의 환불 집행·자동승인 파사드는 자격 검증을 쓰지 않고 배치가 소비한다.
  패키지 단위로 가리면 그 두 스케줄이 조립되지 않는다.
  반대로 셀러센터는 `ClaimRefund*`·`ClaimAutoApprove*` 두 이름 규약으로 그 구현체를 뺀다 — 자동승인은 셀러 자격을 대조하지 않고
  승인하는 경로라 셀러센터에 두면 컨트롤러가 그 빈을 집는 순간 자격 검증이 비어 버린다.
- **환불 집행 배치는 `order.claim.refund.enabled`로 꺼 둘 수 있고 기본값이 false다.** 이 경로가 실제로 PG 환불을 보내므로,
  상점이 테스트 상점인지 확인되기 전에는 닫아 둔다. 금액 상한(`payment.cancel.max-amount`)도 기본 0으로 닫혀 있어 이중 안전장치다.
- **자동승인 배치는 `order.claim.refund.enabled`와 `order.claim.auto-approve.enabled`가 함께 true일 때만 조립된다.**
  자동승인은 승인에서 끝나지 않고 같은 회차에서 환불을 집행하므로, 환불 스위치가 닫혀 있는데 자동승인만 켜면
  돈을 내보내지 않기로 한 결정을 우회한다. `@ConditionalOnProperty`에 두 이름을 함께 적어 둘 다 켜져야 돌게 했다.
- **만료 배치는 `order.expiry.enabled`로 꺼 둘 수 있고 기본값이 false다.** 결제 승인과 만료 취소가 경합하면 돈은 나갔는데 주문은
  취소된 건이 남는데, 환불 집행 경로가 아직 없어 그때는 수동 환불이 필요하다. 켜기 전에 환불 배선이 있어야 한다.
  경합 자체는 건마다 결제 결과를 먼저 확정한 뒤(외부 조회이므로 트랜잭션 밖) 그래도 결제대기로 남은 건만 취소하는 순서로 줄인다.
- **배송 배치는 `order.delivery.enabled`로 꺼 둘 수 있고 기본값이 false다.** 켜면 송장 등록 시각만 보고 주문을 배송완료로 넘기므로,
  택배사 추적 연동 없이 켜면 실제로 도착하지 않은 주문에 구매확정 기한이 시작된다.
- **리뷰 요약 요청 배치는 `review.summary.enabled`로 꺼 둘 수 있고 기본값이 false다.** 켜면 AI 서버에 요약을 요청해
  모델 호출 비용이 든다. AI 서버의 `enable_review_summary`를 먼저 켜야 한다. 거꾸로 켜면 소비하는 쪽이 없어 요청이 토픽에 쌓이고
  응답 대기 30분이 지날 때마다 같은 상품을 다시 요청한다. 선별 스캔에는 `application.review.summary.request`만 올린다.
  결과 반영과 조회 파사드는 api 몫이다.
- **추적 갱신 배치는 `order.delivery.tracking.enabled`로 꺼 둘 수 있고 기본값이 false다.** 지금 조립할 수 있는 추적 어댑터는
  외부를 부르지 않는 목뿐이라, 켜면 `[모의]` 표식이 붙은 가짜 상태가 발송에 쌓여 구매자·셀러 주문 조회에 그대로 뜬다.
  목 어댑터 자체도 기본 꺼짐이고 운영 프로파일에서는 켜도 조립되지 않는다 — 스위치가 둘로 갈려 있어 하나만 켜도 가짜 값이 들어가지 않는다.
  이 배치는 추적 문구와 조회 시각만 쓰고 **상태 전이는 일으키지 않는다** — 전이의 주인은 배송완료 배치와 셀러 수동 처리다.
  어댑터가 없는 환경에서 켜면 부팅은 되고 회차가 첫 건에서 경고를 남기고 끊는다(조용히 도는 대신 로그로 드러난다).
- **추적이 실제 택배사 값으로 채워지면 배송 배치의 판정을 바꿀 수 있다.** 지금 집화 지연 경고와 배송완료 자동 전이는 송장 등록
  시각만 보므로, 실제로 도착하지 않은 주문도 기한이 지나면 배송완료가 된다(그 배치가 기본 꺼짐인 이유다). 실제 추적 어댑터가
  붙은 뒤에는 발송에 쌓인 최근 추적 상태와 조회 시각을 판정 재료로 삼아 "택배사가 배달완료로 보고한 건만 전이"로 좁힐 수 있다.
  두 작업은 별개다 — 추적 경로를 세운 작업에서 배송 배치의 판정 로직은 건드리지 않았다.
- dev 배포 라인은 api·seller·admin과 같은 모양으로 깔려 있다(SCRUM-251) — 전용 ECR(`aechak-batch`)·로그그룹·taskdef·ECS 서비스와
  SSM 경로 `/aechak/dev/batch/`, `deploy-dev-batch.yml`. **ALB 뒤에 두지 않는다**(수신 포트·헬스체크 엔드포인트가 없다).
  **ECS 태스크는 1대 고정** — 여러 대면 같은 스케줄 회차가 동시에 돈다. 포트원 시크릿 2개는 수기 등재 대상이다.

## 7. admin — 어드민 실행 모듈 (SCRUM-170)

```
boot/admin/src/main/java/com/aechak/admin/
├── AdminApplication.java
├── audit/           # 감사 로그 조회 컨트롤러 + 기록 인터셉터·@AdminAudited/@AdminAuditExempt
├── claim/           # 클레임 조회·개입 컨트롤러 + request/response
├── config/          # JpaConfig · OpenApiConfig · WebConfig · AdminBootstrap{Properties,Runner,Config} · InspectionScanConfig · ClaimAmountScanConfig · AdminReviewScanConfig
├── inspection/      # AI 검수 지표·진행 현황 컨트롤러 + response
├── order/           # 주문 배송 모니터링 컨트롤러 + request/response
├── product/         # 상품 검수 큐·카탈로그 관리 컨트롤러 + request/response
├── review/          # 리뷰 관리·블라인드 컨트롤러 + request/response
├── security/        # SecurityConfig — 정책 조립은 admin 소유, 판단 부품은 web-security
├── inquiry/         # 서비스 문의 조회·답변 컨트롤러 + request/response
├── seller/          # 입점 심사·셀러 계정 관리 컨트롤러 + request/response
├── member/          # 일반 회원 조회·제재 컨트롤러 + request/response
└── user/            # 관리자 명부 컨트롤러 + request/response
```

- A-5(MVP 제외)를 해소하고 신설 — 셀러 입점 심사부터 운영자 기능이 실제 API로 필요해졌다.
- **Java(+Lombok)로 작성한다** (팀 결정) — 경계는 boot/admin과 `Admin*` 클래스까지. 공용 모듈(application의 support 등)에 두는 코드는 Kotlin 유지. 빌드는 `aechak.java-spring-boot-app` 컨벤션(50 문서 §1).
- **application의 어드민 전용 컴포넌트 구현체는 `Admin` 접두사를 쓰고, 구매자·셀러 실행 모듈은 그 구현체를 스캔에서 제외한다**(seller-api의 excludeFilters). 심사처럼 운영자만 쓰는 유스케이스가 셀러와 같은 BC 패키지에 사는데 컴포넌트 스캔은 패키지 단위라 가릴 수 없다 — 제외하지 않으면 셀러센터가 쓰지도 않는 빈을 조립하다 의존을 못 찾고 부팅이 깨진다(SCRUM-208 선례).
- **반대 방향도 있다. 어드민이 쓰는 빈과 같은 패키지에 어드민이 조립하지 못하는 빈이 살면 어드민 쪽에서 그 빈을 뺀다.** 검수 패키지(`application.inspection`)에는 어드민 큐 파사드와 함께 판정 적용(`InspectionFacade`)과 요청 발행(`InspectionRequestService`)이 사는데, 둘은 어드민이 조립하지 않는 카프카 배선을 요구한다. 그래서 `InspectionScanConfig`가 두 클래스를 제외하고 패키지를 스캔한다.
  - 뺄 것이 많으면 거꾸로 **쓸 것만 집어 온다.** 클레임 패키지(`application.order.claim.service`)에는 환불 집행·회수·자동승인 서비스가 함께 살고 그것들이 PG·재고·적립금 포트를 요구해, 패키지 단위로는 어드민이 조립할 수 없다. 그래서 어드민 전용 빈은 하위 패키지(`application.order.claim.admin`)로 따로 두어 `scanBasePackages`에 그 하위만 넣고, 그 패키지 밖에서 하나만 필요한 환불액 계산기는 `ClaimAmountScanConfig`가 `useDefaultFilters = false` + 타입 포함 필터로 집어 온다. 계산 경로를 복제하지 않는 이유는 운영자와 셀러가 같은 클레임에서 다른 환불 예정액을 보면 그 숫자로 판정할 수 없기 때문이다.
- **제외 필터로 가리는 것보다 어드민 전용 유스케이스를 하위 패키지에 모으는 쪽이 낫다.** 리뷰 관리는 `application.review.admin`(usecase·port·facade·service)에 모여 있고 `AdminReviewScanConfig`가 그 패키지만 스캔한다 — 제외 목록은 같은 패키지에 빈이 하나 늘어날 때 함께 고쳐야 하고, 빠뜨리면 어드민 부팅이 깨지는 쪽으로 틀린다. 접두사 규칙(`Admin*`)은 그대로 적용한다. 구매자측과 공유해야 하는 빈만 이름으로 집어 온다 — 평점 재집계(`ReviewRatingFacade`·`ReviewQueryService`)가 그렇다(집계 규칙을 두 벌로 두면 블라인드가 반영한 평점과 리뷰 작성이 반영한 평점이 다른 기준으로 갈린다).
- **어드민이 일으킨 상태 변경이 비정규화된 값을 건드리면 같은 트랜잭션에서 직접 다시 센다.** 리뷰 블라인드·해제·삭제는 `product_stats`의 평점 집계를 그 자리에서 재집계한다. 조회 시점 집계는 이미 블라인드를 제외하지만 상품 목록·상세가 읽는 값은 비정규화돼 있고 그 갱신 경로는 리뷰 작성·삭제 카프카 이벤트뿐이다 — 어드민은 카프카를 조립하지 않으므로 이벤트에 맡기면 블라인드해도 평점이 그대로 남는다. 운영자 수동 처리라 호출 빈도가 낮아 동기 재집계의 비용이 문제되지 않는다.
- **운영자가 일으키는 상태 전이는 대상 행을 잠가 읽는다.** 리뷰 블라인드·해제·삭제는 `ReviewRepository.findByIdForUpdate`로 집는다. 잠그지 않으면 판정과 저장 사이에 다른 전이가 끼어든다 — 운영자 A의 삭제와 B의 해제가 겹치면 둘 다 BLOCKED를 읽어 둘 다 204로 성공하고, 나중에 쓰는 해제가 종결된 리뷰를 PUBLIC으로 되살린다. `@Version`으로는 막히지 않는다: 거절된 전이(삭제된 리뷰의 블라인드 등)는 아무것도 쓰지 않아 버전이 올라가지 않는다(`ProductRepository.findByIdForUpdate`와 같은 판단). **같은 애그리거트를 쓰는 다른 경로도 같은 잠금을 타야 한다** — 한쪽만 잠그면 잠그지 않은 쪽이 먼저 읽은 상태로 덮어쓰므로 작성자 삭제(`ReviewCommandService.delete`)도 함께 바꿨다.
- 리뷰 관리 경로는 `/admin/reviews`(목록·상세·블라인드·해제·삭제)다. 구매자 조회와 달리 노출 상태로 걸러내지 않는다 — 가려진 리뷰를 되돌릴 수 있어야 하는 쪽이라 블라인드·삭제된 리뷰도 결과에 나온다. 이미 블라인드된 리뷰의 재블라인드는 409(110020), 블라인드가 아닌 리뷰의 해제는 409(110021), 삭제된 리뷰의 블라인드·재삭제는 400(110002)으로 막는다. 블라인드는 되돌릴 수 있고 삭제는 되돌리는 경로가 없다(행은 soft delete로 남는다).
- api와 동일 구조/규칙(JSON API 기반) — web-common의 응답 봉투·GlobalExceptionHandler 재사용.
- **application 쪽 스캔은 BC 패키지 전체가 아니라 어드민이 실제 소비하는 하위까지 좁힌다.** 같은 BC에 구매자측 리스너가 살면 패키지 단위 스캔이 그 리스너의 어댑터(메일 발송 등)까지 요구해 부팅이 깨진다 — 문의 BC는 `inquiry.facade`·`inquiry.service`만 스캔한다(`application.user.user`로 좁힌 선례와 같은 결).
- **자격 게이트는 모듈 SecurityConfig가 전역 강제**한다(role=ADMIN, 실패 시 20011·403). 컨트롤러별 `@PreAuthorize`를 쓰는 셀러측과 다른 점 — 모듈 전체가 운영자 전용이라 게이트가 하나면 된다. 전역 자원 접근이라 소유권 검증 계층도 없다.
- 인증: 토큰 발급은 api 소관 — 여기는 RS256 검증만 한다(seller-api와 같은 검증 전용 모드).
- **관리자 명부의 저장값은 `users.role`이고, 기동 부트스트랩(`admin.bootstrap.enabled`/`user-ids`)이 기동마다 설정 명부를 멱등 승격한다.** 최초 관리자를 만드는 경로이자 관리자가 0명이 된 상태의 유일한 복구 경로라 "최초 1회"가 아니라 기동마다 돈다. 명부에 적힌 계정이 없거나 비활성이면 경고 로그만 남기고 부팅을 계속한다 — 어드민이 뜨지 않으면 명부를 고칠 수단도 사라진다. 두 경우 모두 에러 코드가 붙은 비즈니스 예외로 분류돼 경고 로그에 남고(비활성은 409), 이미 관리자인 계정은 멱등 건너뜀으로 처리한다. 부트스트랩이 admin에 사는 이유는 이 모듈만 ECS 태스크가 1대로 고정이고(api는 오토스케일) `users` 쓰기 배선이 이미 조립돼 있어서다.
  - 토큰의 `role` 클레임은 발급 시점 값이고, **회전이 그 값을 DB에서 다시 읽는다**(SCRUM-329). 승격·강등은 재로그인 없이 다음 회전부터 반영된다 — 다만 이미 발급된 access는 그 수명(30분)까지 옛 역할로 통과하므로, 상태(`users.status`)처럼 즉시 반영되지는 않는다(상태는 매 요청 프로젝션 조회다). 강등을 그 30분도 기다리지 않고 끊어야 하면 `TokenService.revokeAll`로 사슬을 지운다 — 그 계정의 일반 사용자 세션까지 함께 끊긴다.
  - 명부를 손으로 바꾸는 경로는 `/admin/admins`(목록·승격·강등)다. 자기 역할 변경은 403(30014), 관리자가 0명이 되는 강등은 409(30013), 활성 상태가 아닌 계정의 승격은 409(30017)로 막는다.
- **승인된 셀러의 계정 관리 경로는 `/admin/sellers`다** — 목록(운영 상태·판매자 유형·상호명 검색·오프셋 페이징), 상세(현재 정지 사유·내부 메모·제재 이력), 판매정지(`POST .../suspensions`), 해제(`POST .../suspensions/release`), 메모 저장(`PUT .../memo`). 식별자는 `sellers.user_id`다(별도 셀러 id가 없다).
  - **여기서 막는 것은 판매이고, 계정 로그인 차단은 `/admin/members`의 몫이다.** `UserRole`에 SELLER가 없어 셀러도 role=GENERAL이고 한 계정이 양쪽에 등장할 수 있다 — 두 층을 한 축으로 합치면 "로그인은 되지만 판매만 막은" 상태를 표현할 수 없다.
  - 정지는 `sellers.status`를 SUSPENDED로 옮기는 것으로 끝난다. 셀러센터 쓰기 경로와 노출 조회가 이미 `status != ACTIVE`를 보고 있어 새 게이트를 만들지 않았다. `products.sale_status`는 건드리지 않는다 — 일괄로 바꾸면 해제할 때 원래 값을 복원할 수 없다.
  - 되돌릴 자리가 있는 상태(ACTIVE·PAUSED)에서만 정지할 수 있고, 직전 상태는 `previous_status`가 보관한다. 이미 정지는 409(10007), 정지가 아닌 셀러의 해제는 409(10008), 탈퇴·탈퇴 요청 셀러의 정지와 동시 조치 충돌은 409(10009)다. `Seller`에 `@Version`이 있어 동시 조치는 커밋 시점 flush에서 터지므로 파사드가 `TransactionTemplate` 밖에서 받아 번역한다.
  - **해제 예정 시각(`sellers.suspension_until`)은 운영자에게 보여 주는 값이고 자동 해제 장치가 아니다.** 게이트가 만료를 보지 않으므로 기간이 지나도 상태는 SUSPENDED로 남는다 — 회원의 임시 정지(조회 시점 만료 판정)와 다른 점이다.
  - 제재 이력(`seller_sanctions`)은 추가 전용이고 해제도 `action=RELEASE` 행 추가로 남긴다. 내부 메모는 `sellers.admin_memo`가 현재 값을, 이력 행의 `memo`가 조치 시점 사본을 든다 — 화면이 제재 없이 메모만 저장하는 경로를 가져서, 현재 값을 이력에서 유도하면 메모만 바꾸려는 요청이 가짜 제재 행을 만든다.
  - **목록 응답에 최근 접속 시각과 누적 신고 수를 싣지 않는다.** `users`에 최근 접속 칼럼이 없고, 신고 대상 종류에 셀러가 없어 셀 수 있는 값이 아니다. 대표자명은 서버가 가려서 내려준다 — 목록은 한 요청에 최대 100행의 대표자명을 내보내는 자리라 평문의 노출면이 행 수만큼 커지고, 평문이 필요한 통장사본 대조는 입점 심사 상세가 담당한다.
- **상품 카탈로그 관리 경로는 `/admin/products`다** — 목록(검수 상태·판매 상태·카테고리 하위 포함·상품명/상호명 검색·오프셋 페이징), 판매중지(`POST .../sale-suspensions`), 재개(`POST .../sale-suspensions/release`). 식별자는 상품 공개 ULID다.
  - **검수 큐(`/admin/product-inspections`)와 성격이 갈린다.** 큐는 판정 대기열이라 등재된 행만 보여 주고, 이 경로는 카탈로그 전수라 노출 조건을 하나도 걸지 않는다 — 큐에 없는 상품(판매 중이고 검수를 통과했으며 신고가 없는 상품)도 보고, 검수와 무관한 이유로 판매를 막는 곳이다. 화면의 "검수 통과"와 "블라인드 유지"에 새 경로를 만들지 않은 이유가 그것이다: 그 둘은 큐의 승인·반려가 상품의 `inspection_status`를 APPROVED·REJECTED로 옮기는 같은 조작이고, 블라인드를 검수와 별 축으로 가르면 칼럼과 노출 조회 조건이 하나씩 늘면서 얻는 것이 없다.
  - **판매중지는 `sale_status`를 SUSPENDED로 옮기는 것만으로는 유지되지 않는다.** `SaleStatus.isSellerAssignable()`이 SUSPENDED를 셀러에게 허용하므로 상태만 옮기면 셀러가 자기 상태 변경 경로로 즉시 되돌린다. 그래서 상품에 운영자가 막았다는 표시(`admin_sale_blocked_at`·사유·행위자·직전 상태)를 두고, 그 표시가 있는 동안 `changeSaleStatusBySeller`를 409(40010)로 거부한다.
  - 별도의 가림 수단은 두지 않는다 — SUSPENDED는 노출 화이트리스트(`SaleStatus.EXPOSABLE`)에서 빠지고 `canPurchase`·`canOrder` 양쪽에서도 막혀, 목록·검색·찜·상세·옵션·주문이 상태 하나로 함께 끊긴다.
  - 재개는 막기 직전 상태로 되돌린 뒤 재고 동기화를 이어 부른다. 일괄로 판매중으로 올리면 스스로 판매중지해 둔 셀러의 의사까지 뒤집히고, 품절은 셀러가 지정하는 값이 아니라 재고에서 파생하는 값이다. 이미 막힌 상품의 중지는 409(40012), 막혀 있지 않은 상품의 재개는 409(40009), 동시 조치 충돌은 409(40011)다 — `Product`에 `@Version`이 있어 파사드가 `TransactionTemplate` 밖에서 받아 번역한다(셀러 계정 제재와 같은 방식).
  - 목록의 신고 누적 수는 페이지가 정해진 뒤 그 상품 id 목록으로 한 번 더 묻는다. 상관 서브쿼리는 행마다 1회 실행이고, 본 질의에 `group by`로 섞으면 신고가 0건인 상품이 집계에서 빠진다. 셀러 상호명은 left join이다 — `products.seller_id`는 FK 없는 값 참조라 셀러 행이 없는 상품이 목록에서 사라지면 운영자가 손을 댈 수 없다.
- **검수 진행 현황 경로는 `/admin/inspections`다** — 목록(처리 상태·대상 종류·판정 필터, 요청 시각 내림차순, 오프셋 페이징)과 상세(관점별 결과·사유·셀러 안내·프롬프트 버전·내용 해시·임차). 식별자는 `content_inspections.id`다 — 검수 행에는 공개 식별자가 없다. 조회만 하므로 감사 기록을 붙이지 않는다.
  - **같은 BC의 기존 두 경로로는 답할 수 없는 질문을 받는다.** 운영 지표(`/admin/inspection-metrics`)는 창이 `settled_at` 기준인 집계라 끝난 건만 세고, 검토 큐(`/admin/product-inspections`)는 사람 확인이 남은 행만 들어 위반 판정 건만 올라온다. "방금 등록한 상품의 검수가 지금 어디까지 왔는가"는 둘 중 어느 것도 보여 주지 않아 경로를 새로 뒀다 — 집계가 아니라 검수 건 하나하나이고, 큐와 달리 판정 전·통과까지 전부 본다.
  - **통과와 미검사를 섞지 않는 것이 이 화면의 존재 조건이다.** 관점 결과는 `VIOLATION`·`CLEAR`·`UNCHECKED` 셋을 그대로 내려보내고 미검사에는 그 사유를 함께 싣는다. 판정의 `UNJUDGED`(기한 초과)·`FAILED`(처리 실패)도 `PASS`와 한 칸에 묶지 않는다. 관점 칸이 아예 없는 것(파이프라인이 거기까지 가지 못했다)과 `UNCHECKED`(갔지만 보지 못했다)도 다르다 — 어느 쪽도 통과로 채우지 않는다.
  - **판정을 아직 받지 못한 건은 판정 축으로 고를 수 없다.** 그 상태는 판정 칼럼이 빈 것이고 `InspectionJudgment`에 그 값이 없다 — `processingState=QUEUED`·`IN_FLIGHT`가 그 축이다. 판정에 "없음"을 끼워 두 칼럼을 한 축으로 합치면 기한 초과와 아직 안 끝난 건이 같은 칸에 섞인다(두 칼럼이 갈라져 있는 이유가 그것이다).
  - 상품명은 목록 질의가 상품을 left join으로 붙여 함께 가져온다. 행마다 따로 물으면 조회가 행 수만큼 늘고, inner join으로 두면 상품이 지워진 검수와 리뷰 검수가 목록에서 사라져 운영자가 "안 보인다"의 원인을 이 화면에서 찾을 수 없다. 조인 조건에 대상 종류를 함께 넣는다 — 빼면 리뷰 검수의 `target_ref`가 어느 상품 id와 같을 때 그 상품 이름이 리뷰 행에 붙는다.
  - 임차(`lease_owner`·`lease_expires_at`)는 상세에만 싣는다. 멈춘 건을 진단하는 유일한 단서이고 담기는 값이 워커 인스턴스 표식이라 운영자에게 보여 줄 수 있지만, 한 번에 100행까지 내보내는 목록에 두면 내부 표식의 노출면이 행 수만큼 커진다(셀러 목록에서 대표자명을 가린 것과 같은 결).
  - **요청 시각에 인덱스를 두지 않았다.** `content_inspections`의 보조 인덱스는 `settled_at` 하나뿐이고 지표가 그 축을 쓴다 — 이 목록은 상태 등호 + `requested_at` 정렬이라 정렬이 파일소트다. 조회만 하는 변경에 스키마를 건드리지 않으려 미뤘고, 눈에 보이게 느려지면 `(processing_state, requested_at)`을 더한다. 더할 때는 마이그레이션과 엔티티 선언을 함께 바꿔야 ddl-auto 환경과 Flyway 환경의 조회 계획이 갈리지 않는다.
- **일반 회원 조회·제재 경로는 `/admin/members`다** — 목록(상태 필터·닉네임/회원ID 검색·가입일 범위·커서 페이징), 상세(현재 프로필 이미지와 소개글, 제재 이력 포함), 제재(`POST .../sanctions`), 해제(`POST .../sanctions/release`), 프로필 초기화(`POST .../profile/reset`).
  - 목록·상세는 `users.role = GENERAL`만 본다. 관리자 계정은 명부 경로가 따로 있어 여기서는 조회도 404(30000), 제재도 409(30021)로 막는다 — 운영 권한을 가진 계정이 차단 상태로 남지 않게 강등을 먼저 하게 한다. 자기 자신 제재는 403(30020).
  - 제재는 `users.status` 단일 축으로 표현한다(RESTRICTED·SUSPENDED·BANNED). 접근 판정의 단일 지점이 매 요청 한 컬럼만 읽고 끝나야 하고, 제재 상태끼리는 배타적이라 축을 둘로 나누면 모순 조합이 생긴다.
  - **탈퇴(WITHDRAWN, 본인 의사)와 영구 정지(BANNED, 운영 조치)는 합치지 않는다.** 차단 효과가 같아도 한 값으로 묶으면 사후에 가릴 수 없다. 운영자의 탈퇴 처리는 WITHDRAWN으로 가고, 운영 조치였다는 사실은 제재 이력(`user_sanctions`)이 남긴다.
  - 영구 정지는 종단 상태다 — 추가 제재(409/30018)도 해제(409/30019)도 받지 않는다. 탈퇴한 계정도 제재 대상이 아니다(409/30003).
  - **온보딩을 마치지 않은 계정에는 되돌아오는 제재(이용 제한·임시 정지)를 걸 수 없다**(409/30023). 상태가 한 축이라 PENDING_ONBOARDING을 덮으면 돌아올 상태를 잃고, 해제·만료가 약관 동의도 프로필도 없는 ACTIVE를 만들어 온보딩 게이트를 우회한다. 영구 정지·탈퇴 처리는 돌아올 상태가 없어 허용한다.
  - 제재 이력은 추가 전용이다. 해제도 UPDATE가 아니라 `action=RELEASE` 행 추가로 남기고, 사유는 자유 문자열이 아니라 고정 어휘(`SanctionReasonCode`) + 상세로 받는다 — 신고 사유와 같은 결정이다.
  - **프로필 초기화는 제재가 아니다.** 신고된 프로필의 이미지를 기본 이미지로 되돌리고 소개글을 비울 뿐 계정 상태와 제재 이력은 건드리지 않고 사용자는 그 뒤에 다시 바꿀 수 있다. 닉네임은 바꾸지 않는다. 제재 중인 계정도 대상이고 프로필이 없는 계정(온보딩 전, 탈퇴)은 409(30024), 관리자 계정은 조회와 같이 404(30000)다. 감사 로그에는 `MEMBER_PROFILE_RESET`으로 남는다.
  - **임시 정지 만료는 조회 시점 판정이다**(`UserStatus.effective`). 저장값(SUSPENDED + `sanction_until`)은 "7일 정지를 걸었다"는 사실 기록으로 남고, 인가 게이트와 어드민 조회가 같은 함수로 유효 상태를 계산한다. 상태를 되돌리는 배치는 두지 않았다 — 판정이 한 곳이면 운영자가 본 상태와 실제 접근 권한이 갈리지 않는다.
- **주문 배송 모니터링(`/admin/orders`)의 "지연" 기준은 서버가 쥔다.** 목록 행의 지연 신호와 지표 카드의 지연 건수가
  같은 경계 시각을 받아 판정한다 — 프런트가 날짜를 빼서 세면 두 수치가 다른 기준으로 갈린다. 발송 기한은 셀러센터가 쓰는
  `order.dispatch.sla-days`를 그대로 읽어 같은 주문을 두 화면이 같게 본다. 셀러가 신고하는 `avgDispatchDays`는 쓰지 않는다 —
  셀러마다 기준이 갈려 "지연 N건"이 셀러 간 비교 불가가 되고, 영업일을 날짜로 환산할 휴일 달력이 없다.
  배송 정체(`order.delivery.sla-days`)는 셀러 화면에 없는 어드민 전용 축이다.
  - **두 축의 시계가 시작하는 지점이 다르다.** 발송 축은 주문 시각(`orders.created_at`)에서, 배송 축은 발송 시각
    (주문에 붙은 가장 늦은 `shipments.dispatched_at`, 없으면 주문 시각)에서 센다. 배송 축도 주문 시각에서 재면 발송이
    늦은 주문이 송장을 받는 순간 발송 축에서 빠지고 배송 축에 그대로 걸려, 사유만 "배송 정체"로 바뀐 채 다시 나타난다 —
    운영자가 셀러를 독촉해야 할 건에 택배사를 쫓는다. 발송 기록이 없는 배송중 주문의 시계를 멈추면 그 주문이 두 축
    어디에도 안 잡혀 영구히 보이지 않으므로 주문 시각으로 되돌린다.
  - **지표 카드의 금액(`totalOrderAmount`)은 주문 금액 합이지 실제 수납액이 아니다.** 상품금액 + 셀러배송비 - 안분
    쿠폰할인이고 적립금은 빠지지 않는다 — 적립금은 주문그룹 단위로 쓰이는데 그룹 하나가 셀러별 주문 여럿으로 쪼개져
    셀러별 주문에 안분할 근거가 없다. 그래서 정산·대사 숫자와 맞춰 볼 값이 아니다. 취소 주문과 취소·반품으로 종결된
    품목은 목록 행의 금액과 같은 규칙으로 함께 빠진다.
- **셀러 발송 독촉(`POST /admin/orders/{orderId}/dispatch-nudges`)은 기록만 남긴다.** 어드민 모듈은 발송 어댑터(`:ses-client`)를
  의존하지 않아 `EmailSender` 구현이 조립되지 않는다 — 그래서 응답에 발송을 주장하는 필드를 두지 않는다. 발송을 붙이는 시점에
  의존·스캔과 응답 계약을 함께 고친다. 같은 주문 반복 독촉은 `order.dispatch.nudge-cooldown-hours` 간격으로 묶고, 주문 행
  선점 조회로 동시 요청을 직렬화한다(409·50501).
- **운영자 조치는 감사 로그에 남는다(SCRUM-318).** 기록은 쓰기 핸들러에 붙은 `@AdminAudited`를 보고
  `AdminAuditInterceptor`가 핸들러가 끝난 뒤 쓴다. 유스케이스마다 기록 호출을 흩뿌리지 않는 이유는
  그 방식이 빠뜨리기 쉽고 **빠뜨린 것이 보이지 않기** 때문이다 — 쓰기 핸들러에 `@AdminAudited`나
  `@AdminAuditExempt`(이유 필수) 중 하나가 없으면 `AdminAuditCoverageTest`가 빌드를 끊는다.
  - 인터셉터가 스스로 아는 것은 봉투(운영자·시각·조치·결과·IP·추적 id)까지다. 사유와 기록 시점의
    대상 이름은 요청 본문·애그리거트에만 있으므로 그걸 아는 자리가 `AdminAuditContextHolder`로 올린다.
  - 거부당한 시도도 남는다(결과 FAILURE + HTTP 상태). 다만 **모듈 게이트가 끊은 401·403은 남지 않는다** —
    Security 필터 구간에서 끝나 DispatcherServlet에 닿지 않는다.
  - **기록 실패는 조치를 되돌리지 않는다.** 쓰기는 REQUIRES_NEW로 자기 트랜잭션에서 커밋하고,
    실패는 파사드가 삼켜 ERROR 로그로만 남긴다. 반대로 했다면 감사 테이블 사고 한 번이 모든 운영 조치를 멈춘다.
  - **수정·삭제 경로를 만들지 않는다.** 도메인 포트는 `append`만 열고 컨트롤러에 쓰기 핸들러가 없다.
  - 지금 기록하는 조치는 입점 심사 승인·거부, 관리자 승격·강등, 상품 검수 승인·반려, 클레임 개입,
    회원 제재·해제, 서비스 문의 답변, 리뷰 블라인드·해제·삭제, 셀러 판매정지·해제·내부 메모 수정,
    **상품 판매중지·재개**다. 조치를 더하는 사람이 할 일은
    쓰기 핸들러에 애너테이션 하나를 붙이는 것뿐이고, 기록 코드를 쓰지 않는다. 면제를 고르면 `reason`에
    그 판단을 적는다 — 커버리지 테스트는 "어느 쪽인지 정했나"만 본다. 문의의 처리중·보류 전이가
    지금 유일한 면제다(처리 흐름 표시이고 그 결과는 문의 행이 들고 있다).
  - 사유 칸에는 고정 어휘를 사람이 읽는 문구로 옮겨 싣는다(`SanctionReasonCode.label`·
    `ReviewBlindReason.label`). 열거 이름을 그대로 실으면 운영자가 조치 이력마다 코드를 해독해야 한다.
  - `X-Trace-Id` 필터를 WebConfig가 등록한다(시큐리티 체인보다 앞). 이 모듈의 로그 패턴은 전부터
    `%X{traceId}`를 쓰는데 등록이 빠져 있었고, 감사 행의 추적 id도 같은 값이다.
- DB 마이그레이션은 api 단일 소유 그대로 — admin은 접속만 한다.
- 배포: ECS 서비스 `aechak-admin-dev` + ALB host 규칙(`admin-api-<env>` 도메인). `admin-<env>`는 어드민 웹(FE) 몫이라 API는 api 레이블을 붙인다.
