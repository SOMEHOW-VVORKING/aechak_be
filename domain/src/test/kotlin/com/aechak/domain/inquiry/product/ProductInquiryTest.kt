package com.aechak.domain.inquiry.product

import com.aechak.common.error.BusinessException
import com.aechak.domain.inquiry.error.InquiryErrorCode
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductInquiryTest {
    private val wednesday = LocalDateTime.of(2026, 10, 21, 10, 0)

    private fun anInquiry(
        authorUserId: Long = 10L,
        sellerId: Long = 20L,
        title: String = "크기가 어떻게 되나요",
        content: String = "소형견이 쓸 수 있는 크기인지 궁금해요",
        secret: Boolean = false,
        now: LocalDateTime = wednesday,
    ): ProductInquiry =
        ProductInquiry.ask(
            productId = 1L,
            productPublicId = "01HPRODUCTPUBLICID00000001",
            sellerId = sellerId,
            authorUserId = authorUserId,
            productNameSnapshot = "자동 급식기 5L",
            category = ProductInquiryCategory.PRODUCT,
            title = title,
            content = content,
            secret = secret,
            now = now,
        )

    @Test
    fun `접수하면 답변 대기 상태이고 답변과 종료 시각이 없다`() {
        val inquiry = anInquiry()

        assertEquals(ProductInquiryStatus.RECEIVED, inquiry.status)
        assertNull(inquiry.answer)
        assertNull(inquiry.answeredAt)
        assertNull(inquiry.withdrawnAt)
    }

    @Test
    fun `제목과 본문의 앞뒤 공백을 잘라 담는다`() {
        val inquiry = anInquiry(title = "  제목  ", content = "  본문  ")

        assertEquals("제목", inquiry.title)
        assertEquals("본문", inquiry.content)
    }

    @Test
    fun `제목이 공백뿐이면 접수할 수 없다`() {
        val e = assertFailsWith<BusinessException> { anInquiry(title = "   ") }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_TITLE_REQUIRED, e.errorCode)
    }

    @Test
    fun `제목이 상한을 한 글자 넘으면 접수할 수 없다`() {
        val e = assertFailsWith<BusinessException> { anInquiry(title = "가".repeat(ProductInquiry.TITLE_MAX + 1)) }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_TITLE_TOO_LONG, e.errorCode)
    }

    @Test
    fun `제목이 상한과 같으면 접수된다`() {
        val inquiry = anInquiry(title = "가".repeat(ProductInquiry.TITLE_MAX))

        assertEquals(ProductInquiry.TITLE_MAX, inquiry.title.length)
    }

    @Test
    fun `본문이 공백뿐이면 접수할 수 없다`() {
        val e = assertFailsWith<BusinessException> { anInquiry(content = " ") }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_CONTENT_REQUIRED, e.errorCode)
    }

    @Test
    fun `본문이 상한을 넘으면 접수할 수 없다`() {
        val e = assertFailsWith<BusinessException> { anInquiry(content = "가".repeat(ProductInquiry.CONTENT_MAX + 1)) }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_CONTENT_TOO_LONG, e.errorCode)
    }

    @Test
    fun `자기 상품에는 문의를 등록할 수 없다`() {
        val e = assertFailsWith<BusinessException> { anInquiry(authorUserId = 20L, sellerId = 20L) }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_SELF_ASK_FORBIDDEN, e.errorCode)
    }

    @Test
    fun `답변 기한은 주말을 건너뛴 영업일 2일째의 끝이다`() {
        // 수(10/21) 접수 → 목·금이 2영업일 → 금(10/23) 끝
        assertEquals(
            LocalDateTime.of(2026, 10, 23, 23, 59, 59, 999_999_000),
            ProductInquiry.answerDeadline(wednesday),
        )
        // 목(10/22) 접수 → 금·월 → 월(10/26) 끝
        assertEquals(
            LocalDateTime.of(2026, 10, 26, 23, 59, 59, 999_999_000),
            ProductInquiry.answerDeadline(LocalDateTime.of(2026, 10, 22, 10, 0)),
        )
        // 금(10/23) 접수 → 월·화 → 화(10/27) 끝
        assertEquals(
            LocalDateTime.of(2026, 10, 27, 23, 59, 59, 999_999_000),
            ProductInquiry.answerDeadline(LocalDateTime.of(2026, 10, 23, 10, 0)),
        )
        // 토(10/24) 접수 → 월·화 → 화(10/27) 끝
        assertEquals(
            LocalDateTime.of(2026, 10, 27, 23, 59, 59, 999_999_000),
            ProductInquiry.answerDeadline(LocalDateTime.of(2026, 10, 24, 10, 0)),
        )
    }

    @Test
    fun `기한 지남은 미답변 상태에서 기한을 넘긴 순간부터다`() {
        val inquiry = anInquiry()
        val deadline = inquiry.answerDueAt

        assertFalse(inquiry.isOverdue(deadline))
        assertTrue(inquiry.isOverdue(deadline.plusNanos(1_000)))
    }

    @Test
    fun `답변하면 기한이 지나도 더는 기한 지남이 아니다`() {
        val inquiry = anInquiry()
        inquiry.writeAnswer(answererUserId = 20L, content = "35cm 입니다", now = wednesday.plusDays(10))

        assertFalse(inquiry.isOverdue(inquiry.answerDueAt.plusDays(30)))
    }

    @Test
    fun `철회하면 기한 지남 대상에서 빠진다`() {
        val inquiry = anInquiry()
        inquiry.withdraw(wednesday.plusHours(1))

        assertFalse(inquiry.isOverdue(inquiry.answerDueAt.plusDays(30)))
    }

    @Test
    fun `답변을 등록하면 상태와 답변 시각과 답변 본문이 함께 세팅된다`() {
        val inquiry = anInquiry()
        val answeredAt = wednesday.plusHours(3)

        inquiry.writeAnswer(answererUserId = 20L, content = "  입구 높이는 12cm 입니다  ", now = answeredAt)

        assertEquals(ProductInquiryStatus.ANSWERED, inquiry.status)
        assertEquals(answeredAt, inquiry.answeredAt)
        val answer = assertNotNull(inquiry.answer)
        assertEquals("입구 높이는 12cm 입니다", answer.content)
        assertEquals(20L, answer.answererUserId)
    }

    @Test
    fun `답변 본문이 공백뿐이면 답변이 달리지 않고 상태도 그대로다`() {
        val inquiry = anInquiry()

        val e = assertFailsWith<BusinessException> { inquiry.writeAnswer(20L, "   ", wednesday) }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_ANSWER_REQUIRED, e.errorCode)
        assertNull(inquiry.answer)
        assertEquals(ProductInquiryStatus.RECEIVED, inquiry.status)
        assertNull(inquiry.answeredAt)
    }

    @Test
    fun `답변 본문이 상한을 넘으면 답변이 달리지 않는다`() {
        val inquiry = anInquiry()

        val e =
            assertFailsWith<BusinessException> {
                inquiry.writeAnswer(20L, "가".repeat(ProductInquiry.ANSWER_CONTENT_MAX + 1), wednesday)
            }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_ANSWER_TOO_LONG, e.errorCode)
        assertNull(inquiry.answer)
        assertEquals(ProductInquiryStatus.RECEIVED, inquiry.status)
    }

    @Test
    fun `두 번째 답변은 거절되고 첫 답변과 답변 시각이 그대로 남는다`() {
        val inquiry = anInquiry()
        val firstAnsweredAt = wednesday.plusHours(1)
        inquiry.writeAnswer(20L, "첫 답변", firstAnsweredAt)

        val e = assertFailsWith<BusinessException> { inquiry.writeAnswer(20L, "두 번째 답변", wednesday.plusHours(2)) }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_ALREADY_ANSWERED, e.errorCode)
        assertEquals("첫 답변", assertNotNull(inquiry.answer).content)
        assertEquals(firstAnsweredAt, inquiry.answeredAt)
    }

    @Test
    fun `철회된 문의에는 답변할 수 없고 답변이 달리지 않는다`() {
        val inquiry = anInquiry()
        inquiry.withdraw(wednesday.plusHours(1))

        val e = assertFailsWith<BusinessException> { inquiry.writeAnswer(20L, "답변", wednesday.plusHours(2)) }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_WITHDRAWN, e.errorCode)
        assertNull(inquiry.answer)
        assertEquals(ProductInquiryStatus.WITHDRAWN, inquiry.status)
    }

    @Test
    fun `철회하면 상태와 철회 시각이 세팅된다`() {
        val inquiry = anInquiry()
        val withdrawnAt = wednesday.plusHours(1)

        inquiry.withdraw(withdrawnAt)

        assertEquals(ProductInquiryStatus.WITHDRAWN, inquiry.status)
        assertEquals(withdrawnAt, inquiry.withdrawnAt)
    }

    @Test
    fun `답변이 달린 문의는 철회할 수 없고 상태가 그대로다`() {
        val inquiry = anInquiry()
        inquiry.writeAnswer(20L, "답변", wednesday.plusHours(1))

        val e = assertFailsWith<BusinessException> { inquiry.withdraw(wednesday.plusHours(2)) }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_CANNOT_WITHDRAW_ANSWERED, e.errorCode)
        assertEquals(ProductInquiryStatus.ANSWERED, inquiry.status)
        assertNull(inquiry.withdrawnAt)
    }

    @Test
    fun `두 번 철회하면 두 번째는 거절되고 첫 철회 시각이 그대로다`() {
        val inquiry = anInquiry()
        val firstWithdrawnAt = wednesday.plusHours(1)
        inquiry.withdraw(firstWithdrawnAt)

        val e = assertFailsWith<BusinessException> { inquiry.withdraw(wednesday.plusHours(2)) }

        assertEquals(InquiryErrorCode.PRODUCT_INQUIRY_ALREADY_WITHDRAWN, e.errorCode)
        assertEquals(firstWithdrawnAt, inquiry.withdrawnAt)
    }

    @Test
    fun `상품명 스냅샷은 컬럼 상한에서 잘린다`() {
        val inquiry =
            ProductInquiry.ask(
                productId = 1L,
                productPublicId = "01HPRODUCTPUBLICID00000001",
                sellerId = 20L,
                authorUserId = 10L,
                productNameSnapshot = "가".repeat(ProductInquiry.PRODUCT_NAME_SNAPSHOT_MAX + 50),
                category = ProductInquiryCategory.ETC,
                title = "제목",
                content = "본문",
                secret = false,
                now = wednesday,
            )

        assertEquals(ProductInquiry.PRODUCT_NAME_SNAPSHOT_MAX, inquiry.productNameSnapshot.length)
    }
}
