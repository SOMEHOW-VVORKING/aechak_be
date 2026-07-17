package com.aechak.webcommon.error

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 웹 계열 실행 모듈(api/admin) 공용 전역 예외 처리기.
 *
 * - BusinessException: errorCode.status(int) → HttpStatus 변환. 여기가 유일한 변환 지점.
 * - @Valid 형식 검증 실패: 90002(INVALID_REQUEST)로 매핑하되 message에 필드별 사유를 싣는다(분기는 errorCode 고정).
 * - 그 외 Exception: 최후 방어선. 90001로 감싸고 스택트레이스는 로그로만 남긴다.
 *   (즉석 문자열 코드("C500" 등) 생성 금지 — errorCode 분기 일관성 유지)
 *
 * 실행 모듈에서 컴포넌트 스캔에 포함시켜 활성화한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalidRequest(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        // 제약의 message가 그대로 실린다 — 기본 메시지는 JVM 로케일 의존이라 제약 쪽에 message 명시가 전제.
        val detail =
            e.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
                .ifBlank { CommonErrorCode.INVALID_REQUEST.message }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, detail))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, "${e.parameterName}: 필수 파라미터가 누락되었습니다."))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, "${e.name}: 잘못된 값입니다."))

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
