package com.aechak.seller.review.response

import com.aechak.application.review.seller.usecase.result.SellerReviewListResult
import java.math.BigDecimal

data class SellerReviewListResponse(
    val summary: SellerReviewSummaryResponse?,
    val reviews: List<SellerReviewItemResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: SellerReviewListResult): SellerReviewListResponse =
            SellerReviewListResponse(
                summary =
                    result.summary?.let {
                        SellerReviewSummaryResponse(
                            reviewCount = it.reviewCount,
                            averageRating = it.averageRating,
                            ratingDistribution = it.ratingDistribution,
                        )
                    },
                reviews = result.page.items.map(SellerReviewItemResponse::from),
                totalCount = result.page.totalCount,
                nextCursor = result.page.nextCursor,
                hasNext = result.page.hasNext,
            )
    }
}

/** ratingDistribution은 별점(1~5)별 개수이며 없는 별점도 0을 담는다 — 분포 막대가 빈칸을 그리지 않게. */
data class SellerReviewSummaryResponse(
    val reviewCount: Long,
    val averageRating: BigDecimal?,
    val ratingDistribution: Map<Int, Long>,
)
