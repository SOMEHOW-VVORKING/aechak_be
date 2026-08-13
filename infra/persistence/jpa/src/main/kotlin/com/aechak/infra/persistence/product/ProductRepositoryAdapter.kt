package com.aechak.infra.persistence.product

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.repository.ProductRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Product?
}

@Repository
class ProductRepositoryAdapter(
    private val jpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product = jpaRepository.save(product)

    override fun saveNow(product: Product): Product = jpaRepository.saveAndFlush(product)

    override fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Product? = jpaRepository.findByPublicIdAndSellerId(publicId, sellerId)
}
