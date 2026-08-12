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

    // 통계 행이 없는 상품도 누락 없이 집계하도록 원자 upsert, 감사 컬럼은 NOT NULL이라 함께 세팅
    @Modifying
    @Query(
        value =
            "INSERT INTO product_stats " +
                "(product_id, review_count, rating_sum, average_rating, like_count, created_at, updated_at) " +
                "VALUES (:productId, 0, 0, NULL, 1, NOW(), NOW()) " +
                "ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()",
        nativeQuery = true,
    )
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
