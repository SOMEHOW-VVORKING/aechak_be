package com.aechak.domain.product.stats.repository

import com.aechak.domain.product.stats.ProductStats
import java.math.BigDecimal

interface ProductStatsRepository {
    /** 상품 배치 조회 */
    fun findAllByProductIds(productIds: Collection<Long>): List<ProductStats>

    fun save(productStats: ProductStats): ProductStats

    /** 찜 수 +1 원자 갱신, 갱신 행 수 반환 */
    fun increaseLikeCount(productId: Long): Int

    /** 찜 수 -1 원자 갱신(like_count > 0), 갱신 행 수 반환 */
    fun decreaseLikeCount(productId: Long): Int

    /** 리뷰 재계산 절대값으로 평점 집계를 upsert */
    fun upsertReviewStats(
        productId: Long,
        reviewCount: Int,
        ratingSum: Long,
        averageRating: BigDecimal?,
    )
}
