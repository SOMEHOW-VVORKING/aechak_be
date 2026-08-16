package com.aechak.api.review

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.stats.usecase.ProductStatsUseCase
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import com.aechak.domain.review.review.Review
import com.aechak.domain.user.point.policy.ReviewRewardPolicy
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

/**
 * 리뷰 작성 이벤트를 받은 컨슈머가 호출하는 두 UseCase(평점 재계산, 적립 지급)를 직접 검증한다.
 * 아웃박스·Kafka 없이 재계산의 정확성과 재실행 멱등성만 본다.
 */
class ReviewProjectionIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var productStatsUseCase: ProductStatsUseCase

    @Autowired
    private lateinit var pointUseCase: PointUseCase

    @Autowired
    private lateinit var productStatsRepository: ProductStatsRepository

    @Test
    fun `평점 재계산은 노출 리뷰에서 리뷰 수와 합계와 평균을 절대값으로 채운다`() {
        val productId = 100L
        persistReviews(productId, ratings = listOf(5, 4, 3), authorUserId = 1L)

        productStatsUseCase.recomputeReviewStats(productId)

        val stats = productStatsRepository.findAllByProductIds(listOf(productId)).single()
        assertEquals(3, stats.reviewCount)
        assertEquals(12L, stats.ratingSum)
        assertEquals(0, BigDecimal("4.00").compareTo(stats.averageRating))
    }

    @Test
    fun `평점 재계산을 반복해도 결과가 같다`() {
        val productId = 200L
        persistReviews(productId, ratings = listOf(5, 5), authorUserId = 2L)

        productStatsUseCase.recomputeReviewStats(productId)
        productStatsUseCase.recomputeReviewStats(productId)

        val stats = productStatsRepository.findAllByProductIds(listOf(productId)).single()
        assertEquals(2, stats.reviewCount)
        assertEquals(10L, stats.ratingSum)
        assertEquals(0, BigDecimal("5.00").compareTo(stats.averageRating))
    }

    @Test
    fun `포토 리뷰 적립은 EARN 원장과 잔액 캐시에 정책 금액을 반영하고 같은 리뷰 재지급은 무연산이다`() {
        val userId = createActiveUser()
        val reviewId = 777L

        pointUseCase.earnReviewReward(userId, reviewId, hasPhoto = true)
        pointUseCase.earnReviewReward(userId, reviewId, hasPhoto = true)

        val (ledgerCount, balance) =
            tx.execute {
                val count =
                    em
                        .createQuery(
                            "select count(t) from PointTransaction t where t.idempotencyKey = :key",
                            java.lang.Long::class.java,
                        ).setParameter("key", "EARN:REVIEW_REWARD:$reviewId")
                        .singleResult
                val user = em.find(User::class.java, userId)
                count.toLong() to user.pointBalance
            }!!

        assertEquals(1L, ledgerCount)
        assertEquals(ReviewRewardPolicy.amountFor(hasPhoto = true), balance)
    }

    private fun persistReviews(
        productId: Long,
        ratings: List<Int>,
        authorUserId: Long,
    ) {
        tx.execute {
            ratings.forEachIndexed { index, rating ->
                em.persist(
                    Review.write(
                        productId = productId,
                        optionNameSnapshot = "블랙 / L",
                        orderItemId = productId * 100 + index, // 1주문1리뷰 UNIQUE 회피용 고유값
                        authorUserId = authorUserId,
                        rating = rating,
                        content = "좋은 상품입니다",
                    ),
                )
            }
        }
    }
}
