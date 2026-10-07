package com.aechak.webcommon.error

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 웹 계열 실행 모듈(api/admin) 공용 전역 예외 처리기.
 *
 * - BusinessException: errorCode.status(int) → HttpStatus 변환. 여기가 유일한 변환 지점.
 * - @Valid 형식 검증 실패: 90001(INVALID_REQUEST)로 매핑하되 message에 필드별 사유를 싣는다(분기는 errorCode 고정).
 * - 본문 파싱 실패(필수 필드 누락·타입 오류·깨진 JSON): 400. 이 핸들러가 없으면 아래 catch-all이 먼저 잡아
 *   클라이언트 버그가 5xx 알람으로 둔갑한다. 파싱 예외 메시지는 페이로드 조각(PII)을 물고 올 수 있어 싣지 않는다.
 * - 그 외 Exception: 최후 방어선. 90000으로 감싸고 스택트레이스는 로그로만 남긴다.
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
                .mapNotNull { it.defaultMessage }
                .joinToString(", ")
                .ifBlank { CommonErrorCode.INVALID_REQUEST.message }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, detail))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, "요청 본문을 읽을 수 없습니다."))

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
        // message는 detail이 실렸으면 그것을(예: 10105 부족 목록), 아니면 errorCode 기본 문구를 따른다 — 분기는 errorCode 고정.
        return ResponseEntity
            .status(HttpStatus.valueOf(e.errorCode.status))
            .body(ErrorResponse(e.errorCode.code, e.message ?: e.errorCode.message))
    }

    // @Version 엔티티는 flush 시점에 터져 서비스 코드로는 못 잡음. 안 걸러주면 500으로 샘.
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleConcurrentModification(e: OptimisticLockingFailureException): ResponseEntity<ErrorResponse> {
        log.warn("낙관적 락 충돌", e)
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(CommonErrorCode.CONCURRENT_MODIFICATION))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("unexpected exception", e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR))
    }
}
