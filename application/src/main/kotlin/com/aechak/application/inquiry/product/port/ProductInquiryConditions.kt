package com.aechak.application.inquiry.product.port

import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus

/** 상품 상세의 문의 목록 조건 — 철회된 문의는 포트가 걸러낸다. */
data class ProductInquiryPageCondition(
    val productId: Long,
    val offset: Long,
    val limit: Int,
) {
    init {
        require(offset >= 0) { "offset은 0 이상이어야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}

/** 내 문의 목록 조건 — 철회한 문의도 본인에게는 보인다. */
data class MyProductInquiryCondition(
    val authorUserId: Long,
    val status: ProductInquiryStatus?,
    val offset: Long,
    val limit: Int,
) {
    init {
        require(offset >= 0) { "offset은 0 이상이어야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}

/** 셀러가 받은 문의 목록 조건. */
data class SellerProductInquiryCondition(
    val sellerId: Long,
    val status: SellerProductInquiryStatusFilter?,
    val category: ProductInquiryCategory?,
    val keyword: String?,
    val offset: Long,
    val limit: Int,
) {
    init {
        require(offset >= 0) { "offset은 0 이상이어야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
