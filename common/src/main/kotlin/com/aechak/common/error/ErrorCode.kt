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
