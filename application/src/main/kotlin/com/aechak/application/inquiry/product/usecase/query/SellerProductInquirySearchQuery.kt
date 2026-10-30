package com.aechak.application.inquiry.product.usecase.query

import com.aechak.application.inquiry.product.port.SellerProductInquiryStatusFilter
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory

/** 셀러가 받은 문의 목록 조회 입력 — 상태 탭 + 유형 필터 + 검색어 + 오프셋 페이징. */
data class SellerProductInquirySearchQuery(
    val sellerId: Long,
    val status: SellerProductInquiryStatusFilter? = null,
    val category: ProductInquiryCategory? = null,
    val keyword: String? = null,
    val page: Int = 0,
    val size: Int = ProductInquiryPageSize.DEFAULT,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size in ProductInquiryPageSize.MIN..ProductInquiryPageSize.MAX) {
            "size는 ${ProductInquiryPageSize.MIN}~${ProductInquiryPageSize.MAX} 범위 안에 있어야 합니다."
        }
        keyword?.let {
            require(it.isNotBlank()) { "keyword는 공백일 수 없습니다." }
            require(it.length <= KEYWORD_MAX) { "keyword는 최대 $KEYWORD_MAX 자입니다." }
        }
    }

    companion object {
        const val KEYWORD_MAX = 100
    }
}
