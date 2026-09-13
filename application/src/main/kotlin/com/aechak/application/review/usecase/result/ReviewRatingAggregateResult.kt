package com.aechak.application.review.usecase.result

import com.aechak.application.review.port.view.RatingBucketView
import java.math.BigDecimal
import java.math.RoundingMode

/** 상품 평점 집계 절대값(리뷰 수, 별점 합, 평균). */
data class ReviewRatingAggregateResult(
    val reviewCount: Long,
    val ratingSum: Long,
    val averageRating: BigDecimal?,
) {
    companion object {
        fun from(buckets: List<RatingBucketView>): ReviewRatingAggregateResult {
            val reviewCount = buckets.sumOf { it.count }
            val ratingSum = buckets.sumOf { it.rating * it.count }
            val average =
                if (reviewCount == 0L) {
                    null
                } else {
                    BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(reviewCount), 2, RoundingMode.HALF_UP)
                }
            return ReviewRatingAggregateResult(reviewCount, ratingSum, average)
        }
    }
}
