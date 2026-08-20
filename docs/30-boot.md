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
- **Flyway 미탑재**: 스키마 관리는 api가 유일 소유자. seller 단독 배포 시 새 마이그레이션은 api 선배포가 전제.
- 인증: 토큰 발급은 api 소관 — 여기는 RS256 검증만 한다(web-security JwtConfig 공유, 같은 키 주입).
- 온보딩 허용 경로 없음 — 셀러센터 전 EP가 ACTIVE 전용(UserStatusFilter 화이트리스트 empty).
- 배포: ECS 서비스 `aechak-seller-api-dev` + ALB host 규칙(`seller-api-<env>` 도메인). JWT는 공개키만 주입한 검증 전용 모드 — 발급은 api 소관.

## 6. batch

```
boot/batch/src/main/kotlin/com/aechak/batch/
├── BatchApplication.kt
├── job/{도메인}/                 # Job/Step 정의 — ItemProcessor 등에서 UseCase 호출
└── support/
    └── BusinessSkipPolicy.kt    # BusinessException → errorCode 로깅 + 스킵 판단 (HTTP 개념 없음)
```

- 예외 소비 방식: web-common의 핸들러가 아니라 SkipPolicy/Listener에서 errorCode 기준 처리.
- 배치가 자체 발신하는 에러 코드의 status는 500 고정 (05 문서 ErrorCode 컨벤션).

## 7. admin — A-5 결정: MVP 제외

- MVP에서는 만들지 않는다 (입점 심사·신고 처리 등 운영은 DB/API 수동 — 60 문서).
- 생성 시점이 오면 api와 동일 구조/규칙(JSON API 기반)으로 만들고, web-common의 GlobalExceptionHandler를 재사용한다.
