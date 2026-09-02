package com.aechak.api.review.response

import com.aechak.application.review.usecase.result.ProductReviewListResult

data class ReviewListResponse(
    val summary: ReviewRatingSummaryResponse?,
    val reviews: List<ReviewItemResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: ProductReviewListResult): ReviewListResponse =
            ReviewListResponse(
                summary = result.summary?.let { ReviewRatingSummaryResponse.from(it) },
                reviews = result.page.items.map(ReviewItemResponse::from),
                totalCount = result.page.totalCount,
                nextCursor = result.page.nextCursor,
                hasNext = result.page.hasNext,
            )
    }
}
