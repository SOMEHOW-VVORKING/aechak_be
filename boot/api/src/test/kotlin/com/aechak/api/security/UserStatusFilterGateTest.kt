package com.aechak.api.security

import com.aechak.api.auth.cookie.RefreshCookieFactory
import com.aechak.api.user.user.UserController
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.application.auth.service.TokenPolicy
import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.application.user.term.usecase.result.TermResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.command.SetNicknameCommand
import com.aechak.application.user.user.usecase.command.UpdateProfileCommand
import com.aechak.application.user.user.usecase.query.UserSearchQuery
import com.aechak.application.user.user.usecase.result.UserAuthorResult
import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.application.user.user.usecase.result.UserSummaryResult
import com.aechak.application.user.withdrawal.usecase.WithdrawalUseCase
import com.aechak.application.user.withdrawal.usecase.result.WithdrawalCheckResult
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.webcommon.error.GlobalExceptionHandler
import com.aechak.websecurity.authentication.AuthPrincipal
import com.aechak.websecurity.filter.UserStatusFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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
import java.time.Duration

/**
 * PENDING 접근 게이트 고정 — 프로필 수정은 온보딩 화이트리스트 밖(ACTIVE 전용)임을 회귀로 못박는다.
 * 목록의 진실은 SecurityConfig.onboardingAllowedPaths — 여기서는 유저 구간만 거울로 든다(standalone이라 base-path 접두 없음).
 */
class UserStatusFilterGateTest {
    private val pendingReader =
        object : UserStatusReader {
            override fun statusOf(userId: Long): UserStatus = UserStatus.PENDING_ONBOARDING
        }

    private val allowedPaths = setOf("/users/me", "/users/me/consents", "/users/me/nickname", "/users/nickname/check")

    private val pendingMe =
        UserMeResult(
            status = UserStatus.PENDING_ONBOARDING,
            nickname = null,
            profileImageUrl = null,
            profileImageKey = null,
            bio = null,
            email = null,
            phoneNumber = null,
            isPhoneVerified = false,
        )

    private val fakeUserUseCase =
        object : UserUseCase {
            override fun checkNickname(
                userId: Long,
                nickname: String,
            ): Boolean = true

            override fun setNickname(command: SetNicknameCommand): UserMeResult = pendingMe

            override fun getMe(userId: Long): UserMeResult = pendingMe

            override fun updateProfile(command: UpdateProfileCommand): UserMeResult = error("필터가 차단했어야 한다")

            override fun searchUsers(query: UserSearchQuery): List<UserSummaryResult> = error("not used")

            override fun getAuthors(userIds: Collection<Long>): Map<Long, UserAuthorResult> = error("not used")

            override fun registerFromSocial(): Long = error("not used")
        }

    private val fakeConsentUseCase =
        object : ConsentUseCase {
            override fun getActiveTerms(): List<TermResult> = error("not used")

            override fun submitConsents(command: SubmitConsentsCommand) = Unit
        }

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

    private val fakeWithdrawalUseCase =
        object : WithdrawalUseCase {
            override fun checkWithdrawal(userId: Long): WithdrawalCheckResult = error("not used")

            override fun withdraw(userId: Long) = error("not used")
        }

    private val refreshCookieFactory =
        RefreshCookieFactory(
            TokenPolicy(
                accessTtl = Duration.ofMinutes(30),
                refreshTtl = Duration.ofDays(7),
                rotationGrace = Duration.ofSeconds(60),
            ),
            basePath = "/api/v1",
        )

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(UserController(fakeUserUseCase, fakeConsentUseCase, fakeWithdrawalUseCase, refreshCookieFactory))
            .addFilters<StandaloneMockMvcBuilder>(UserStatusFilter(pendingReader, mapper, allowedPaths))
            .setCustomArgumentResolvers(principalResolver)
            .setMessageConverters(JacksonJsonHttpMessageConverter(mapper))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    /** 필터는 SecurityContext의 주체를 본다 — standalone에는 시큐리티 체인이 없어 직접 채운다. */
    @BeforeEach
    fun authenticateAsPending() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(AuthPrincipal(userId = 7L, role = UserRole.GENERAL), "")
    }

    @AfterEach
    fun clearSecurityContext() = SecurityContextHolder.clearContext()

    @Test
    fun `PENDING 유저의 프로필 수정은 403과 ONBOARDING_REQUIRED로 차단된다`() {
        mockMvc
            .perform(
                put("/users/me/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"코코집사","bio":null,"profileImageKey":null}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ONBOARDING_REQUIRED.code))
    }

    @Test
    fun `PENDING 유저도 온보딩 화이트리스트 경로(닉네임 설정)는 통과한다`() {
        mockMvc
            .perform(
                put("/users/me/nickname")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"코코집사"}"""),
            ).andExpect(status().isOk)
    }
}
