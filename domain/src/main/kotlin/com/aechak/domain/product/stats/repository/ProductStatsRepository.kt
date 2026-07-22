package com.aechak.domain.product.stats.repository

import com.aechak.domain.product.stats.ProductStats

interface ProductStatsRepository {
    /** 상품 배치 조회 */
    fun findAllByProductIds(productIds: Collection<Long>): List<ProductStats>
}
