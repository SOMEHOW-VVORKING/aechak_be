package com.aechak.api.config

import com.aechak.common.error.CommonErrorCode
import com.aechak.webcommon.error.ErrorResponse
import com.aechak.webcommon.error.GlobalExceptionHandler
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * "경로가 없다"를 스프링이 어떤 예외로 알리는지는 **구성에 따라 갈린다.** 둘 다 404로 떨어지는지 고정한다.
 *
 * - 정적 리소스 매핑이 켜져 있으면(기본값) 전 경로 패턴에 걸린 `ResourceHttpRequestHandler`가
 *   `NoResourceFoundException`을 던진다. [UnmappedRequestIntegrationTest]가 실 컨텍스트에서 보는 쪽이다.
 * - `spring.web.resources.add-mappings=false`로 끄면 그 매핑이 사라져 핸들러 조회가 빈손이 되고,
 *   `DispatcherServlet.noHandlerFound`가 `NoHandlerFoundException`을 던진다. Spring 7의
 *   `noHandlerFound`는 프로퍼티로 가려지지 않고 무조건 던지므로, 그 설정 한 줄에 404가 500으로 샌다.
 *
 * 두 번째 경로는 프로파일을 바꿔야 재현되므로 통합 테스트로 고정할 수 없다. 대신 **스프링이 실제로 쓰는
 * resolver**로 판정해서, 어노테이션에 타입을 빠뜨리거나 catch-all(500)이 먼저 걸리는 회귀를 잡는다.
 * (`ExceptionHandlerMethodResolver` 생성 자체가 매핑 중복·누락도 함께 검증한다.)
 */
class UnmappedRequestHandlerResolutionTest {
    private val handler = GlobalExceptionHandler()
    private val resolver = ExceptionHandlerMethodResolver(GlobalExceptionHandler::class.java)

    @Test
    @DisplayName("매핑 없는 경로를 알리는 두 예외 모두 404·90006 핸들러로 간다")
    fun bothUnmappedExceptionsResolveToNotFound() {
        val unmapped =
            listOf(
                NoResourceFoundException(HttpMethod.GET, "", "/api/v1/no-such-path"),
                NoHandlerFoundException("GET", "/api/v1/no-such-path", HttpHeaders()),
            )

        assertAll(
            unmapped.map { exception ->
                {
                    val response = dispatch(exception)
                    val where = exception.javaClass.simpleName
                    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode.value(), "상태코드가 404가 아니다 ($where)")
                    assertEquals(CommonErrorCode.ENDPOINT_NOT_FOUND.code, response.body?.errorCode, "errorCode가 다르다 ($where)")
                }
            },
        )
    }

    /** 스프링이 이 예외에 고를 핸들러를 실제로 조회해 호출한다 — 호출 대상을 테스트가 직접 고르지 않는다. */
    @Suppress("UNCHECKED_CAST")
    private fun dispatch(exception: Exception): ResponseEntity<ErrorResponse> {
        val method = resolver.resolveMethod(exception)
        assertNotNull(method, "핸들러가 없다 — catch-all이 500으로 내보낸다: ${exception.javaClass.simpleName}")
        return method.invoke(handler, exception) as ResponseEntity<ErrorResponse>
    }
}
