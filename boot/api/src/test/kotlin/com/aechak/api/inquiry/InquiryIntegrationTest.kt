package com.aechak.api.inquiry

import com.aechak.api.support.IntegrationTestBase
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.inquiry.inquiry.Inquiry
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration

/**
 * 문의 접수 통합 테스트
 * EmailSender를 기록 Fake(@Primary)로 대체, enabled/recipients/from은 @TestPropertySource 주입.
 */
@TestPropertySource(
    properties = [
        "inquiry.notification.enabled=true",
        "inquiry.notification.recipients=ops1@aechak.com,ops2@aechak.com",
        "aws.ses.from=no-reply@aechak.com",
    ],
)
class InquiryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var recordingEmailSender: RecordingEmailSender

    private lateinit var mockMvc: MockMvc
    private var userId = 0L
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        userId = createActiveUser()
        token = mintAccessToken(userId)
        recordingEmailSender.reset()
    }

    @Test
    fun `문의를 접수하면 저장하고 운영팀 메일을 발송하며 201을 반환한다`() {
        mockMvc
            .perform(postInquiry(token, inquiryJson()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("RECEIVED"))
            .andExpect(jsonPath("$.data.createdAt").exists())

        val inquiry = findInquiries(userId).single()
        assertEquals(InquiryType.SERVICE_ETC, inquiry.inquiryType)
        assertEquals("reply@user.com", inquiry.replyEmail)
        assertEquals("앱이 자꾸 튕겨요", inquiry.content)
        assertEquals(InquiryStatus.RECEIVED, inquiry.status)
        assertEquals(userId, inquiry.userId)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val mail = requireNotNull(recordingEmailSender.last) { "운영팀 메일이 발송돼야 한다" }
            assertEquals(listOf("ops1@aechak.com", "ops2@aechak.com"), mail.to)
            assertEquals("reply@user.com", mail.replyTo, "Reply-To는 문의자 이메일이어야 한다")
            assertTrue(mail.body.contains("앱이 자꾸 튕겨요"), "본문에 문의 내용이 담겨야 한다")
        }
    }

    @Test
    fun `메일 발송이 실패해도 접수는 저장되고 201을 반환한다`() {
        recordingEmailSender.failOnSend = true

        mockMvc
            .perform(postInquiry(token, inquiryJson()))
            .andExpect(status().isCreated)

        // 발송은 커밋 후 비동기라, 실패 발송이 실제로 시도됐는지 관측해야 삼킴 경로가 검증된다(last는 throw 직전에 세팅됨).
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertNotNull(recordingEmailSender.last, "실패해도 발송은 시도돼야 한다")
        }
        assertEquals(1, findInquiries(userId).size, "발송 실패가 접수를 되돌리지 않아야 한다")
    }

    @Test
    fun `이메일 형식이 틀리면 400과 INVALID_REQUEST를 반환한다`() {
        mockMvc
            .perform(postInquiry(token, inquiryJson(replyEmail = "not-an-email")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))

        assertNull(recordingEmailSender.last, "검증 실패면 통지 메일이 나가지 않아야 한다")
    }

    @Test
    fun `내용이 비면 400과 INVALID_REQUEST를 반환한다`() {
        mockMvc
            .perform(postInquiry(token, inquiryJson(content = "")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))

        assertNull(recordingEmailSender.last, "검증 실패면 통지 메일이 나가지 않아야 한다")
    }

    @Test
    fun `알 수 없는 문의 유형은 400과 INVALID_REQUEST를 반환한다`() {
        val body = """{"inquiryType":"UNKNOWN","replyEmail":"reply@user.com","content":"내용"}"""
        mockMvc
            .perform(postInquiry(token, body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))

        assertNull(recordingEmailSender.last, "검증 실패면 통지 메일이 나가지 않아야 한다")
    }

    @Test
    fun `미인증 요청은 401을 반환한다`() {
        mockMvc
            .perform(post("/api/v1/inquiries").contentType(MediaType.APPLICATION_JSON).content(inquiryJson()))
            .andExpect(status().isUnauthorized)

        assertNull(recordingEmailSender.last, "미인증 요청은 통지 메일이 나가지 않아야 한다")
    }

    // --- helpers ---

    private fun MockHttpServletRequestBuilder.bearer(t: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $t")

    private fun postInquiry(
        token: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/inquiries")
            .bearer(token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun findInquiries(userId: Long): List<Inquiry> =
        tx.execute {
            em
                .createQuery("select i from Inquiry i where i.userId = :uid order by i.id", Inquiry::class.java)
                .setParameter("uid", userId)
                .resultList
        }!!

    private fun inquiryJson(
        inquiryType: String = "SERVICE_ETC",
        replyEmail: String = "reply@user.com",
        content: String = "앱이 자꾸 튕겨요",
    ): String =
        """
        {
          "inquiryType": "$inquiryType",
          "replyEmail": "$replyEmail",
          "content": "$content"
        }
        """.trimIndent()

    @TestConfiguration(proxyBeanMethods = false)
    class RecordingEmailConfig {
        @Bean
        @Primary
        fun recordingEmailSender(): RecordingEmailSender = RecordingEmailSender()
    }
}
