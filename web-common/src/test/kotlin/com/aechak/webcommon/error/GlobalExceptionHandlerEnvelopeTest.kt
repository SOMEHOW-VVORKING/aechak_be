package com.aechak.webcommon.error

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.junit.jupiter.api.assertAll
import org.springframework.core.MethodParameter
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
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

    /**
     * 핸들러 이름 → 기대 상태·errorCode와 **그 핸들러를 실제로 호출하는 람다**.
     *
     * 아래 트립와이어가 비교하는 것이 이 맵의 키이므로, 이름만 적어 통과시킬 수 없다 —
     * 키를 적으려면 호출 람다를 함께 달아야 하고, 그 람다가 곧 여기서 검증되는 호출이다.
     */
    private val cases: Map<String, Case> =
        mapOf(
            "handleBusiness" to Case(400, 90002) { handler.handleBusiness(BusinessException(CommonErrorCode.INVALID_CURSOR)) },
            "handleInvalidRequest" to Case(400, 90001) { handler.handleInvalidRequest(invalidRequest()) },
            "handleTypeMismatch" to Case(400, 90001) { handler.handleTypeMismatch(typeMismatch()) },
            "handleUnreadableBody" to Case(400, 90001) { handler.handleUnreadableBody(unreadableBody()) },
            "handleMissingParameter" to
                Case(400, 90001) { handler.handleMissingParameter(MissingServletRequestParameterException("size", "int")) },
            "handleMissingHeader" to
                Case(400, 90001) { handler.handleMissingHeader(MissingRequestHeaderException("X-Trace-Id", sampleParameter())) },
            "handleConcurrentModification" to
                Case(409, 90003) { handler.handleConcurrentModification(OptimisticLockingFailureException("버전 충돌")) },
            "handleNoResource" to
                Case(404, 90006) { handler.handleNoResource(NoResourceFoundException(HttpMethod.GET, UNMAPPED_PATH, UNMAPPED_PATH)) },
            "handleNoHandler" to
                Case(404, 90006) { handler.handleNoHandler(NoHandlerFoundException("GET", UNMAPPED_PATH, HttpHeaders())) },
            "handleMethodNotAllowed" to Case(405, 90007) { handler.handleMethodNotAllowed(methodNotSupported()) },
            "handleUnsupportedMediaType" to Case(415, 90008) { handler.handleUnsupportedMediaType(unsupportedMediaType()) },
            "handleNotAcceptable" to
                Case(406, 90009) { handler.handleNotAcceptable(HttpMediaTypeNotAcceptableException(jsonOnly)) },
            "handleMalformedMultipart" to Case(400, 90010) { handler.handleMalformedMultipart(MultipartException("boundary 없음")) },
            "handleUploadTooLarge" to
                Case(413, 90011) { handler.handleUploadTooLarge(MaxUploadSizeExceededException(MAX_UPLOAD_BYTES)) },
            "handleUnexpected" to Case(500, 90000) { handler.handleUnexpected(IllegalStateException("예상 못한 오류")) },
        )

    @Test
    fun `모든 실패 응답은 JSON을 못박고 상태코드와 errorCode를 지킨다`() {
        // 하나가 깨져도 나머지를 함께 보고한다 — 못박기를 빠뜨린 핸들러가 둘 이상일 때 한 번에 드러난다.
        assertAll(cases.map { (name, case) -> { assertEnvelope(case.respond(), case.status, case.errorCode, name) } })
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

    /**
     * 405 응답은 그 경로가 받는 메서드를 Allow 헤더로 알려준다(HTTP 명세가 요구한다).
     * 못박기를 넣으면서 이 헤더를 떨어뜨리면 클라이언트가 무엇을 쓸지 알 수 없다.
     */
    @Test
    fun `405는 지원 메서드를 Allow 헤더에 담는다`() {
        val response = handler.handleMethodNotAllowed(methodNotSupported())

        assertEquals("GET", response.headers.getFirst(HttpHeaders.ALLOW), "405가 알려준 지원 메서드가 다르다")
    }

    /** 지원 메서드를 모를 때는 빈 Allow 헤더를 흘리지 않는다(415의 Accept와 같은 규칙). */
    @Test
    fun `지원 메서드를 모를 때는 Allow 헤더를 붙이지 않는다`() {
        val response = handler.handleMethodNotAllowed(HttpRequestMethodNotSupportedException("DELETE"))

        assertEquals(null, response.headers.getFirst(HttpHeaders.ALLOW))
        assertEnvelope(response, 405, 90007, "handleMethodNotAllowed")
    }

    /** 지원 타입이 비어 있으면 빈 Accept 헤더를 흘리지 않는다. */
    @Test
    fun `지원 타입을 모를 때는 Accept 헤더를 붙이지 않는다`() {
        val response = handler.handleUnsupportedMediaType(HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, emptyList()))

        assertEquals(null, response.headers.getFirst(HttpHeaders.ACCEPT))
        assertEnvelope(response, 415, 90008, "handleUnsupportedMediaType")
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
     * 그래서 "모든 `@ExceptionHandler`가 위 불변식 테스트를 거쳤는가"를 여기서 강제한다.
     * 새 핸들러를 추가하면 이 테스트가 먼저 빨개지고, [cases]에 한 줄 더 넣어야 통과한다.
     *
     * 비교 대상이 **[cases]의 키**인 것이 핵심이다. 이름 목록을 따로 들면 호출 없이 이름만 추가해
     * 트립와이어를 통과시킬 수 있다 — 키를 적는 유일한 방법이 호출 람다를 함께 다는 것이어야 한다.
     */
    @Test
    fun `모든 ExceptionHandler가 불변식 테스트를 거친다`() {
        val declared =
            GlobalExceptionHandler::class.java.declaredMethods
                .filter { it.isAnnotationPresent(ExceptionHandler::class.java) }
                .map { it.name }
                .toSortedSet()

        assertEquals(declared, cases.keys.toSortedSet(), UNCOVERED_HINT)
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
        handlerName: String,
    ) {
        val where = "$handlerName, errorCode=$expectedErrorCode"
        assertEquals(expectedStatus, response.statusCode.value(), "상태코드가 다르다 ($where)")
        assertEquals(expectedErrorCode, response.body?.errorCode, "errorCode가 다르다 ($where)")
        // 핵심 단언: 협상에 맡기지 않았는지.
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType, "Content-Type을 못박지 않았다 ($where)")
        assertTrue(!response.body?.message.isNullOrBlank(), "message가 비었다 ($where)")
    }

    private val jsonOnly = listOf(MediaType.APPLICATION_JSON)

    private fun unsupportedMediaType() = HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, jsonOnly)

    private fun methodNotSupported() = HttpRequestMethodNotSupportedException("DELETE", listOf("GET"))

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

    /** 기대값과 호출 방법을 한 묶음으로 든다 — 둘이 떨어져 있으면 호출 없는 이름이 생길 수 있다. */
    private class Case(
        val status: Int,
        val errorCode: Int,
        val respond: () -> ResponseEntity<ErrorResponse>,
    )

    companion object {
        private const val MAX_UPLOAD_BYTES = 1_048_576L
        private const val UNMAPPED_PATH = "/api/v1/no-such-path"
        private const val FIELD_REASON = "size는 1~100 사이여야 합니다."

        private const val UNCOVERED_HINT =
            "@ExceptionHandler가 추가·삭제됐다. 새 핸들러도 Content-Type을 JSON으로 못박아야 한다 — " +
                "cases 맵에 \"핸들러 이름 to Case(상태, errorCode) { 호출 }\" 한 줄을 추가하라."
    }
}
