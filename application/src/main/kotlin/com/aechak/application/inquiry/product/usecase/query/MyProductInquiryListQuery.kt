package com.aechak.application.inquiry.product.usecase.query

import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus

/** 내 문의 목록 조회 입력. 상태가 없으면 철회분까지 전부. */
data class MyProductInquiryListQuery(
    val authorUserId: Long,
    val status: ProductInquiryStatus? = null,
    val page: Int = 0,
    val size: Int = ProductInquiryPageSize.DEFAULT,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size in ProductInquiryPageSize.MIN..ProductInquiryPageSize.MAX) {
            "size는 ${ProductInquiryPageSize.MIN}~${ProductInquiryPageSize.MAX} 범위 안에 있어야 합니다."
        }
    }
}
