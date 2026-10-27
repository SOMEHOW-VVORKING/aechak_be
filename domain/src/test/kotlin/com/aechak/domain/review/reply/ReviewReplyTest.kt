package com.aechak.domain.review.reply

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewReplyTest {
    @Test
    fun `답글을 쓰면 리뷰와 셀러가 함께 기록된다`() {
        val reply = ReviewReply.write(reviewId = 7L, sellerId = 11L, content = "알려 주셔서 감사해요.")

        assertEquals(7L, reply.reviewId)
        assertEquals(11L, reply.sellerId)
        assertEquals("알려 주셔서 감사해요.", reply.content)
    }

    @Test
    fun `앞뒤 공백은 저장 전에 잘린다`() {
        val reply = ReviewReply.write(reviewId = 1L, sellerId = 1L, content = "  사이즈표를 고쳤어요.\n")

        assertEquals("사이즈표를 고쳤어요.", reply.content)
    }

    @Test
    fun `빈 내용과 공백뿐인 내용은 거절한다`() {
        listOf("", "   ", "\n\t").forEach { blank ->
            val e = assertFailsWith<BusinessException> { ReviewReply.write(1L, 1L, blank) }
            assertEquals(ReviewErrorCode.INVALID_REVIEW_REPLY_CONTENT, e.errorCode)
        }
    }

    @Test
    fun `최대 길이는 받고 한 글자 넘으면 거절한다`() {
        val maxContent = "가".repeat(ReviewReply.CONTENT_MAX)

        assertEquals(ReviewReply.CONTENT_MAX, ReviewReply.write(1L, 1L, maxContent).content.length)

        val e = assertFailsWith<BusinessException> { ReviewReply.write(1L, 1L, maxContent + "가") }
        assertEquals(ReviewErrorCode.INVALID_REVIEW_REPLY_CONTENT, e.errorCode)
    }

    @Test
    fun `수정도 같은 검증을 통과해야 하고 실패하면 원래 내용이 남는다`() {
        val reply = ReviewReply.write(1L, 1L, "처음 답글")

        reply.edit("  고친 답글  ")
        assertEquals("고친 답글", reply.content)

        assertFailsWith<BusinessException> { reply.edit(" ") }
        assertEquals("고친 답글", reply.content)

        assertFailsWith<BusinessException> { reply.edit("가".repeat(ReviewReply.CONTENT_MAX + 1)) }
        assertEquals("고친 답글", reply.content)
    }
}
