package com.aechak.application.product.stats.usecase

interface ProductStatsUseCase {
    fun recomputeReviewStats(productId: Long)
}
