package com.aechak.api.user.verification

import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.application.user.verification.usecase.PhoneVerificationUseCase
import com.aechak.application.user.verification.usecase.command.ConfirmPhoneCodeCommand
import com.aechak.application.user.verification.usecase.command.SendPhoneCodeCommand
import com.aechak.application.user.verification.usecase.result.PhoneCodeSentResult
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.webcommon.error.GlobalExceptionHandler
import com.aechak.websecurity.authentication.AuthPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
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
import kotlin.test.Test

/** 형식 검증(@Pattern → 90001)·DTO 매핑 — 비즈니스 없는 웹 경계만 standalone으로 본다(UserControllerTest 결). */
class PhoneVerificationControllerTest {
    private var capturedSend: SendPhoneCodeCommand? = null
    private var capturedConfirm: ConfirmPhoneCodeCommand? = null

    private val fakeUseCase =
        object : PhoneVerificationUseCase {
            override fun sendCode(command: SendPhoneCodeCommand): PhoneCodeSentResult {
                capturedSend = command
                return PhoneCodeSentResult(expiresInSeconds = 180, resendCooldownSeconds = 60)
            }

            override fun confirm(command: ConfirmPhoneCodeCommand): UserMeResult {
                capturedConfirm = command
                return UserMeResult(
                    status = UserStatus.ACTIVE,
                    nickname = "코코집사",
                    profileImageUrl = null,
                    profileImageKey = null,
                    bio = null,
                    email = null,
                    phoneNumber = "010-****-5678",
                    isPhoneVerified = true,
                )
            }
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

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(PhoneVerificationController(fakeUseCase))
            .setCustomArgumentResolvers(principalResolver)
            .setMessageConverters(JacksonJsonHttpMessageConverter(JsonMapper.builder().addModule(kotlinModule()).build()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `발송은 200과 타이머 값을 반환하고 하이픈 입력을 그대로 커맨드로 넘긴다`() {
        mockMvc
            .perform(
                post("/users/me/phone/verifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phoneNumber": "010-1234-5678"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.expiresInSeconds").value(180))
            .andExpect(jsonPath("$.data.resendCooldownSeconds").value(60))

        assertEquals(SendPhoneCodeCommand(userId = 7L, phoneNumber = "010-1234-5678"), capturedSend)
    }

    @Test
    fun `전화번호 형식 위반은 90001로 수렴한다`() {
        mockMvc
            .perform(
                post("/users/me/phone/verifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phoneNumber": "02-123-4567"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `confirm은 200과 마스킹된 내 정보를 반환한다`() {
        mockMvc
            .perform(
                post("/users/me/phone/verifications/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phoneNumber": "010-1234-5678", "code": "000000"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phoneNumber").value("010-****-5678"))
            .andExpect(jsonPath("$.data.isPhoneVerified").value(true))

        assertEquals(
            ConfirmPhoneCodeCommand(userId = 7L, phoneNumber = "010-1234-5678", code = "000000"),
            capturedConfirm,
        )
    }

    @Test
    fun `인증번호가 6자리 숫자가 아니면 90001`() {
        mockMvc
            .perform(
                post("/users/me/phone/verifications/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phoneNumber": "010-1234-5678", "code": "12ab"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }
}
