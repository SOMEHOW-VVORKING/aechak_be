package com.aechak.application.review.seller.service

import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.seller.port.SellerReviewListCondition
import com.aechak.application.review.seller.port.SellerReviewQueryPort
import com.aechak.application.review.seller.port.view.SellerReviewView
import com.aechak.application.review.seller.usecase.command.WriteReviewReplyCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.reply.ReviewReply
import com.aechak.domain.review.reply.repository.ReviewReplyRepository
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.repository.ReviewRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 답글 쓰기의 **판정 순서**만 본다 — 실제 SQL·잠금은 통합 테스트와 DB가 본다.
 *
 * 여기서 고정하려는 것은 하나다: 조인 선조회(스냅샷)가 "노출 중"이라고 말해도,
 * 잠금 읽기가 돌려준 최신 리뷰가 비노출이면 답글을 **쓰지 않는다**. 이 재확인이 빠지면
 * "선조회 통과 → 구매자가 리뷰 삭제를 커밋 → 답글 INSERT" 순서로 지워진 리뷰에 답글이 매달린다.
 */
class ReviewReplyServiceTest {
    private val sellerId = 7L
    private val reviewId = 100L

    @Test
    fun `선조회가 통과해도 잠금 읽기가 지워진 리뷰를 주면 답글을 쓰지 않는다`() {
        val replies = RecordingReplyRepository()
        // 스냅샷을 보는 조인 선조회는 아직 "노출 중"이라고 답한다
        val service = ReviewReplyService(replies, FixedReviewRepository(deletedReview()), AlwaysOwnedPort())

        val thrown = assertThrows<BusinessException> { service.create(command()) }

        assertEquals(ReviewErrorCode.REVIEW_NOT_FOUND, thrown.errorCode)
        // 남의 리뷰와 같은 코드다 — 삭제된 리뷰라는 사실조차 따로 알리지 않는다
        assertEquals(110000, thrown.errorCode.code)
        assertTrue(replies.saved.isEmpty(), "비노출로 바뀐 리뷰에 답글 행이 남았다")
    }

    @Test
    fun `잠금 읽기가 리뷰를 못 찾으면 답글을 쓰지 않는다`() {
        val replies = RecordingReplyRepository()
        val service = ReviewReplyService(replies, FixedReviewRepository(null), AlwaysOwnedPort())

        val thrown = assertThrows<BusinessException> { service.create(command()) }

        assertEquals(ReviewErrorCode.REVIEW_NOT_FOUND, thrown.errorCode)
        assertTrue(replies.saved.isEmpty())
    }

    @Test
    fun `잠금 읽기가 노출 중인 리뷰를 주면 답글을 쓴다`() {
        val replies = RecordingReplyRepository()
        val service = ReviewReplyService(replies, FixedReviewRepository(visibleReview()), AlwaysOwnedPort())

        service.create(command())

        assertEquals(listOf("실측값으로 고쳤어요."), replies.saved.map { it.content })
    }

    @Test
    fun `수정·삭제도 같은 재확인을 거친다`() {
        val existing = ReviewReply.write(reviewId, sellerId, "원래 답글")
        val replies = RecordingReplyRepository(existing)
        val service = ReviewReplyService(replies, FixedReviewRepository(deletedReview()), AlwaysOwnedPort())

        assertEquals(ReviewErrorCode.REVIEW_NOT_FOUND, assertThrows<BusinessException> { service.edit(command()) }.errorCode)
        assertEquals(
            ReviewErrorCode.REVIEW_NOT_FOUND,
            assertThrows<BusinessException> { service.delete(sellerId, reviewId) }.errorCode,
        )
        assertEquals("원래 답글", existing.content, "비노출 리뷰의 답글이 수정됐다")
        assertTrue(replies.deleted.isEmpty(), "비노출 리뷰의 답글이 지워졌다")
    }

    @Test
    fun `수정·삭제는 답글도 잠금 읽기로 집는다`() {
        // 스냅샷으로 읽으면 같은 답글을 지우는 요청과 겹쳤을 때 이미 사라진 행을 들고 와
        // 0행 UPDATE/DELETE 가 되고, 계약의 404/110200 대신 409/90003 이 나간다.
        val existing = ReviewReply.write(reviewId, sellerId, "원래 답글")
        val replies = RecordingReplyRepository(existing)
        val service = ReviewReplyService(replies, FixedReviewRepository(visibleReview()), AlwaysOwnedPort())

        service.edit(command())
        service.delete(sellerId, reviewId)

        assertEquals(2, replies.lockedReads)
        assertEquals(0, replies.snapshotReads, "수정·삭제가 스냅샷 조회로 대상을 집었다")
    }

    @Test
    fun `선조회에서 걸리면 잠금 읽기까지 가지 않는다`() {
        // 없는 id로 곧장 FOR UPDATE를 걸면 PK 갭 잠금이 생겨 남의 리뷰 작성까지 막는다
        val reviews = FixedReviewRepository(visibleReview())
        val service = ReviewReplyService(RecordingReplyRepository(), reviews, NeverOwnedPort())

        assertEquals(ReviewErrorCode.REVIEW_NOT_FOUND, assertThrows<BusinessException> { service.create(command()) }.errorCode)
        assertEquals(0, reviews.lockedReads, "선조회에서 끊긴 요청이 리뷰 행을 잠갔다")
    }

    private fun command() = WriteReviewReplyCommand(sellerId = sellerId, reviewId = reviewId, content = "실측값으로 고쳤어요.")

    private fun visibleReview(): Review =
        Review.write(
            productId = 1L,
            optionNameSnapshot = "기본",
            orderItemId = 1L,
            authorUserId = 2L,
            rating = 5,
            content = "좋아요",
        )

    private fun deletedReview(): Review = visibleReview().apply { delete() }

    private class FixedReviewRepository(
        private val review: Review?,
    ) : ReviewRepository {
        var lockedReads = 0
            private set

        override fun findById(id: Long): Review? = review

        override fun findByIdForUpdate(id: Long): Review? {
            lockedReads++
            return review
        }

        override fun save(review: Review): Review = review

        override fun existsByOrderItemId(orderItemId: Long): Boolean = false
    }

    private class RecordingReplyRepository(
        private val existing: ReviewReply? = null,
    ) : ReviewReplyRepository {
        val saved = mutableListOf<ReviewReply>()
        val deleted = mutableListOf<ReviewReply>()
        var snapshotReads = 0
            private set
        var lockedReads = 0
            private set

        override fun findByReviewId(reviewId: Long): ReviewReply? = existing.also { snapshotReads++ }

        override fun findByReviewIdForUpdate(reviewId: Long): ReviewReply? = existing.also { lockedReads++ }

        override fun save(reviewReply: ReviewReply): ReviewReply = reviewReply.also { saved += it }

        override fun delete(reviewReply: ReviewReply) {
            deleted += reviewReply
        }

        override fun deleteByReviewId(reviewId: Long): Int = 0
    }

    private abstract class StubSellerReviewQueryPort : SellerReviewQueryPort {
        override fun findPage(condition: SellerReviewListCondition): List<SellerReviewView> = emptyList()

        override fun count(condition: SellerReviewListCondition): Long = 0L

        override fun findRatingBuckets(
            sellerId: Long,
            productPublicId: String?,
        ): List<RatingBucketView> = emptyList()
    }

    private class AlwaysOwnedPort : StubSellerReviewQueryPort() {
        override fun existsOwnedVisibleReview(
            reviewId: Long,
            sellerId: Long,
        ): Boolean = true
    }

    private class NeverOwnedPort : StubSellerReviewQueryPort() {
        override fun existsOwnedVisibleReview(
            reviewId: Long,
            sellerId: Long,
        ): Boolean = false
    }
}
