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
    detail: String? = null,
) : RuntimeException(detail ?: errorCode.message, cause)
