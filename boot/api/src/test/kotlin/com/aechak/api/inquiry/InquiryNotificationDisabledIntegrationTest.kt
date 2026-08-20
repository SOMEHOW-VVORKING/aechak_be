package com.aechak.api.inquiry

import com.aechak.api.support.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 통지 비활성 환경: 접수는 저장, 발송은 생략.
 * enabled를 안 켜서 recipients/from 없이도 부팅, EmailSender 미호출.
 */
class InquiryNotificationDisabledIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var recordingEmailSender: RecordingEmailSender

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        recordingEmailSender.reset()
    }

    @Test
    fun `통지 비활성이면 접수는 저장하되 메일은 발송하지 않는다`() {
        val userId = createActiveUser()
        val token = mintAccessToken(userId)

        mockMvc
            .perform(
                post("/api/v1/inquiries")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"inquiryType":"ACCOUNT","replyEmail":"reply@user.com","content":"로그인이 안돼요"}"""),
            ).andExpect(status().isCreated)

        val count =
            tx.execute {
                em
                    .createQuery("select count(i) from Inquiry i where i.userId = :uid", java.lang.Long::class.java)
                    .setParameter("uid", userId)
                    .singleResult
            }!!
        assertEquals(1L, count.toLong(), "통지가 꺼져도 접수는 저장돼야 한다")
        assertNull(recordingEmailSender.last, "통지 비활성이면 EmailSender를 호출하지 않아야 한다")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RecordingEmailConfig {
        @Bean
        @Primary
        fun recordingEmailSender(): RecordingEmailSender = RecordingEmailSender()
    }
}
