package com.aechak.infra.persistence.inquiry

import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.repository.ProductInquiryRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ProductInquiryJpaRepository : JpaRepository<ProductInquiry, Long> {
    /** public_id에 UNIQUE가 있어 단일 행 잠금이다 — 범위 잠금으로 번지지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProductInquiry i where i.publicId = :publicId")
    fun findByPublicIdForUpdate(
        @Param("publicId") publicId: String,
    ): ProductInquiry?
}

@Repository
class ProductInquiryRepositoryAdapter(
    private val jpaRepository: ProductInquiryJpaRepository,
) : ProductInquiryRepository {
    override fun save(inquiry: ProductInquiry): ProductInquiry = jpaRepository.save(inquiry)

    override fun findByPublicIdForUpdate(publicId: String): ProductInquiry? = jpaRepository.findByPublicIdForUpdate(publicId)
}
