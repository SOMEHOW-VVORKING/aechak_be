package com.aechak.infra.persistence.product

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.repository.ProductRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ProductJpaRepository : JpaRepository<Product, Long>

@Repository
class ProductRepositoryAdapter(
    private val jpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product = jpaRepository.save(product)
}
