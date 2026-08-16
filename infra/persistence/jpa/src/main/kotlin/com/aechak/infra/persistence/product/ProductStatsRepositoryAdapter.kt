package com.aechak.infra.persistence.product

import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

interface ProductStatsJpaRepository : JpaRepository<ProductStats, Long> {
    fun findAllByProductIdIn(productIds: Collection<Long>): List<ProductStats>

    @Modifying
    @Query(
        value = """
            INSERT INTO product_stats (product_id, review_count, rating_sum, average_rating, like_count, created_at, updated_at)
            VALUES (:productId, :reviewCount, :ratingSum, :averageRating, 0, NOW(6), NOW(6)) AS new
            ON DUPLICATE KEY UPDATE
                review_count = new.review_count,
                rating_sum = new.rating_sum,
                average_rating = new.average_rating,
                updated_at = NOW(6)
        """,
        nativeQuery = true,
    )
    fun upsertReviewStats(
        @Param("productId") productId: Long,
        @Param("reviewCount") reviewCount: Int,
        @Param("ratingSum") ratingSum: Long,
        @Param("averageRating") averageRating: BigDecimal?,
    ): Int
}

@Repository
class ProductStatsRepositoryAdapter(
    private val jpaRepository: ProductStatsJpaRepository,
) : ProductStatsRepository {
    override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStats> =
        if (productIds.isEmpty()) emptyList() else jpaRepository.findAllByProductIdIn(productIds)

    override fun upsertReviewStats(
        productId: Long,
        reviewCount: Int,
        ratingSum: Long,
        averageRating: BigDecimal?,
    ) {
        jpaRepository.upsertReviewStats(productId, reviewCount, ratingSum, averageRating)
    }
}
