package com.aechak.application.review.usecase.result

import com.aechak.application.review.port.view.RatingBucketView
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewRatingSummaryResultTest {
    @Test
    fun `버킷에서 개수 평균 분포를 파생하고 빠진 별점은 0으로 채운다`() {
        val summary =
            ReviewRatingSummaryResult.from(
                listOf(RatingBucketView(5, 6), RatingBucketView(4, 3), RatingBucketView(2, 1)),
            )

        assertEquals(10L, summary.reviewCount)
        assertEquals(0, BigDecimal("4.40").compareTo(summary.averageRating))
        assertEquals(mapOf(1 to 0L, 2 to 1L, 3 to 0L, 4 to 3L, 5 to 6L), summary.ratingDistribution)
    }

    @Test
    fun `리뷰가 없으면 개수는 0이고 평균은 null이며 분포는 전부 0이다`() {
        val summary = ReviewRatingSummaryResult.from(emptyList())

        assertEquals(0L, summary.reviewCount)
        assertNull(summary.averageRating)
        assertEquals(mapOf(1 to 0L, 2 to 0L, 3 to 0L, 4 to 0L, 5 to 0L), summary.ratingDistribution)
    }
}
