package com.aechak.infra.persistence.inquiry

import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.repository.ProductInquiryRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ProductInquiryJpaRepository : JpaRepository<ProductInquiry, Long> {
    fun findByPublicId(publicId: String): ProductInquiry?
}

@Repository
class ProductInquiryRepositoryAdapter(
    private val jpaRepository: ProductInquiryJpaRepository,
) : ProductInquiryRepository {
    override fun save(inquiry: ProductInquiry): ProductInquiry = jpaRepository.save(inquiry)

    override fun findByPublicId(publicId: String): ProductInquiry? = jpaRepository.findByPublicId(publicId)
}
