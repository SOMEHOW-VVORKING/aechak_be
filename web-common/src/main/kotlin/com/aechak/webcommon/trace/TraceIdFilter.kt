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
