package com.aechak.webcommon.error

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.springframework.core.MethodParameter
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpInputMessage
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실패 응답 봉투의 불변식: **모든 핸들러가 Content-Type을 application/json으로 못박는다.**
 *
 * 하나라도 빠지면 그 응답은 요청 Accept와 협상을 거치고, 협상이 깨지는 요청(`Accept: application/xml`,
 * 문법이 깨진 Accept)에서 봉투를 쓸 수 없어 MVC가 처리를 포기한다 → `sendError`가 `/error`를 띄우고
 * ERROR 디스패치에 걸린 시큐리티가 공개 경로에서도 401 "로그인이 필요합니다"를 쓴다.
 * dev 실측(2026-10-26, 수정 전)에서 404·400·BusinessException이 전부 그 401로 둔갑했다.
 *
 * 상태코드·errorCode까지 함께 고정해, 못박기를 핑계로 상태코드가 뭉개지는 것도 막는다.
 */
class GlobalExceptionHandlerEnvelopeTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `모든 실패 응답은 JSON을 못박고 상태코드와 errorCode를 지킨다`() {
        assertEnvelope(handler.handleBusiness(BusinessException(CommonErrorCode.INVALID_CURSOR)), 400, 90002)
        assertEnvelope(handler.handleInvalidRequest(invalidRequest()), 400, 90001)
        assertEnvelope(handler.handleTypeMismatch(typeMismatch()), 400, 90001)
        assertEnvelope(handler.handleUnreadableBody(unreadableBody()), 400, 90001)
        assertEnvelope(handler.handleMissingParameter(MissingServletRequestParameterException("size", "int")), 400, 90001)
        assertEnvelope(handler.handleMissingHeader(MissingRequestHeaderException("X-Trace-Id", sampleParameter())), 400, 90001)
        assertEnvelope(handler.handleConcurrentModification(OptimisticLockingFailureException("버전 충돌")), 409, 90003)
        assertEnvelope(handler.handleUnsupportedMediaType(unsupportedMediaType()), 415, 90008)
        assertEnvelope(handler.handleNotAcceptable(HttpMediaTypeNotAcceptableException(jsonOnly)), 406, 90009)
        assertEnvelope(handler.handleUnexpected(IllegalStateException("예상 못한 오류")), 500, 90000)
    }

    /**
     * 415 응답은 클라이언트가 보낼 수 있는 Content-Type을 알려준다(스프링 기본 resolver와 같은 자리).
     * 존재만 보면 값이 엉뚱해도 통과하므로 파싱해서 `application/json`이 실제로 들어있는지 본다.
     */
    @Test
    fun `415는 지원 Content-Type을 Accept 헤더에 담는다`() {
        val response = handler.handleUnsupportedMediaType(unsupportedMediaType())

        val advertised = response.headers.getFirst(HttpHeaders.ACCEPT)
        assertTrue(advertised != null, "415 응답에 Accept 헤더가 없다")
        assertTrue(
            MediaType.parseMediaTypes(advertised).any { it.isCompatibleWith(MediaType.APPLICATION_JSON) },
            "415가 알려준 지원 타입에 JSON이 없다: $advertised",
        )
    }

    /** 지원 타입이 비어 있으면 빈 Accept 헤더를 흘리지 않는다. */
    @Test
    fun `지원 타입을 모를 때는 Accept 헤더를 붙이지 않는다`() {
        val response = handler.handleUnsupportedMediaType(HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, emptyList()))

        assertEquals(null, response.headers.getFirst(HttpHeaders.ACCEPT))
        assertEnvelope(response, 415, 90008)
    }

    /** 415 응답에 스프링 기본 문구·패키지명 같은 내부 구현이 실리지 않는다. */
    @Test
    fun `415 응답에 내부 구현이 실리지 않는다`() {
        val body = handler.handleUnsupportedMediaType(unsupportedMediaType()).body!!

        assertEquals(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.message, body.message)
        listOf("com.aechak", "org.springframework", "Content-Type", "text/plain").forEach {
            assertTrue(!body.message.contains(it), "내부 구현이 응답 message에 실렸다($it): ${body.message}")
        }
    }

    /**
     * 핸들러를 새로 추가하면서 못박기를 빠뜨리는 것이 이 결함의 재발 경로다.
     * 그래서 "모든 @ExceptionHandler가 위 불변식 테스트를 거쳤는가"를 여기서 강제한다.
     * 새 핸들러를 추가하면 이 테스트가 먼저 빨개지고, 위 목록에 한 줄 더 넣어야 통과한다.
     */
    @Test
    fun `모든 ExceptionHandler가 불변식 테스트를 거친다`() {
        val declared =
            GlobalExceptionHandler::class.java.declaredMethods
                .filter { it.isAnnotationPresent(ExceptionHandler::class.java) }
                .map { it.name }
                .toSortedSet()

        assertEquals(COVERED_HANDLERS, declared, UNCOVERED_HINT)
    }

    /** 필드 사유를 그대로 싣는 분기(message가 errorCode 기본 문구로 뭉개지지 않는지도 같이 본다). */
    @Test
    fun `검증 실패는 필드 사유를 message에 싣는다`() {
        assertEquals(FIELD_REASON, handler.handleInvalidRequest(invalidRequest()).body?.message)
    }

    private fun assertEnvelope(
        response: ResponseEntity<ErrorResponse>,
        expectedStatus: Int,
        expectedErrorCode: Int,
    ) {
        val where = "errorCode=$expectedErrorCode"
        assertEquals(expectedStatus, response.statusCode.value(), "상태코드가 다르다 ($where)")
        assertEquals(expectedErrorCode, response.body?.errorCode, "errorCode가 다르다 ($where)")
        // 핵심 단언: 협상에 맡기지 않았는지.
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType, "Content-Type을 못박지 않았다 ($where)")
        assertTrue(!response.body?.message.isNullOrBlank(), "message가 비었다 ($where)")
    }

    private val jsonOnly = listOf(MediaType.APPLICATION_JSON)

    private fun unsupportedMediaType() = HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, jsonOnly)

    private fun unreadableBody() =
        HttpMessageNotReadableException(
            "본문 파싱 실패",
            object : HttpInputMessage {
                override fun getHeaders(): HttpHeaders = HttpHeaders()

                override fun getBody(): InputStream = ByteArrayInputStream(ByteArray(0))
            },
        )

    private fun invalidRequest(): MethodArgumentNotValidException {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(FieldError("request", "size", FIELD_REASON))
        return MethodArgumentNotValidException(sampleParameter(), bindingResult)
    }

    private fun typeMismatch() =
        MethodArgumentTypeMismatchException("abc", Int::class.javaObjectType, "size", sampleParameter(), NumberFormatException("abc"))

    private fun sampleParameter() = MethodParameter(javaClass.getDeclaredMethod("sampleParameterTarget", String::class.java), 0)

    @Suppress("UNUSED_PARAMETER", "unused")
    private fun sampleParameterTarget(header: String) = Unit

    companion object {
        private const val FIELD_REASON = "size는 1~100 사이여야 합니다."

        /** 위 불변식 테스트가 직접 호출해 검증하는 핸들러 전수. */
        private val COVERED_HANDLERS =
            sortedSetOf(
                "handleBusiness",
                "handleConcurrentModification",
                "handleInvalidRequest",
                "handleMissingHeader",
                "handleMissingParameter",
                "handleNotAcceptable",
                "handleTypeMismatch",
                "handleUnexpected",
                "handleUnreadableBody",
                "handleUnsupportedMediaType",
            )

        private const val UNCOVERED_HINT =
            "@ExceptionHandler가 추가·삭제됐다. 새 핸들러도 Content-Type을 JSON으로 못박아야 한다 — " +
                "`모든 실패 응답은 JSON을 못박고...` 테스트에 한 줄 추가하고 이 목록을 갱신하라."
    }
}
