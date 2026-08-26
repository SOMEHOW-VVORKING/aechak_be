package com.aechak.domain.review.review

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewTest {
    private fun aReview(rating: Int = 5): Review =
        Review.write(
            productId = 1L,
            optionNameSnapshot = "블랙 / L",
            orderItemId = 1L,
            authorUserId = 1L,
            rating = rating,
            content = "좋은 상품입니다",
        )

    @Test
    fun `작성하면 PUBLIC 상태이고 삭제 시각이 없다`() {
        val review = aReview()

        assertEquals(ReviewStatus.PUBLIC, review.reviewStatus)
        assertNull(review.deletedAt)
    }

    @Test
    fun `별점이 상한 5를 넘으면 작성할 수 없다`() {
        val e = assertFailsWith<BusinessException> { aReview(rating = 6) }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_RATING, e.errorCode)
    }

    @Test
    fun `별점이 하한 1 미만이면 작성할 수 없다`() {
        val e = assertFailsWith<BusinessException> { aReview(rating = 0) }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_RATING, e.errorCode)
    }

    @Test
    fun `별점 경계값 1과 5는 허용된다`() {
        assertEquals(1, aReview(rating = 1).rating)
        assertEquals(5, aReview(rating = 5).rating)
    }

    @Test
    fun `사진을 첨부해 작성하면 이미지가 순서대로 담긴다`() {
        val review =
            Review.write(
                productId = 1L,
                optionNameSnapshot = "블랙 / L",
                orderItemId = 1L,
                authorUserId = 1L,
                rating = 5,
                content = "좋은 상품입니다",
                images = listOf(ReviewImage.of("k0", 0), ReviewImage.of("k1", 1)),
            )

        assertEquals(2, review.images.size)
        assertEquals(listOf(0, 1), review.images.map { it.sortOrder })
    }

    @Test
    fun `사진이 상한 5장을 넘으면 작성할 수 없다`() {
        val tooMany = (0..5).map { ReviewImage.of("k$it", it) }

        val e =
            assertFailsWith<BusinessException> {
                Review.write(
                    productId = 1L,
                    optionNameSnapshot = "블랙 / L",
                    orderItemId = 1L,
                    authorUserId = 1L,
                    rating = 5,
                    content = "좋은 상품입니다",
                    images = tooMany,
                )
            }

        assertEquals(ReviewErrorCode.REVIEW_TOO_MANY_IMAGES, e.errorCode)
    }

    @Test
    fun `마스킹하면 MASKED 상태와 노출 문구를 저장한다`() {
        val review = aReview()

        review.mask("** 상품입니다")

        assertEquals(ReviewStatus.MASKED, review.reviewStatus)
        assertEquals("** 상품입니다", review.displayContent)
    }

    @Test
    fun `차단하면 BLOCKED 상태가 된다`() {
        val review = aReview()

        review.block()

        assertEquals(ReviewStatus.BLOCKED, review.reviewStatus)
    }

    @Test
    fun `삭제하면 DELETED 상태가 되고 삭제 시각이 채워진다`() {
        val review = aReview()

        review.delete()

        assertEquals(ReviewStatus.DELETED, review.reviewStatus)
        assertNotNull(review.deletedAt)
    }

    @Test
    fun `DELETED 리뷰를 다시 삭제하면 INVALID_REVIEW_STATUS_TRANSITION 오류를 던진다`() {
        val review = aReview()
        review.delete()

        val e = assertFailsWith<BusinessException> { review.delete() }

        assertEquals(ReviewErrorCode.INVALID_REVIEW_STATUS_TRANSITION, e.errorCode)
    }

    @Test
    fun `마스킹된 리뷰는 대체 본문을, 대체 본문이 없으면 블라인드 문구를 노출한다`() {
        assertEquals(
            "이 **럼아",
            Review.visibleContent(ReviewStatus.MASKED, content = "이 씨발럼아", displayContent = "이 **럼아"),
        )
        assertEquals(
            "블라인드 처리된 리뷰입니다.",
            Review.visibleContent(ReviewStatus.MASKED, content = "이 씨발럼아", displayContent = null),
        )
    }

    @Test
    fun `마스킹이 아닌 상태는 본문을 그대로 노출한다`() {
        listOf(ReviewStatus.PUBLIC, ReviewStatus.BLOCKED, ReviewStatus.HIDDEN, ReviewStatus.DELETED).forEach { status ->
            assertEquals(
                "원문입니다",
                Review.visibleContent(status, content = "원문입니다", displayContent = "대체 본문"),
                "$status 는 본문을 그대로 둬야 한다",
            )
        }
    }

    @Test
    fun `작성 마감은 구매확정일로부터 30일째 되는 날의 끝이다`() {
        val confirmedAt = LocalDateTime.of(2026, 1, 1, 12, 0)

        assertEquals(LocalDateTime.of(2026, 1, 31, 23, 59, 59, 999_999_999), Review.writeDeadline(confirmedAt))
    }

    @Test
    fun `같은 날 구매확정이면 시각이 달라도 마감이 같다`() {
        val deadline = Review.writeDeadline(LocalDateTime.of(2026, 1, 1, 0, 0))

        assertEquals(deadline, Review.writeDeadline(LocalDateTime.of(2026, 1, 1, 23, 59, 59)))
    }

    @Test
    fun `마감일 오후에도 작성할 수 있고 자정을 넘기면 못 쓴다`() {
        val confirmedAt = LocalDateTime.of(2026, 1, 1, 12, 0)
        val deadline = Review.writeDeadline(confirmedAt)

        assertTrue(Review.isWithinWriteWindow(confirmedAt, confirmedAt))
        assertTrue(Review.isWithinWriteWindow(confirmedAt, LocalDateTime.of(2026, 1, 31, 23, 0)))
        assertTrue(Review.isWithinWriteWindow(confirmedAt, deadline))
        assertFalse(Review.isWithinWriteWindow(confirmedAt, deadline.plusNanos(1)))
    }
}
