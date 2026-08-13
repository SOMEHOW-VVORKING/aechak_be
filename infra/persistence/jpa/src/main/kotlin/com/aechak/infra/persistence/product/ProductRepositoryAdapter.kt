package com.aechak.infra.persistence.product

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.repository.ProductRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    @Query("select p.id from Product p where p.publicId = :publicId")
    fun findIdByPublicId(
        @Param("publicId") publicId: String,
    ): Long?
}

@Repository
class ProductRepositoryAdapter(
    private val jpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun findIdByPublicId(publicId: String): Long? = jpaRepository.findIdByPublicId(publicId)
}
