package com.aechak.domain.product.stats.repository

import com.aechak.domain.product.stats.ProductStats

interface ProductStatsRepository {
    /** 상품 배치 조회 */
    fun findAllByProductIds(productIds: Collection<Long>): List<ProductStats>

    fun save(productStats: ProductStats): ProductStats

    /** 찜 수 +1 원자 갱신, 갱신 행 수 반환 */
    fun increaseLikeCount(productId: Long): Int

    /** 찜 수 -1 원자 갱신(like_count > 0), 갱신 행 수 반환 */
    fun decreaseLikeCount(productId: Long): Int
}
