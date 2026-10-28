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
다섯은 코드만 읽어서는 어긋난 것이 보이지 않고, 문서를 믿는 클라이언트에서만 터진다.

| 상황 | 두는 것 |
| --- | --- |
| `is`로 시작하는 Boolean 프로퍼티 | 게터에 `@get:JsonProperty("isXxx")`. 없으면 응답은 `isXxx`인데 문서는 `xxx`로 적힌다 |
| 한 실행 모듈 안에서 단순 이름이 겹치는 DTO(중첩 클래스 포함) | 한쪽에 `@Schema(name = "...")`. 문서는 단순 이름으로 스키마를 등록해서 겹치면 한 벌만 남고 나머지가 사라진다 |
| 요청이 스스로를 검사하는 계산 프로퍼티(`@get:AssertTrue`) | `@get:Schema(hidden = true)`. 클라이언트가 채워 보낼 값이 아니다 |
| 상태·종류를 내리는 필드 | 문자열 대신 **enum 타입 그대로** 선언한다(`.name` 변환을 두지 않는다). 와이어 어휘가 enum 이름이 아니면(화면용 소문자 세그먼트 등) `@field:Schema(allowableValues = [...])`로 못박고, 그 목록이 서버가 실제로 받는 값과 같은지는 계약 테스트가 지킨다 |

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
따로 띄울 필요가 없다. 어드민에는 아직 이 테스트가 없어 위 규칙을 사람이 지켜야 한다.

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
- **셀러센터 전용 유스케이스는 BC 패키지의 `seller` 하위에 둔다**(`application.order.seller`, `application.settlement.seller`).
  BC 패키지 전체를 스캔하면 셀러센터가 안 쓰는 파사드가 딸려온다 — `application.order`는 구매자 주문서·장바구니가 배송지·적립금
  조립까지 요구하고, `application.settlement`는 구매확정 원장 생성과 계좌 이관이 딸려온다. 뒤집어, batch는 두 BC 패키지를
  스캔하므로 이 하위 패키지들을 excludeFilters로 뺀다 — 셀러 자격(`application.seller`)을 배치가 끌고 오지 않게 한다.
- **셀러와 배치가 같은 전이를 일으키는 로직은 `application.order.{하위}` 를 따로 파고 양쪽 스캔에 넣는다**(`application.order.delivery`).
  `application.order.seller` 에 두면 batch의 제외 필터에 걸려 배치가 쓸 수 없고, 복제해 두면 전이 순서·이력 기록이 갈린다.
- **Flyway 미탑재**: 스키마 관리는 api가 유일 소유자. seller 단독 배포 시 새 마이그레이션은 api 선배포가 전제.
- 인증: 토큰 발급은 api 소관 — 여기는 RS256 검증만 한다(web-security JwtConfig 공유, 같은 키 주입).
- 온보딩 허용 경로 없음 — 셀러센터 전 EP가 ACTIVE 전용(UserStatusFilter 화이트리스트 empty).
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
├── claim/           # 클레임 조회·개입 컨트롤러 + request/response
├── config/          # JpaConfig · OpenApiConfig · WebConfig · AdminBootstrap{Properties,Runner,Config} · InspectionScanConfig · ClaimAmountScanConfig
├── product/         # 상품 검수 큐 컨트롤러 + request/response
├── security/        # SecurityConfig — 정책 조립은 admin 소유, 판단 부품은 web-security
├── seller/          # 입점 심사 컨트롤러 + request/response
└── user/            # 관리자 명부 컨트롤러 + request/response
```

- A-5(MVP 제외)를 해소하고 신설 — 셀러 입점 심사부터 운영자 기능이 실제 API로 필요해졌다.
- **Java(+Lombok)로 작성한다** (팀 결정) — 경계는 boot/admin과 `Admin*` 클래스까지. 공용 모듈(application의 support 등)에 두는 코드는 Kotlin 유지. 빌드는 `aechak.java-spring-boot-app` 컨벤션(50 문서 §1).
- **application의 어드민 전용 컴포넌트 구현체는 `Admin` 접두사를 쓰고, 구매자·셀러 실행 모듈은 그 구현체를 스캔에서 제외한다**(seller-api의 excludeFilters). 심사처럼 운영자만 쓰는 유스케이스가 셀러와 같은 BC 패키지에 사는데 컴포넌트 스캔은 패키지 단위라 가릴 수 없다 — 제외하지 않으면 셀러센터가 쓰지도 않는 빈을 조립하다 의존을 못 찾고 부팅이 깨진다(SCRUM-208 선례).
- **반대 방향도 있다. 어드민이 쓰는 빈과 같은 패키지에 어드민이 조립하지 못하는 빈이 살면 어드민 쪽에서 그 빈을 뺀다.** 검수 패키지(`application.inspection`)에는 어드민 큐 파사드와 함께 판정 적용(`InspectionFacade`)과 요청 발행(`InspectionRequestService`)이 사는데, 둘은 어드민이 조립하지 않는 카프카 배선을 요구한다. 그래서 `InspectionScanConfig`가 두 클래스를 제외하고 패키지를 스캔한다.
  - 뺄 것이 많으면 거꾸로 **쓸 것만 집어 온다.** 클레임 패키지(`application.order.claim.service`)에는 환불 집행·회수·자동승인 서비스가 함께 살고 그것들이 PG·재고·적립금 포트를 요구해, 패키지 단위로는 어드민이 조립할 수 없다. 그래서 어드민 전용 빈은 하위 패키지(`application.order.claim.admin`)로 따로 두어 `scanBasePackages`에 그 하위만 넣고, 그 패키지 밖에서 하나만 필요한 환불액 계산기는 `ClaimAmountScanConfig`가 `useDefaultFilters = false` + 타입 포함 필터로 집어 온다. 계산 경로를 복제하지 않는 이유는 운영자와 셀러가 같은 클레임에서 다른 환불 예정액을 보면 그 숫자로 판정할 수 없기 때문이다.
- api와 동일 구조/규칙(JSON API 기반) — web-common의 응답 봉투·GlobalExceptionHandler 재사용.
- **자격 게이트는 모듈 SecurityConfig가 전역 강제**한다(role=ADMIN, 실패 시 20011·403). 컨트롤러별 `@PreAuthorize`를 쓰는 셀러측과 다른 점 — 모듈 전체가 운영자 전용이라 게이트가 하나면 된다. 전역 자원 접근이라 소유권 검증 계층도 없다.
- 인증: 토큰 발급은 api 소관 — 여기는 RS256 검증만 한다(seller-api와 같은 검증 전용 모드).
- **관리자 명부의 저장값은 `users.role`이고, 기동 부트스트랩(`admin.bootstrap.enabled`/`user-ids`)이 기동마다 설정 명부를 멱등 승격한다.** 최초 관리자를 만드는 경로이자 관리자가 0명이 된 상태의 유일한 복구 경로라 "최초 1회"가 아니라 기동마다 돈다. 명부에 적힌 계정이 없거나 비활성이면 경고 로그만 남기고 부팅을 계속한다 — 어드민이 뜨지 않으면 명부를 고칠 수단도 사라진다. 두 경우 모두 에러 코드가 붙은 비즈니스 예외로 분류돼 경고 로그에 남고(비활성은 409), 이미 관리자인 계정은 멱등 건너뜀으로 처리한다. 부트스트랩이 admin에 사는 이유는 이 모듈만 ECS 태스크가 1대로 고정이고(api는 오토스케일) `users` 쓰기 배선이 이미 조립돼 있어서다.
  - 토큰의 `role` 클레임은 로그인 시점 스냅샷이다 — 승격 직후 ADMIN 토큰을 받으려면 api에서 다시 로그인해야 하고, 강등해도 발급돼 있던 토큰은 만료까지 ADMIN으로 통과한다.
  - 명부를 손으로 바꾸는 경로는 `/admin/admins`(목록·승격·강등)다. 자기 역할 변경은 403(30014), 관리자가 0명이 되는 강등은 409(30013), 활성 상태가 아닌 계정의 승격은 409(30017)로 막는다.
- DB 마이그레이션은 api 단일 소유 그대로 — admin은 접속만 한다.
- 배포: ECS 서비스 `aechak-admin-dev` + ALB host 규칙(`admin-api-<env>` 도메인). `admin-<env>`는 어드민 웹(FE) 몫이라 API는 api 레이블을 붙인다.
