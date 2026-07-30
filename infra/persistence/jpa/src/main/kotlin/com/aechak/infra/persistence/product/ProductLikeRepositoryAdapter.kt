package com.aechak.infra.persistence.product

import com.aechak.domain.product.like.ProductLike
import com.aechak.domain.product.like.repository.ProductLikeRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ProductLikeJpaRepository : JpaRepository<ProductLike, Long> {
    fun existsByProductIdAndUserId(
        productId: Long,
        userId: Long,
    ): Boolean
}

@Repository
class ProductLikeRepositoryAdapter(
    private val jpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun existsByProductIdAndUserId(
        productId: Long,
        userId: Long,
    ): Boolean = jpaRepository.existsByProductIdAndUserId(productId, userId)
}
