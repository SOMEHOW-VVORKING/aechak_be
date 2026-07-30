package com.aechak.api.user.user

import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.application.user.term.usecase.command.SubmitConsentsCommand
import com.aechak.application.user.term.usecase.result.TermResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.command.RegisterUserCommand
import com.aechak.application.user.user.usecase.query.UserSearchQuery
import com.aechak.application.user.user.usecase.result.UserResult
import com.aechak.application.user.user.usecase.result.UserSummaryResult
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.webcommon.error.GlobalExceptionHandler
import com.aechak.websecurity.authentication.AuthPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class UserControllerTest {
    private var captured: SubmitConsentsCommand? = null

    private val fakeConsentUseCase =
        object : ConsentUseCase {
            override fun getActiveTerms(): List<TermResult> = error("not used")

            override fun submitConsents(command: SubmitConsentsCommand) {
                captured = command
            }
        }

    private val fakeUserUseCase =
        object : UserUseCase {
            override fun register(command: RegisterUserCommand): UserResult = error("not used")

            override fun getUser(userId: Long): UserResult = error("not used")

            override fun searchUsers(query: UserSearchQuery): List<UserSummaryResult> = error("not used")

            override fun registerFromSocial(): Long = error("not used")
        }

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

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(UserController(fakeUserUseCase, fakeConsentUseCase))
            .setCustomArgumentResolvers(principalResolver)
            // 런타임(Boot)과 동일하게 Kotlin 모듈 매퍼 사용 — 필수 필드 누락을 파싱 단계에서 거른다
            .setMessageConverters(JacksonJsonHttpMessageConverter(JsonMapper.builder().addModule(kotlinModule()).build()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `동의를 제출하면 204 무본문으로 응답하고 인증 주체의 userId로 위임한다`() {
        mockMvc
            .perform(
                post("/users/me/consents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[{"termId":1,"isAgreed":true},{"termId":4,"isAgreed":false}]}"""),
            ).andExpect(status().isNoContent)

        val command = requireNotNull(captured)
        assertEquals(7L, command.userId, "본문이 아닌 인증 주체에서 userId를 얻는다")
        assertEquals(listOf(1L to true, 4L to false), command.items.map { it.termId to it.isAgreed })
    }

    @Test
    fun `items가 비면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(
                post("/users/me/consents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `같은 termId를 중복 제출하면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(
                post("/users/me/consents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[{"termId":1,"isAgreed":true},{"termId":1,"isAgreed":false}]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `isAgreed가 누락되면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(
                post("/users/me/consents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"items":[{"termId":1}]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }
}
