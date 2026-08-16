package com.aechak.application.product.stats.facade

import com.aechak.application.product.stats.service.ProductStatsService
import com.aechak.application.product.stats.usecase.ProductStatsUseCase
import com.aechak.application.review.usecase.ReviewRatingUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 평점 집계 투영 */
@Service
class ProductStatsFacade(
    private val productStatsService: ProductStatsService,
    private val reviewRatingUseCase: ReviewRatingUseCase,
) : ProductStatsUseCase {
    @Transactional
    override fun recomputeReviewStats(productId: Long) {
        val stats = reviewRatingUseCase.getReviewRatingStats(productId)
        productStatsService.applyReviewStats(
            productId = productId,
            reviewCount = stats.reviewCount.toInt(),
            ratingSum = stats.ratingSum,
            averageRating = stats.averageRating,
        )
    }
}
