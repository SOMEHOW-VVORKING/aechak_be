package com.aechak.application.review.facade

import com.aechak.application.review.service.ProductReviewService
import com.aechak.application.review.usecase.ReviewRatingUseCase
import com.aechak.application.review.usecase.result.ReviewRatingAggregateResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewRatingFacade(
    private val productReviewService: ProductReviewService,
) : ReviewRatingUseCase {
    @Transactional(readOnly = true)
    override fun getReviewRatingStats(productId: Long): ReviewRatingAggregateResult =
        ReviewRatingAggregateResult.from(productReviewService.getRatingBuckets(productId))
}
