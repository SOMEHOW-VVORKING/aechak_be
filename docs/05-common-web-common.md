# 애착 — common / web-common 모듈 초기 세팅 스펙

> 이 문서는 AI 코딩 에이전트에게 그대로 전달하여 모듈 초기 구조를 생성하기 위한 스펙이다.
> 코드 블록은 **템플릿**이며, `// TODO:` 주석 부분만 실제 프로젝트 값으로 채운다.
> 여기 명시된 규칙과 충돌하는 임의 판단(필드 추가, 패키지 변경 등)은 하지 않는다.

---

## 0. 설계 원칙 요약 (변경 금지)

1. **성공 응답**: `data` 필드로 감싼다. 성공 여부는 HTTP Status(2xx)로만 판별. 본문에 `code`/`message`/`timestamp`/`status` 래퍼 없음.
2. **실패 응답**: `errorCode`(int) + `message`. 클라이언트는 `errorCode`로 분기한다. `timestamp` 없음.
3. **추적**: `X-Trace-Id` 응답 헤더. 모든 응답(성공/실패)에 포함. MDC 연동.
4. **에러 코드 체계**: 컨텍스트별 int 대역.

   | 대역 | 컨텍스트 | enum 파일 |
   | --- | --- | --- |
   | 10000 | seller | `SellerErrorCode` |
   | 20000 | auth | `AuthErrorCode` |
   | 30000 | user | `UserErrorCode` |
   | 40000 | product | `ProductErrorCode` |
   | 50000 | order (배송 흡수) | `OrderErrorCode` |
   | 60000 | payment | `PaymentErrorCode` |
   | 70000 | settlement | `SettlementErrorCode` |
   | 80000 | search | 대역 예약 — enum 없음 |
   | 90000 | 서버 공통 | `CommonErrorCode` |
   | 100000 | file | `FileErrorCode` |
   | 110000 | review | `ReviewErrorCode` |
   | 120000 | inquiry | `InquiryErrorCode` |

   - **애그리거트 100번대**: 대역 안에서 애그리거트 **루트** 하나가 100번대 하나를 받고, 그 안은 00번부터 채운다
     (`User` 30000~, `PointTransaction` 30100~, `DeliveryAddress` 30500~). 배정 단위는 패키지가 아니라 루트이고,
     판별 기준은 **자체 리포지토리 포트를 갖는가**다. 한 패키지를 공유하는 애그리거트라도 루트마다 100번대를
     따로 받는다 — `pet/`의 `PetProfile`(30600~)과 `Breed`(30700~)가 그 예다.
     배정 순서는 **선점 순** — 먼저 코드가 생긴 루트가 앞 100번대를 갖는다.
     **코드가 없는 애그리거트에는 번호를 미리 잡아두지 않는다** — 첫 코드가 필요해진 시점에
     그 대역의 다음 빈 100번대를 준다. enum 파일은 **BC당 하나**이므로 100번대 구분이 파일을 쪼개지는 않는다.
     소속은 **검증 대상이 어느 애그리거트인가**로 정한다 — `INVALID_BREED`는 펫 등록 중에 던지지만
     검증 대상이 `Breed`이므로 `Breed`의 100번대다.
     `auth`·`file`·`common`처럼 애그리거트가 없는 컨텍스트는 나눌 단위가 없으므로 100번대를 쓰지 않고 대역 시작부터 순차로 채운다.
   - **BC 격리**: 대역을 소유하는 단위는 BC다. 서버를 BC 단위로 분리할 수 있어야 하므로
     **다른 BC의 에러 코드를 던지거나 응답으로 노출하지 않는다.** 예외는 둘이다.
     ① 90000번대는 BC가 아니라 플랫폼 소유라 어느 서버가 내든 위반이 아니다.
     ② `auth`·`file`처럼 애그리거트가 없는 application 전용 컨텍스트는 자기 대역을 갖되 BC 지위는 미확정으로 둔다.
     이 예외는 **대역을 소유한다**는 데까지만 미친다 — 이 컨텍스트들도 남의 BC 대역을 던지거나 노출하는 것은 위반이다.

     > **현황(시점 의존 — 원칙이 아니라 지금의 코드 상태다)**: 위반이 하나 남아 있다 —
     > `SocialLoginService`가 소셜 로그인 중 `UserErrorCode`를 던진다.
     > 던지는 코드를 바꾸면 응답 상태코드가 달라지므로 번호 체계와 분리해 다룬다.
5. **의존 방향**: `도메인/실행 모듈 → common` 단방향. common은 아무것도 의존하지 않는다.
   - `common`: Spring 의존 **없음** (순수 Kotlin/Java). HTTP 상태는 raw `int`로만 표현.
   - `web-common`: `common` + `spring-web` 의존. HTTP 번역(직렬화, 상태코드 변환, 필터)은 전부 여기.
6. **common 입장 기준**: "의존성이 없어서"가 아니라 **"둘 이상의 실행 모듈이 실제로 쓰는가"**. ErrorResponse는 웹 계열(api/admin)만 쓰므로 web-common 소속.

---

## 1. 모듈 / 패키지 구조

```
project-root/
├── common/                                  # 순수 모듈. Spring 의존 없음
│   └── src/main/kotlin/com/aechak/common/
│       └── error/
│           ├── ErrorCode.kt                 # 인터페이스 (규약)
│           ├── BusinessException.kt         # 베이스 예외
│           └── CommonErrorCode.kt           # 90000번대 서버 공통 enum
│
├── web-common/                              # common + spring-web 의존
│   └── src/main/kotlin/com/aechak/webcommon/
│       ├── response/
│       │   └── ApiResponse.kt               # 성공 응답 래퍼
│       ├── error/
│       │   ├── ErrorResponse.kt             # 실패 응답 본문 (wire format)
│       │   └── GlobalExceptionHandler.kt    # @RestControllerAdvice
│       └── trace/
│           └── TraceIdFilter.kt             # X-Trace-Id 필터
│
├── domain/                                  # 도메인 BC. common만 의존
│   └── src/main/kotlin/com/aechak/domain/
│       └── {bc}/error/
│           └── {Bc}ErrorCode.kt             # ErrorCode 구현 enum
│                                            #   seller / user / product / order / payment / settlement / review
│
└── application/                             # 유스케이스 조율 계층. domain 의존
    └── src/main/kotlin/com/aechak/application/
        ├── auth/error/
        │   └── AuthErrorCode.kt             # 20000번대 — 애그리거트가 없어 domain이 아닌 여기 소속
        └── file/error/
            └── FileErrorCode.kt             # 100000번대 — 위와 같은 이유
```

- BC별 에러 enum은 **그 BC의 패키지가 소유**한다. common에 몰아넣지 않는다 (머지 충돌 핫스팟 방지, 응집도 유지).
  애그리거트를 갖는 BC는 `domain` 모듈에, 애그리거트가 없는 `auth`·`file`은 `application` 모듈에 둔다.
- 90000번대(CommonErrorCode)만 예외적으로 common 소속 — 특정 BC에 속하지 않고 api/batch 어디서나 필요하므로.

---

## 2. Gradle 의존성 규칙

> 빌드 파일의 최종 형태(컨벤션 플러그인 + 버전 카탈로그)는 **50-build 문서 §5가 단일 출처**다.
> 이 문서 작성 시점의 스니펫은 이후 결정(도메인 = 단일 모듈 내 패키지, A-1 등)으로 대체되어 삭제함.
> 이 문서가 소유하는 규칙 두 가지만 남긴다:

- `web-common → common`은 **api**로 노출한다 — web-common을 무는 실행 모듈(api/admin)이
  ErrorCode/BusinessException을 직접 쓰기 때문. (`api(project(":common"))`)
- common은 의존성이 **의도적으로 비어 있는** 모듈이다. spring-web, spring-context 등 추가 금지.

---

## 3. common 모듈 코드 템플릿

### 3-1. ErrorCode.kt

```kotlin
package com.aechak.common.error

/**
 * 에러 코드 규약.
 *
 * - code: 도메인별 int 체계 (예: 20001). 클라이언트 분기 기준.
 * - message: 사용자에게 노출 가능한 한국어 메시지.
 * - status: HTTP 상태의 raw int (예: 404).
 *   spring-web의 HttpStatus 타입을 피하기 위해 int로 둔다.
 *   HttpStatus로의 변환은 web-common(GlobalExceptionHandler)에서만 수행한다.
 *
 * [배치 전용 에러 코드 주의]
 * HTTP로 노출되지 않는 에러 코드(배치 발신 등)는 status에 500을 채우는 것을
 * 팀 컨벤션으로 한다. 배치 발신 코드가 유의미하게 늘어나면
 * ErrorCode / HttpAwareErrorCode 인터페이스 분리(ISP)로 리팩토링한다.
 */
interface ErrorCode {
    val code: Int
    val message: String
    val status: Int
}
```

### 3-2. BusinessException.kt

```kotlin
package com.aechak.common.error

/**
 * 도메인 로직에서 던지는 베이스 예외.
 * ErrorCode 인터페이스 타입만 참조하므로 어떤 도메인 enum이든 담을 수 있다.
 *
 * 소비 방식은 실행 모듈이 결정한다:
 * - api/admin: GlobalExceptionHandler → ErrorResponse JSON
 * - batch: SkipPolicy/Listener → 로그 + 스킵/재시도 판단
 * - kafka consumer: errorCode 기준 DLT 라우팅
 */
open class BusinessException(
    val errorCode: ErrorCode,
    cause: Throwable? = null,
) : RuntimeException(errorCode.message, cause)
```

### 3-3. CommonErrorCode.kt

```kotlin
package com.aechak.common.error

/**
 * 서버 공통(90000번대) 에러 코드.
 * 특정 도메인에 속하지 않아 common에 위치하는 유일한 enum.
 */
enum class CommonErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {
    INTERNAL_SERVER_ERROR(90000, "서버 오류가 발생했습니다.", 500),
    INVALID_REQUEST(90001, "잘못된 요청입니다.", 400),
    INVALID_CURSOR(90002, "유효하지 않은 커서입니다.", 400),
    CONCURRENT_MODIFICATION(90003, "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도해 주세요.", 409),
}
```

---

## 4. web-common 모듈 코드 템플릿

### 4-1. response/ApiResponse.kt

```kotlin
package com.aechak.webcommon.response

/**
 * 성공 응답 래퍼. 데이터를 data 필드로 감싼다.
 *
 * 규칙:
 * - 성공 여부는 HTTP Status(2xx)로 판별. 본문에 code/status/timestamp 없음.
 * - 반환할 데이터가 없는 성공(생성/삭제)은 이 클래스를 쓰지 않고
 *   빈 본문 + HTTP Status(201/204)로만 표현한다.
 */
data class ApiResponse<T>(
    val data: T,
) {
    companion object {
        fun <T> of(data: T): ApiResponse<T> = ApiResponse(data)
    }
}
```

### 4-2. error/ErrorResponse.kt

```kotlin
package com.aechak.webcommon.error

import com.aechak.common.error.ErrorCode

/**
 * 실패 응답 본문 (HTTP wire format).
 *
 * 규칙:
 * - errorCode(int) + message만. timestamp 없음(추적은 X-Trace-Id 헤더).
 * - status는 본문에 넣지 않음 (HTTP Status로 전달).
 * - 클라이언트는 message가 아닌 errorCode로 분기한다.
 *
 * 위치가 common이 아닌 이유: 이 형태를 결정하는 것은 API 응답 규격이며,
 * 소비자가 웹 계열 실행 모듈(api/admin)뿐이다. batch는 사용하지 않는다.
 */
data class ErrorResponse(
    val errorCode: Int,
    val message: String,
) {
    companion object {
        fun of(errorCode: ErrorCode): ErrorResponse =
            ErrorResponse(errorCode.code, errorCode.message)
    }
}
```

### 4-3. error/GlobalExceptionHandler.kt

```kotlin
package com.aechak.webcommon.error

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 웹 계열 실행 모듈(api/admin) 공용 전역 예외 처리기.
 *
 * - BusinessException: errorCode.status(int) → HttpStatus 변환. 여기가 유일한 변환 지점.
 * - 그 외 Exception: 최후 방어선. 90000으로 감싸고 스택트레이스는 로그로만 남긴다.
 *   (즉석 문자열 코드("C500" 등) 생성 금지 — errorCode 분기 일관성 유지)
 *
 * 실행 모듈에서 컴포넌트 스캔에 포함시켜 활성화한다.
 * @Valid 검증 실패(MethodArgumentNotValidException)는 90001로 매핑한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ErrorResponse> {
        // TODO: 필요 시 warn 레벨 로깅 (traceId는 MDC로 자동 포함)
        return ResponseEntity
            .status(HttpStatus.valueOf(e.errorCode.status))
            .body(ErrorResponse.of(e.errorCode))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("unexpected exception", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR))
    }
}
```

### 4-4. trace/TraceIdFilter.kt

```kotlin
package com.aechak.webcommon.trace

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 모든 요청에 X-Trace-Id를 부여하는 필터.
 *
 * - 요청 헤더에 X-Trace-Id가 있으면 전파, 없으면 생성.
 * - MDC("traceId")에 넣어 로그 상관관계 확보, 응답 헤더에도 항상 포함.
 * - 성공/실패 관계없이 모든 응답에 적용.
 *
 * TODO: 실행 모듈에서 FilterRegistrationBean 또는 @Component로 등록.
 *       logback 패턴에 %X{traceId} 포함할 것.
 */
class TraceIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val traceId = request.getHeader(TRACE_ID_HEADER) ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, traceId)
        response.setHeader(TRACE_ID_HEADER, traceId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }

    companion object {
        const val TRACE_ID_HEADER = "X-Trace-Id"
        const val MDC_KEY = "traceId"
    }
}
```

---

## 5. BC 에러 코드 템플릿 (참고용 — 각 BC 담당자가 작성)

```kotlin
package com.aechak.domain.user.error   // TODO: BC별 패키지

import com.aechak.common.error.ErrorCode

/**
 * {BC} 에러 코드. 이 BC가 소유하며 파일은 BC당 하나다.
 *
 * 코드 대역: TODO (사용자 30000~ / 주문 50000~ / 결제 60000~ — §0-4 대역표)
 * 대역 안은 애그리거트 루트마다 100번대 하나, 그 안은 00번부터.
 * 새 코드 추가 시 이 파일만 수정 — common/web-common 변경 불필요.
 */
enum class UserErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    USER_NOT_FOUND(30000, "사용자를 찾을 수 없습니다.", 404),
    DUPLICATE_NICKNAME(30001, "이미 사용 중인 닉네임입니다.", 409),
    // TODO: BC 기능 구현하며 추가
}
```

**던지는 쪽 사용 예 (템플릿):**

```kotlin
fun findById(id: Long): User =
    userRepository.findByIdOrNull(id)
        ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)
```

**컨트롤러 사용 예 (템플릿):**

```kotlin
@GetMapping("/users/{id}")
fun getUser(@PathVariable id: Long): ResponseEntity<ApiResponse<UserResponse>> =
    ResponseEntity.ok(ApiResponse.of(userService.getUser(id)))

@PostMapping("/users")
fun signUp(@RequestBody request: SignUpRequest): ResponseEntity<Void> {
    userService.signUp(request)
    return ResponseEntity.status(HttpStatus.CREATED).build()   // 빈 본문
}
```

---

## 6. 운영 컨벤션 (데이터 추가/관리 규칙)

1. **새 에러 코드 추가**: 해당 BC의 enum에만 추가한다. 검증 대상 애그리거트의 100번대에서 다음 번호를 쓴다. code/message/status를 선언부에서 한 번에 정의 (원격 매핑 테이블 없음 → 누락 사고 원천 차단).
2. **새 애그리거트 루트의 첫 에러 코드**: 배정 시점은 루트를 만드는 때가 아니라 **그 루트의 코드가 처음 필요해진 때**다. 그 시점에 BC 대역의 다음 빈 100번대를 배정하고 00번부터 쓴다. 코드가 없는 루트에는 번호를 미리 잡아두지 않는다. 패키지를 기존 루트와 공유해도 100번대는 따로 받는다. enum 파일은 BC당 하나이므로 늘리지 않는다.
3. **새 BC 추가**: 대역을 먼저 할당(§0-4 표 갱신)하고, 해당 패키지에 `{BC}ErrorCode` enum 생성.
4. **90000번대**: CommonErrorCode에만 추가. BC enum에서 90000번대 사용 금지.
5. **status 값**: 대부분 400. NOT_FOUND 계열 404, 중복 409, 인증 401/403, 외부 연동 실패 502. 배치 등 비HTTP 발신 코드는 500 고정.
6. **금지 사항**:
   - common에 Spring 의존성 추가 금지
   - 도메인 모듈이 web-common 의존 금지
   - ErrorResponse에 timestamp/status 필드 추가 금지
   - 핸들러에서 즉석 에러 코드 문자열/숫자 생성 금지 (반드시 enum 참조)
