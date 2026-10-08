package com.aechak.infra.persistence.product.like

import com.aechak.domain.product.like.ProductLike
import com.aechak.domain.product.like.repository.ProductLikeRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ProductLikeJpaRepository : JpaRepository<ProductLike, Long> {
    fun existsByProductIdAndUserId(
        productId: Long,
        userId: Long,
    ): Boolean

    @Query("select pl.product.id from ProductLike pl where pl.userId = :userId and pl.product.id in :productIds")
    fun findLikedProductIds(
        @Param("userId") userId: Long,
        @Param("productIds") productIds: Collection<Long>,
    ): List<Long>

    // 멱등 원자 삽입 (충돌은 0행으로 흡수), 감사 컬럼은 NOT NULL이라 함께 세팅
    @Modifying
    @Query(
        value =
            "INSERT IGNORE INTO product_likes (product_id, user_id, created_at, updated_at) " +
                "VALUES (:productId, :userId, NOW(), NOW())",
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("productId") productId: Long,
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query("delete from ProductLike pl where pl.product.id = :productId and pl.userId = :userId")
    fun deleteByProductIdAndUserId(
        @Param("productId") productId: Long,
        @Param("userId") userId: Long,
    ): Int
}

@Repository
class ProductLikeRepositoryAdapter(
    private val jpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun existsByProductIdAndUserId(
        productId: Long,
        userId: Long,
    ): Boolean = jpaRepository.existsByProductIdAndUserId(productId, userId)

    override fun findLikedProductIds(
        userId: Long,
        productIds: Collection<Long>,
    ): Set<Long> = if (productIds.isEmpty()) emptySet() else jpaRepository.findLikedProductIds(userId, productIds).toSet()

    override fun insertIfAbsent(
        productId: Long,
        userId: Long,
    ): Boolean = jpaRepository.insertIgnore(productId, userId) > 0

    override fun deleteByProductIdAndUserId(
        productId: Long,
        userId: Long,
    ): Int = jpaRepository.deleteByProductIdAndUserId(productId, userId)
}
