package com.aechak.api.review.response

import com.aechak.application.review.usecase.result.ReviewRatingSummaryResult
import java.math.BigDecimal

/** ratingDistribution은 별점(1~5)별 개수이며 없는 별점도 0을 담는다. */
data class ReviewRatingSummaryResponse(
    val reviewCount: Long,
    val averageRating: BigDecimal?,
    val ratingDistribution: Map<Int, Long>,
) {
    companion object {
        fun from(result: ReviewRatingSummaryResult): ReviewRatingSummaryResponse =
            ReviewRatingSummaryResponse(
                reviewCount = result.reviewCount,
                averageRating = result.averageRating,
                ratingDistribution = result.ratingDistribution,
            )
    }
}
