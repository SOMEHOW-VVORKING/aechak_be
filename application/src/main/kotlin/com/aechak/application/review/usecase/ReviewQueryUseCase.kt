package com.aechak.application.review.usecase

import com.aechak.application.review.usecase.query.MyReviewListQuery
import com.aechak.application.review.usecase.query.ProductReviewListQuery
import com.aechak.application.review.usecase.result.MyReviewListItemResult
import com.aechak.application.review.usecase.result.ProductReviewListResult
import com.aechak.application.support.CursorPageResult

interface ReviewQueryUseCase {
    fun getProductReviews(
        query: ProductReviewListQuery,
        viewerId: Long,
    ): ProductReviewListResult

    fun getMyReviews(query: MyReviewListQuery): CursorPageResult<MyReviewListItemResult>
}
