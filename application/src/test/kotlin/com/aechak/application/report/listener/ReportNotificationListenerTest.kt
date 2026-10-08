package com.aechak.application.report.listener

import com.aechak.application.email.port.EmailMessage
import com.aechak.application.email.port.EmailSender
import com.aechak.application.report.port.ReportNotificationPolicy
import com.aechak.domain.product.report.enums.ProductReportReason
import com.aechak.domain.product.report.event.ProductReportReceivedEvent
import com.aechak.domain.review.report.enums.ReviewReportReason
import com.aechak.domain.review.report.event.ReviewReportReceivedEvent
import com.aechak.domain.user.report.enums.ReportReasonCode
import com.aechak.domain.user.report.event.UserReportReceivedEvent
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 계약 테스트. 신고 접수 이벤트 하나가 운영팀 메일 한 통이 되는 규칙(수신자, 제목, 본문, 생략 조건, 실패 삼킴)을 고정한다.
 * 깨지면 운영팀이 신고를 놓치거나 본문이 깨지거나 발송 실패가 리스너 밖으로 새어 나간다.
 */
class ReportNotificationListenerTest {
    private val sent = mutableListOf<EmailMessage>()
    private var failOnSend = false
    private val emailSender =
        object : EmailSender {
            override fun send(message: EmailMessage) {
                sent += message
                if (failOnSend) throw RuntimeException("SES 발송 실패 (테스트)")
            }
        }
    private val receivedAt = LocalDateTime.of(2026, 10, 7, 12, 0)

    private fun listener(
        enabled: Boolean = true,
        recipients: List<String> = listOf("ops@aechak.com"),
    ) = ReportNotificationListener(emailSender, Policy(enabled, recipients))

    private fun productEvent(reasonText: String?) =
        ProductReportReceivedEvent(
            reportId = 10L,
            productPublicId = "01J000000000000000000TEST",
            reasonCode = ProductReportReason.OTHER,
            reasonText = reasonText,
            createdAt = receivedAt,
        )

    @Test
    fun `상품 신고가 접수되면 운영팀 수신자에게 신고 id와 대상, 사유를 담아 보내고 회신 주소는 비운다`() {
        listener().handleProductReportReceived(productEvent("가품 의심\n두 번째 줄"))

        val mail = sent.single()
        assertEquals(listOf("ops@aechak.com"), mail.to, "운영팀 수신자에게 보내야 한다")
        assertNull(mail.replyTo, "회신 주소를 두지 않아야 한다")
        assertEquals("[신고/PRODUCT] #10", mail.subject, "제목에 대상 종류와 신고 id가 담겨야 한다")
        assertTrue(mail.body.contains("- 대상: 상품 publicId 01J000000000000000000TEST"), "본문에 대상이 담겨야 한다")
        assertTrue(mail.body.contains("- 사유: OTHER"), "본문에 사유 코드가 담겨야 한다")
        assertTrue(mail.body.endsWith("상세 사유:\n가품 의심\n두 번째 줄"), "여러 줄 상세 사유가 들여쓰기 없이 끝에 붙어야 한다")
    }

    @Test
    fun `리뷰와 사용자 신고는 제목에 대상 종류를 달리 담고 상세 사유가 없으면 없음으로 적는다`() {
        listener().handleReviewReportReceived(
            ReviewReportReceivedEvent(
                reportId = 11L,
                reviewId = 3L,
                reasonCode = ReviewReportReason.SPAM,
                reasonText = null,
                createdAt = receivedAt,
            ),
        )
        listener().handleUserReportReceived(
            UserReportReceivedEvent(
                reportId = 12L,
                targetUserId = 5L,
                reviewId = 3L,
                reasonCode = ReportReasonCode.ABUSE,
                reasonText = null,
                createdAt = receivedAt,
            ),
        )

        assertEquals(listOf("[신고/REVIEW] #11", "[신고/USER] #12"), sent.map { it.subject }, "제목에 대상 종류가 담겨야 한다")
        assertTrue(sent[0].body.contains("- 대상: 리뷰 id 3"), "리뷰 신고 본문에 리뷰 id가 담겨야 한다")
        assertTrue(
            sent[1].body.contains("- 대상: 사용자 id 5 (리뷰 id 3 작성자)"),
            "사용자 신고 본문에 사용자 id와 근거 리뷰 id가 담겨야 한다",
        )
        assertTrue(sent.all { it.body.endsWith("상세 사유:\n(없음)") }, "상세 사유가 없으면 없음으로 적어야 한다")
    }

    @Test
    fun `통지가 꺼져 있거나 수신자가 없으면 메일을 보내지 않는다`() {
        listener(enabled = false).handleProductReportReceived(productEvent(null))
        listener(recipients = emptyList()).handleProductReportReceived(productEvent(null))

        assertTrue(sent.isEmpty(), "꺼져 있거나 수신자가 없으면 발송을 생략해야 한다")
    }

    @Test
    fun `메일 발송이 실패해도 예외를 밖으로 던지지 않는다`() {
        failOnSend = true

        listener().handleProductReportReceived(productEvent(null))

        assertEquals(1, sent.size, "발송은 시도돼야 한다")
    }

    private data class Policy(
        override val enabled: Boolean,
        override val recipients: List<String>,
    ) : ReportNotificationPolicy
}
