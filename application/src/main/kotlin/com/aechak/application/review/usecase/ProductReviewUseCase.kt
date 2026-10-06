package com.aechak.application.review.usecase

import com.aechak.application.review.usecase.query.ProductReviewListQuery
import com.aechak.application.review.usecase.result.ProductReviewListResult

interface ProductReviewUseCase {
    fun getProductReviews(
        query: ProductReviewListQuery,
        viewerId: Long,
    ): ProductReviewListResult
}
