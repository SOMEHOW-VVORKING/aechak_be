package com.aechak.api.trace

import com.aechak.webcommon.trace.TraceIdFilter
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/** [단위] traceId 필터 — MDC 키 값·정리 계약 검증. */
class TraceIdFilterTest {
    private val filter = TraceIdFilter()

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `요청 처리 동안 MDC의 traceId 키에 값을 넣고 끝나면 지운다`() {
        val request = MockHttpServletRequest()
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-from-client")
        var seenDuringRequest: String? = null
        val chain = FilterChain { _, _ -> seenDuringRequest = MDC.get("traceId") }

        filter.doFilter(request, MockHttpServletResponse(), chain)

        // 키를 리터럴로 단언하는 이유: 퍼블리셔·인터셉터·logback 패턴과 키 "값"의 정합을 고정한다
        assertThat(seenDuringRequest)
            .`as`("요청 처리 중에는 클라이언트가 보낸 traceId가 MDC에 있어야 한다")
            .isEqualTo("trace-from-client")
        assertThat(MDC.get("traceId"))
            .`as`("톰캣 스레드는 풀에서 재사용되므로 요청이 끝나면 지워져야 한다")
            .isNull()
    }
}
