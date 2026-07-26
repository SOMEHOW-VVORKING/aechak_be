package com.aechak.infra.persistence.product

import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ProductStatsJpaRepository : JpaRepository<ProductStats, Long> {
    fun findAllByProductIdIn(productIds: Collection<Long>): List<ProductStats>
}

@Repository
class ProductStatsRepositoryAdapter(
    private val jpaRepository: ProductStatsJpaRepository,
) : ProductStatsRepository {
    override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStats> =
        if (productIds.isEmpty()) emptyList() else jpaRepository.findAllByProductIdIn(productIds)
}
