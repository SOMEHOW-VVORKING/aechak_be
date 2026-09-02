package com.aechak.application.review.usecase.result

import com.aechak.application.review.port.view.RatingBucketView
import java.math.BigDecimal
import java.math.RoundingMode

data class ReviewRatingSummaryResult(
    val reviewCount: Long,
    val averageRating: BigDecimal?,
    val ratingDistribution: Map<Int, Long>,
) {
    companion object {
        private val STARS = 1..5

        fun from(buckets: List<RatingBucketView>): ReviewRatingSummaryResult {
            val countByStar = buckets.associate { it.rating to it.count }
            val distribution = STARS.associateWith { star -> countByStar[star] ?: 0L }
            val reviewCount = distribution.values.sum()
            val average =
                if (reviewCount == 0L) {
                    null
                } else {
                    val ratingSum = distribution.entries.sumOf { (star, count) -> star * count }
                    BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(reviewCount), 2, RoundingMode.HALF_UP)
                }
            return ReviewRatingSummaryResult(reviewCount, average, distribution)
        }
    }
}
