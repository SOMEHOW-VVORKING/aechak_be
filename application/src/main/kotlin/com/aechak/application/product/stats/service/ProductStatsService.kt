package com.aechak.application.product.stats.service

import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import org.springframework.stereotype.Service

/** 상품 통계 조회 — 카탈로그와 검색이 공유하는 공통 관심사 */
@Service
class ProductStatsService(
    private val productStatsRepository: ProductStatsRepository,
) {
    /** 상품 통계 배치 조회 */
    fun getStatsByProductIds(productIds: List<Long>): Map<Long, ProductStats> =
        productStatsRepository.findAllByProductIds(productIds).associateBy { it.productId }
}
