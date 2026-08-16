package com.aechak.application.review.usecase

import com.aechak.application.review.usecase.result.ReviewRatingAggregateResult

interface ReviewRatingUseCase {
    fun getReviewRatingStats(productId: Long): ReviewRatingAggregateResult
}
