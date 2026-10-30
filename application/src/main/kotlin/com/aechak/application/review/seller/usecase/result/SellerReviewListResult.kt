package com.aechak.application.review.seller.usecase.result

import com.aechak.application.review.usecase.result.ReviewRatingSummaryResult
import com.aechak.application.support.CursorPageResult

/** summary는 첫 페이지에만 담긴다 — 분포 집계를 페이지마다 다시 세지 않는다. */
data class SellerReviewListResult(
    val summary: ReviewRatingSummaryResult?,
    val page: CursorPageResult<SellerReviewItemResult>,
)
