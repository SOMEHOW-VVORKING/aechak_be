package com.aechak.domain.review.review

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.review.enums.ReviewStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
