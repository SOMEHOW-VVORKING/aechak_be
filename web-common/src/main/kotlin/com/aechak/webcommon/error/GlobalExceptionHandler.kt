package com.aechak.webcommon.error

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException

/**
 * 웹 계열 실행 모듈(api/seller/admin) 공용 전역 예외 처리기.
 *
 * - BusinessException: errorCode.status(int) → HttpStatus 변환. 여기가 유일한 변환 지점.
 * - @Valid 형식 검증 실패: 90001(INVALID_REQUEST)로 매핑하되 message에 필드별 사유를 싣는다(분기는 errorCode 고정).
 * - 본문 파싱 실패(필수 필드 누락·타입 오류·깨진 JSON): 400. 이 핸들러가 없으면 아래 catch-all이 먼저 잡아
 *   클라이언트 버그가 5xx 알람으로 둔갑한다. 파싱 예외 메시지는 페이로드 조각(PII)을 물고 올 수 있어 싣지 않는다.
 * - 미디어 타입 불일치: 415·406. 같은 이유로 catch-all보다 먼저 잡는다.
 * - 멀티파트 요청 오류: 400·413. 업로드를 받는 엔드포인트가 없어도 도달한다.
 * - 그 외 Exception: 최후 방어선. 90000으로 감싸고 스택트레이스는 로그로만 남긴다.
 *   (즉석 문자열 코드("C500" 등) 생성 금지 — errorCode 분기 일관성 유지)
 *
 * 모든 실패 응답은 [envelope]을 지난다 — Content-Type을 JSON으로 못박는 유일한 지점이다.
 *
 * 실행 모듈에서 컴포넌트 스캔에 포함시켜 활성화한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실패 응답 봉투를 만드는 유일한 지점. Content-Type을 JSON으로 못박는다.
     *
     * 못박지 않으면 이 봉투도 요청 Accept와 협상을 거치고, 협상이 깨지는 요청(`Accept: application/xml`,
     * 문법이 깨진 Accept)에서는 봉투를 쓸 수 없어 MVC가 처리를 포기한다 — `sendError`가 ERROR 디스패치를
     * 띄우고, 거기 걸린 시큐리티가 공개 경로에서도 401을 쓴다. 못박으면 Accept를 아예 보지 않는다.
     *
     * [advertise]는 415가 "이 형식으로 보내라"고 알려줄 타입이다(그 외에는 비운다).
     */
    private fun envelope(
        status: HttpStatus,
        body: ErrorResponse,
        advertise: List<MediaType> = emptyList(),
    ): ResponseEntity<ErrorResponse> {
        val builder =
            ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
        advertise.takeIf { it.isNotEmpty() }?.let { builder.header(HttpHeaders.ACCEPT, MediaType.toString(it)) }
        return builder.body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalidRequest(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        // 제약의 message가 그대로 실린다 — 기본 메시지는 JVM 로케일 의존이라 제약 쪽에 message 명시가 전제.
        val detail =
            e.bindingResult.fieldErrors
                .mapNotNull { it.defaultMessage }
                .joinToString(", ")
                .ifBlank { CommonErrorCode.INVALID_REQUEST.message }
        return envelope(HttpStatus.BAD_REQUEST, ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, detail))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        envelope(HttpStatus.BAD_REQUEST, ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, "요청 본문을 읽을 수 없습니다."))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
        envelope(
            HttpStatus.BAD_REQUEST,
            ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, "${e.parameterName}: 필수 파라미터가 누락되었습니다."),
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(e: MissingRequestHeaderException): ResponseEntity<ErrorResponse> =
        envelope(
            HttpStatus.BAD_REQUEST,
            ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, "${e.headerName}: 필수 헤더가 누락되었습니다."),
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        envelope(HttpStatus.BAD_REQUEST, ErrorResponse(CommonErrorCode.INVALID_REQUEST.code, "${e.name}: 잘못된 값입니다."))

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ErrorResponse> {
        // TODO: 필요 시 warn 레벨 로깅 (traceId는 MDC로 자동 포함)
        // message는 detail이 실렸으면 그것을(예: 10105 부족 목록), 아니면 errorCode 기본 문구를 따른다 — 분기는 errorCode 고정.
        return envelope(
            HttpStatus.valueOf(e.errorCode.status),
            ErrorResponse(e.errorCode.code, e.message ?: e.errorCode.message),
        )
    }

    // @Version 엔티티는 flush 시점에 터져 서비스 코드로는 못 잡음. 안 걸러주면 500으로 샘.
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleConcurrentModification(e: OptimisticLockingFailureException): ResponseEntity<ErrorResponse> {
        log.warn("낙관적 락 충돌", e)
        return envelope(HttpStatus.CONFLICT, ErrorResponse.of(CommonErrorCode.CONCURRENT_MODIFICATION))
    }

    // 본문을 읽을 변환기가 없는 Content-Type. 안 걸러주면 catch-all이 500으로 내보낸다.
    // 지원 타입을 Accept 헤더로 알려준다 — 요청 형식 계약이라 내부 구성이 새지 않는다.
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(e: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> {
        log.debug("지원하지 않는 Content-Type {}", e.contentType)
        return envelope(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            ErrorResponse.of(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE),
            advertise = e.supportedMediaTypes,
        )
    }

    // 협상할 수 있는 타입이 없거나 Accept 문법이 깨진 경우. 둘 다 못박힌 봉투여야 응답이 나간다.
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleNotAcceptable(e: HttpMediaTypeNotAcceptableException): ResponseEntity<ErrorResponse> {
        log.debug("협상 불가한 Accept — 지원 {}", e.supportedMediaTypes)
        return envelope(HttpStatus.NOT_ACCEPTABLE, ErrorResponse.of(CommonErrorCode.NOT_ACCEPTABLE))
    }

    /**
     * 업로드 용량 초과. 업로드를 받는 엔드포인트가 없어도 도달한다 — `DispatcherServlet`이 핸들러를 찾기
     * 전에 `checkMultipart`를 돌리고, 멀티파트 resolver는 요청 Content-Type만 보고 파싱한다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(e: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> {
        log.debug("업로드 용량 초과 — 상한 {}bytes", e.maxUploadSize)
        return envelope(HttpStatus.CONTENT_TOO_LARGE, ErrorResponse.of(CommonErrorCode.PAYLOAD_TOO_LARGE))
    }

    /** 멀티파트 파싱 실패(boundary 누락·끊긴 본문). 위와 같은 이유로 도달하고, 전부 클라이언트가 만든 요청이다. */
    @ExceptionHandler(MultipartException::class)
    fun handleMalformedMultipart(e: MultipartException): ResponseEntity<ErrorResponse> {
        log.debug("멀티파트 파싱 실패")
        return envelope(HttpStatus.BAD_REQUEST, ErrorResponse.of(CommonErrorCode.MALFORMED_MULTIPART))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("unexpected exception", e)
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.of(CommonErrorCode.INTERNAL_SERVER_ERROR))
    }
}
