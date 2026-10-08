package com.aechak.application.review.facade

import com.aechak.application.product.stats.usecase.ProductStatsUseCase
import com.aechak.application.product.stats.usecase.command.ApplyReviewStatsCommand
import com.aechak.application.review.service.ReviewQueryService
import com.aechak.application.review.usecase.ReviewRatingUseCase
import com.aechak.application.review.usecase.result.ReviewRatingAggregateResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewRatingFacade(
    private val reviewQueryService: ReviewQueryService,
    private val productStatsUseCase: ProductStatsUseCase,
) : ReviewRatingUseCase {
    @Transactional
    override fun recomputeProductRating(productId: Long) {
        val aggregate = ReviewRatingAggregateResult.from(reviewQueryService.getRatingBuckets(productId))
        productStatsUseCase.applyReviewStats(
            ApplyReviewStatsCommand(
                productId = productId,
                reviewCount = aggregate.reviewCount.toInt(),
                ratingSum = aggregate.ratingSum,
                averageRating = aggregate.averageRating,
            ),
        )
    }
}
