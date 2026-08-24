package com.aechak.application.review.usecase.result

import com.aechak.application.support.CursorPageResult

data class ProductReviewListResult(
    val summary: ReviewRatingSummaryResult?,
    val page: CursorPageResult<ReviewItemResult>,
)
