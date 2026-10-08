package com.aechak.api.review

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.repository.DuplicateOrderItemReviewException
import com.aechak.domain.review.review.repository.ReviewRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ReviewRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    @Test
    fun `같은 주문 품목의 UNIQUE 위반은 의미 있는 중복 리뷰 예외로 번역한다`() {
        reviewRepository.save(review(orderItemId = 100L))

        assertThrows(DuplicateOrderItemReviewException::class.java) {
            reviewRepository.save(review(orderItemId = 100L))
        }
    }

    private fun review(orderItemId: Long): Review =
        Review.write(
            productId = 1L,
            optionNameSnapshot = "블랙 / L",
            orderItemId = orderItemId,
            authorUserId = 1L,
            rating = 5,
            content = "좋은 상품입니다",
        )
}
