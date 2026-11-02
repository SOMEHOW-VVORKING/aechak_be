package com.aechak.api.report

import com.aechak.api.inquiry.RecordingEmailSender
import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.review.report.ReviewReport
import com.aechak.domain.review.review.Review
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration

/**
 * 신고 접수 메일 통합 테스트. 신고가 커밋되면 운영팀 메일이 실제로 나가는지 HTTP부터 확인한다.
 * 깨지면 신고는 저장되는데 운영팀 메일이 나가지 않는다.
 */
@TestPropertySource(
    properties = [
        "report.notification.enabled=true",
        "report.notification.recipients=ops1@aechak.com,ops2@aechak.com",
    ],
)
class ReportNotificationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var recordingEmailSender: RecordingEmailSender

    @BeforeEach
    fun setUp() {
        recordingEmailSender.reset()
    }

    @Test
    fun `리뷰를 신고하면 커밋 뒤 운영팀에 메일을 보낸다`() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        val reviewId =
            tx.execute {
                val review =
                    Review.write(
                        productId = 1L,
                        optionNameSnapshot = "기본",
                        orderItemId = 1L,
                        authorUserId = createActiveUser(),
                        rating = 5,
                        content = "좋은 상품입니다",
                    )
                em.persist(review)
                review.id
            }!!

        mockMvc
            .perform(
                post("/api/v1/reports")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(createActiveUser())}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"targetType":"REVIEW","targetId":"$reviewId","reasonCode":"SPAM"}"""),
            ).andExpect(status().isCreated)

        val reportId = tx.execute { em.createQuery("select r from ReviewReport r", ReviewReport::class.java).singleResult.id }
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            // 메일이 아직 없으면 AssertionError로 실패해야 Awaitility가 다시 확인한다
            val mail = recordingEmailSender.last
            assertEquals(listOf("ops1@aechak.com", "ops2@aechak.com"), mail?.to, "운영팀 메일이 발송돼야 한다")
            assertEquals("[신고/REVIEW] #$reportId", mail?.subject)
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RecordingEmailConfig {
        @Bean
        @Primary
        fun recordingEmailSender(): RecordingEmailSender = RecordingEmailSender()
    }
}
