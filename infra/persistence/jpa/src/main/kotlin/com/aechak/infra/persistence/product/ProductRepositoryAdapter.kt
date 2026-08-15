package com.aechak.infra.persistence.product

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.repository.ProductRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Product?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): Product?
}

@Repository
class ProductRepositoryAdapter(
    private val jpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product = jpaRepository.save(product)

    override fun saveNow(product: Product): Product = jpaRepository.saveAndFlush(product)

    override fun findByIdForUpdate(id: Long): Product? = jpaRepository.findByIdForUpdate(id)

    override fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Product? = jpaRepository.findByPublicIdAndSellerId(publicId, sellerId)
}
