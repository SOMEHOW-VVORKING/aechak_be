package com.aechak.infra.persistence.product

import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.repository.ProductVersionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ProductVersionJpaRepository : JpaRepository<ProductVersion, Long> {
    @Query("select max(v.versionNo) from ProductVersion v where v.product.id = :productId")
    fun findLastVersionNo(
        @Param("productId") productId: Long,
    ): Int?
}

@Repository
class ProductVersionRepositoryAdapter(
    private val jpaRepository: ProductVersionJpaRepository,
) : ProductVersionRepository {
    override fun save(productVersion: ProductVersion): ProductVersion = jpaRepository.save(productVersion)

    override fun findLastVersionNo(productId: Long): Int? = jpaRepository.findLastVersionNo(productId)
}
