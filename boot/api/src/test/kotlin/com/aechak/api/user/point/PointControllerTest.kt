package com.aechak.api.user.point

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.result.PointBalanceResult
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.webcommon.error.GlobalExceptionHandler
import com.aechak.websecurity.authentication.AuthPrincipal
import com.aechak.websecurity.filter.UserStatusFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * 적립금 EP는 온보딩 화이트리스트 밖(ACTIVE 전용) — PENDING 차단까지 필터를 체인에 꽂아 회귀로 고정한다.
 * 화이트리스트 목록의 진실은 SecurityConfig.onboardingAllowedPaths — 여기서는 유저 구간만 거울로 든다(standalone이라 base-path 접두 없음).
 */
class PointControllerTest {
    private var capturedUserId: Long? = null
    private var currentStatus: UserStatus = UserStatus.ACTIVE

    private val fakePointUseCase =
        object : PointUseCase {
            override fun getMyPointBalance(userId: Long): PointBalanceResult {
                capturedUserId = userId
                return PointBalanceResult(balance = 1200L)
            }
        }

    private val statusReader =
        object : UserStatusReader {
            override fun statusOf(userId: Long): UserStatus = currentStatus
        }

    private val allowedPaths = setOf("/users/me", "/users/me/consents", "/users/me/nickname", "/users/nickname/check")

    /** standalone MockMvc에는 시큐리티가 없으므로 @AuthenticationPrincipal을 고정 주체로 채운다. */
    private val principalResolver =
        object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == AuthPrincipal::class.java

            override fun resolveArgument(
                parameter: MethodParameter,
                mavContainer: ModelAndViewContainer?,
                webRequest: NativeWebRequest,
                binderFactory: WebDataBinderFactory?,
            ): Any = AuthPrincipal(userId = 7L, role = UserRole.GENERAL)
        }

    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(PointController(fakePointUseCase))
            .addFilters<StandaloneMockMvcBuilder>(UserStatusFilter(statusReader, mapper, allowedPaths))
            .setCustomArgumentResolvers(principalResolver)
            .setMessageConverters(JacksonJsonHttpMessageConverter(mapper))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    /** 필터는 SecurityContext의 주체를 본다 — standalone에는 시큐리티 체인이 없어 직접 채운다. */
    @BeforeEach
    fun authenticate() {
        currentStatus = UserStatus.ACTIVE
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(AuthPrincipal(userId = 7L, role = UserRole.GENERAL), "")
    }

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `잔액 조회는 200과 balance를 반환하고 인증 주체의 userId로 위임한다`() {
        mockMvc
            .perform(get("/users/me/points"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.balance").value(1200))

        assertEquals(7L, capturedUserId, "본문·경로가 아닌 인증 주체에서 userId를 얻는다")
    }

    @Test
    fun `PENDING 유저의 잔액 조회는 403과 ONBOARDING_REQUIRED로 차단된다`() {
        currentStatus = UserStatus.PENDING_ONBOARDING

        mockMvc
            .perform(get("/users/me/points"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ONBOARDING_REQUIRED.code))
    }
}
