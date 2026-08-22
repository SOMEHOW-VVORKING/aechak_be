package com.aechak.api.payment

import com.aechak.api.payment.config.PaymentStoreProperties
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.PreparePaymentCommand
import com.aechak.application.payment.usecase.result.PreparePaymentResult
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.webcommon.error.GlobalExceptionHandler
import com.aechak.websecurity.authentication.AuthPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
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

/**
 * 계약. 결제창 값(storeId·channelKey)의 출처가 boot의 설정이고 결제수단의 출처가 요청 본문임을 고정함.
 * 깨지면 결제창 값이 빈 채로 201이 나가 FE가 결제창을 못 열거나, 사용자가 고른 수단과 다른 수단이 결제행에 찍힘.
 */
class PaymentControllerTest {
    private var capturedCommand: PreparePaymentCommand? = null

    private val fakePaymentUseCase =
        object : PaymentUseCase {
            override fun preparePayment(command: PreparePaymentCommand): PreparePaymentResult {
                capturedCommand = command
                return PreparePaymentResult(paymentId = command.orderGroupPublicId, targetAmount = 13_000L)
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

    private val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

    private fun mockMvc(store: PaymentStoreProperties): MockMvc =
        MockMvcBuilders
            .standaloneSetup(PaymentController(fakePaymentUseCase, store))
            .setCustomArgumentResolvers(principalResolver)
            .setMessageConverters(JacksonJsonHttpMessageConverter(mapper))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    private fun prepare(
        publicId: String,
        body: String = """{"method":"KAKAO_PAY"}""",
    ): MockHttpServletRequestBuilder =
        post("/order-groups/$publicId/payment/prepare")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    @Test
    fun `결제창 값은 설정에서, 결제수단은 요청 본문에서 커맨드로 실린다`() {
        val store = PaymentStoreProperties("store-a", "channel-a")

        mockMvc(store)
            .perform(prepare("og-1"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.storeId").value("store-a"))
            .andExpect(jsonPath("$.data.channelKey").value("channel-a"))

        val command = capturedCommand
        assertEquals(PaymentMethod.KAKAO_PAY, command?.method, "본문의 결제수단이 커맨드에 실려야 한다")
        assertEquals(7L, command?.buyerId, "경로가 아닌 인증 주체에서 buyerId를 얻어야 한다")
        assertEquals("og-1", command?.orderGroupPublicId, "경로변수가 주문그룹 publicId로 실려야 한다")
    }

    @Test
    fun `enum에 없는 결제수단은 파싱 단계에서 90001로 끊는다`() {
        val store = PaymentStoreProperties("store-a", "channel-a")

        mockMvc(store)
            .perform(prepare("og-4", body = """{"method":"VIRTUAL_ACCOUNT"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        assertNull(capturedCommand, "역직렬화가 깨지면 유스케이스까지 가면 안 된다")
    }

    @Test
    fun `결제수단 키가 없으면 파싱 단계에서 90001로 끊는다`() {
        val store = PaymentStoreProperties("store-a", "channel-a")

        mockMvc(store)
            .perform(prepare("og-5", body = "{}"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        assertNull(capturedCommand, "결제수단 없이 결제행을 만들면 안 된다")
    }

    @Test
    fun `storeId나 channelKey 한쪽만 비어도 결제 준비를 60005로 끊는다`() {
        val noStore = PaymentStoreProperties(id = "", channelKey = "channel-a")
        val noChannel = PaymentStoreProperties(id = "store-a", channelKey = "")

        mockMvc(noStore)
            .perform(prepare("og-2"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.errorCode").value(60005))
        mockMvc(noChannel)
            .perform(prepare("og-3"))
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.errorCode").value(60005))

        assertNull(capturedCommand, "설정이 비면 결제행을 만들기 전에 끊어야 한다")
    }
}
