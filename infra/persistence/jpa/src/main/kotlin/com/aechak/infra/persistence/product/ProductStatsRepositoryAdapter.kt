package com.aechak.infra.persistence.product

import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ProductStatsJpaRepository : JpaRepository<ProductStats, Long> {
    fun findAllByProductIdIn(productIds: Collection<Long>): List<ProductStats>

    // 조건부 원자 UPDATE, updated_at 함께 세팅
    @Modifying
    @Query("update ProductStats s set s.likeCount = s.likeCount + 1, s.updatedAt = CURRENT_TIMESTAMP where s.productId = :productId")
    fun increaseLikeCount(
        @Param("productId") productId: Long,
    ): Int

    @Modifying
    @Query(
        "update ProductStats s set s.likeCount = s.likeCount - 1, s.updatedAt = CURRENT_TIMESTAMP " +
            "where s.productId = :productId and s.likeCount > 0",
    )
    fun decreaseLikeCount(
        @Param("productId") productId: Long,
    ): Int
}

@Repository
class ProductStatsRepositoryAdapter(
    private val jpaRepository: ProductStatsJpaRepository,
) : ProductStatsRepository {
    override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStats> =
        if (productIds.isEmpty()) emptyList() else jpaRepository.findAllByProductIdIn(productIds)

    override fun increaseLikeCount(productId: Long): Int = jpaRepository.increaseLikeCount(productId)

    override fun decreaseLikeCount(productId: Long): Int = jpaRepository.decreaseLikeCount(productId)
}
