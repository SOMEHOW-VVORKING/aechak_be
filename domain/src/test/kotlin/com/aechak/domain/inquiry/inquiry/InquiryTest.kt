package com.aechak.domain.inquiry.inquiry

import com.aechak.common.error.BusinessException
import com.aechak.domain.inquiry.error.InquiryErrorCode
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import com.aechak.domain.inquiry.inquiry.event.InquiryAnsweredEvent
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 문의 답변·상태 전이 계약. 깨지면 답변이 덮어써지거나(운영 기록 소실) 완료된 문의가 되돌아간다.
 */
class InquiryTest {
    private val answeredAt = LocalDateTime.of(2026, 10, 27, 14, 30)

    @Test
    fun `답변을 등록하면 완료로 전이하고 답변자와 완료 시각이 남는다`() {
        val inquiry = received()

        inquiry.registerAnswer(adminId = 7L, body = "확인 후 처리했습니다.", answeredAt = answeredAt)

        assertEquals(InquiryStatus.DONE, inquiry.status)
        assertEquals("확인 후 처리했습니다.", inquiry.answer)
        assertEquals(7L, inquiry.answeredByAdminId)
        assertEquals(7L, inquiry.assignedAdminId)
        assertEquals(answeredAt, inquiry.resolvedAt)
    }

    @Test
    fun `답변 등록은 답변 사실 이벤트를 모은다`() {
        val inquiry = received()

        inquiry.registerAnswer(adminId = 7L, body = "처리했습니다.", answeredAt = answeredAt)

        val event = inquiry.events.filterIsInstance<InquiryAnsweredEvent>().single()
        assertEquals("처리했습니다.", event.answer)
        assertEquals(7L, event.answeredByAdminId)
        assertEquals(answeredAt, event.answeredAt)
        assertEquals("buyer@aechak.test", event.replyEmail)
    }

    @Test
    fun `이미 답변한 문의는 다시 답변되지 않고 기존 답변이 그대로다`() {
        val inquiry = received().apply { registerAnswer(7L, "첫 답변", answeredAt) }

        val exception =
            assertFailsWith<BusinessException> {
                inquiry.registerAnswer(9L, "덮어쓰는 답변", answeredAt.plusHours(1))
            }

        assertEquals(InquiryErrorCode.INQUIRY_ALREADY_ANSWERED, exception.errorCode)
        assertEquals("첫 답변", inquiry.answer)
        assertEquals(7L, inquiry.answeredByAdminId)
        assertEquals(answeredAt, inquiry.resolvedAt)
    }

    @Test
    fun `공백 답변은 거절되고 상태가 접수에 머문다`() {
        val inquiry = received()

        val exception = assertFailsWith<BusinessException> { inquiry.registerAnswer(7L, "   ", answeredAt) }

        assertEquals(InquiryErrorCode.INQUIRY_ANSWER_REQUIRED, exception.errorCode)
        assertEquals(InquiryStatus.RECEIVED, inquiry.status)
        assertNull(inquiry.answer)
        assertTrue(inquiry.events.isEmpty())
    }

    @Test
    fun `상한을 한 글자 넘는 답변은 거절되고 상한 길이는 통과한다`() {
        val overLimit =
            assertFailsWith<BusinessException> {
                received().registerAnswer(7L, "가".repeat(Inquiry.MAX_ANSWER_LENGTH + 1), answeredAt)
            }
        assertEquals(InquiryErrorCode.INQUIRY_ANSWER_TOO_LONG, overLimit.errorCode)

        val atLimit = received()
        atLimit.registerAnswer(7L, "가".repeat(Inquiry.MAX_ANSWER_LENGTH), answeredAt)
        assertEquals(InquiryStatus.DONE, atLimit.status)
    }

    @Test
    fun `처리중과 보류로는 옮길 수 있고 담당자가 남는다`() {
        val inquiry = received()

        inquiry.changeStatus(adminId = 3L, target = InquiryStatus.IN_PROGRESS)
        assertEquals(InquiryStatus.IN_PROGRESS, inquiry.status)
        assertEquals(3L, inquiry.assignedAdminId)

        inquiry.changeStatus(adminId = 4L, target = InquiryStatus.ON_HOLD)
        assertEquals(InquiryStatus.ON_HOLD, inquiry.status)
        assertEquals(4L, inquiry.assignedAdminId)
    }

    @Test
    fun `상태 전이로는 답변이 생기지 않는다`() {
        val inquiry = received()

        inquiry.changeStatus(3L, InquiryStatus.IN_PROGRESS)

        assertNull(inquiry.answer)
        assertNull(inquiry.answeredByAdminId)
        assertNull(inquiry.resolvedAt)
    }

    @Test
    fun `완료와 접수로의 상태 전이는 거절된다`() {
        for (target in listOf(InquiryStatus.DONE, InquiryStatus.RECEIVED)) {
            val inquiry = received()

            val exception = assertFailsWith<BusinessException> { inquiry.changeStatus(3L, target) }

            assertEquals(InquiryErrorCode.INQUIRY_STATUS_TRANSITION_NOT_ALLOWED, exception.errorCode)
            assertEquals(InquiryStatus.RECEIVED, inquiry.status)
            assertNull(inquiry.assignedAdminId)
        }
    }

    @Test
    fun `완료된 문의는 되돌려지지 않는다`() {
        val inquiry = received().apply { registerAnswer(7L, "답변", answeredAt) }

        val exception = assertFailsWith<BusinessException> { inquiry.changeStatus(3L, InquiryStatus.IN_PROGRESS) }

        assertEquals(InquiryErrorCode.INQUIRY_STATUS_TRANSITION_NOT_ALLOWED, exception.errorCode)
        assertEquals(InquiryStatus.DONE, inquiry.status)
        assertEquals(7L, inquiry.assignedAdminId)
    }

    @Test
    fun `같은 상태로의 전이는 거절돼 담당자만 바꿔치기할 수 없다`() {
        val inquiry = received().apply { changeStatus(3L, InquiryStatus.IN_PROGRESS) }

        val exception = assertFailsWith<BusinessException> { inquiry.changeStatus(9L, InquiryStatus.IN_PROGRESS) }

        assertEquals(InquiryErrorCode.INQUIRY_STATUS_TRANSITION_NOT_ALLOWED, exception.errorCode)
        assertEquals(3L, inquiry.assignedAdminId)
    }

    private fun received(): Inquiry =
        Inquiry.receive(
            userId = 100L,
            inquiryType = InquiryType.ORDER_PAYMENT,
            replyEmail = "buyer@aechak.test",
            content = "결제는 됐는데 주문 내역에 안 보입니다.",
        )
}
