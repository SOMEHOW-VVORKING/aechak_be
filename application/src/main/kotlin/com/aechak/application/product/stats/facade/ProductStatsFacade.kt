package com.aechak.application.product.stats.facade

import com.aechak.application.product.stats.service.ProductStatsService
import com.aechak.application.product.stats.usecase.ProductStatsUseCase
import com.aechak.application.product.stats.usecase.command.ApplyReviewStatsCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 평점 집계 반영 */
@Service
class ProductStatsFacade(
    private val productStatsService: ProductStatsService,
) : ProductStatsUseCase {
    @Transactional
    override fun applyReviewStats(command: ApplyReviewStatsCommand) {
        productStatsService.applyReviewStats(
            productId = command.productId,
            reviewCount = command.reviewCount,
            ratingSum = command.ratingSum,
            averageRating = command.averageRating,
        )
    }
}
