package com.aechak.domain.inquiry.product.repository

import com.aechak.domain.inquiry.product.ProductInquiry

interface ProductInquiryRepository {
    fun save(inquiry: ProductInquiry): ProductInquiry

    fun findByPublicId(publicId: String): ProductInquiry?
}
