package com.aechak.application.product.stats.service

import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

/** 상품 통계 조회 */
@Service
class ProductStatsService(
    private val productStatsRepository: ProductStatsRepository,
) {
    /** 상품 통계 배치 조회 */
    fun getStatsByProductIds(productIds: List<Long>): Map<Long, ProductStats> =
        productStatsRepository.findAllByProductIds(productIds).associateBy { it.productId }

    /** 리뷰에서 재계산한 절대값으로 평점 집계와 검색 인기순용 상품 리뷰 수를 같은 트랜잭션에서 반영 */
    fun applyReviewStats(
        productId: Long,
        reviewCount: Int,
        ratingSum: Long,
        averageRating: BigDecimal?,
    ) {
        productStatsRepository.upsertReviewStats(productId, reviewCount, ratingSum, averageRating)
        productStatsRepository.updateProductReviewCount(productId, reviewCount)
    }
}
