package com.aechak.infra.persistence.product

import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.repository.ProductVersionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ProductVersionJpaRepository : JpaRepository<ProductVersion, Long>

@Repository
class ProductVersionRepositoryAdapter(
    private val jpaRepository: ProductVersionJpaRepository,
) : ProductVersionRepository {
    override fun save(productVersion: ProductVersion): ProductVersion = jpaRepository.save(productVersion)
}
