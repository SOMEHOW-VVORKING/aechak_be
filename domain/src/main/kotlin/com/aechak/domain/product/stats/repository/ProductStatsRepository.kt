package com.aechak.domain.product.stats.repository

import com.aechak.domain.product.stats.ProductStats
import java.math.BigDecimal

interface ProductStatsRepository {
    /** 상품 배치 조회 */
    fun findAllByProductIds(productIds: Collection<Long>): List<ProductStats>

    /** 리뷰 재계산 절대값으로 평점 집계를 upsert */
    fun upsertReviewStats(
        productId: Long,
        reviewCount: Int,
        ratingSum: Long,
        averageRating: BigDecimal?,
    )
}
