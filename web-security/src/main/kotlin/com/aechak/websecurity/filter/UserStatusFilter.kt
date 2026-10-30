package com.aechak.websecurity.filter

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.webcommon.error.ErrorResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

/**
 * 인가 상태검증 필터 — BearerTokenAuthenticationFilter 바로 뒤에 배치.
 * JWT엔 상태를 박지 않고 매 요청 users.status를 프로젝션 조회한다 — 정지·탈퇴가 재로그인 없이 즉시 반영.
 *
 * - SUSPENDED / BANNED / WITHDRAWN / 유저 없음(탈퇴 직후 옛 토큰) → 403(20005)
 * - RESTRICTED → 제한 경로의 쓰기만 403(20013). 로그인·조회·주문은 통과한다
 * - PENDING_ONBOARDING → 온보딩 허용 목록 외 403(20006)
 * - 응답은 직접 쓴다: 이 위치는 ExceptionTranslationFilter보다 앞이라 AccessDeniedHandler가 못 받고,
 *   @RestControllerAdvice 밖이기도 하다 — 401 EntryPoint와 동일 패턴.
 * - @Component가 아닌 이유: Filter 빈은 Boot가 서블릿 체인에도 자동 등록해 이중 실행되므로
 *   SecurityConfig가 직접 생성해 체인에만 꽂는다.
 */
class UserStatusFilter(
    private val userStatusReader: UserStatusReader,
    private val objectMapper: ObjectMapper,
    /** PENDING_ONBOARDING 허용 경로 — 정책은 조립 지점(SecurityConfig) 소유, 이 필터는 메커니즘만 담당한다. */
    private val onboardingAllowedPaths: Set<String>,
    /** 이용 제한(RESTRICTED) 계정에게 막을 경로 접두 — 같은 이유로 정책은 조립 지점 소유다. */
    private val restrictedWritePathPrefixes: Set<String>,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val principal =
            SecurityContextHolder.getContext().authentication?.principal as? AuthPrincipal
                ?: return filterChain.doFilter(request, response) // 비인증(permitAll) 경로

        when (userStatusReader.statusOf(principal.userId)) {
            UserStatus.ACTIVE -> {
                Unit
            }

            UserStatus.RESTRICTED -> {
                if (isRestrictedWrite(request)) {
                    return writeError(response, AuthErrorCode.ACCOUNT_RESTRICTED)
                }
            }

            UserStatus.PENDING_ONBOARDING -> {
                if (request.requestURI !in onboardingAllowedPaths) {
                    return writeError(response, AuthErrorCode.ONBOARDING_REQUIRED)
                }
            }

            UserStatus.SUSPENDED, UserStatus.BANNED, UserStatus.WITHDRAWN, null -> {
                return writeError(response, AuthErrorCode.ACCOUNT_BLOCKED)
            }
        }
        filterChain.doFilter(request, response)
    }

    /** 조회는 남겨 두고 쓰기만 막는다 — 제한 계정도 자기 주문·리뷰를 읽을 수는 있어야 한다. */
    private fun isRestrictedWrite(request: HttpServletRequest): Boolean {
        if (request.method.equals(HttpMethod.GET.name(), ignoreCase = true)) return false
        return restrictedWritePathPrefixes.any { request.requestURI.startsWith(it) }
    }

    private fun writeError(
        response: HttpServletResponse,
        errorCode: AuthErrorCode,
    ) {
        response.status = errorCode.status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)))
    }
}
