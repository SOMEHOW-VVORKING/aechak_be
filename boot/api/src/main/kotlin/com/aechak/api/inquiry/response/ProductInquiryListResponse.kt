package com.aechak.api.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.ProductInquiryItemResult
import com.aechak.application.support.OffsetPageResult

data class ProductInquiryListResponse(
    val inquiries: List<ProductInquiryItemResponse>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: OffsetPageResult<ProductInquiryItemResult>): ProductInquiryListResponse =
            ProductInquiryListResponse(
                inquiries = page.items.map(ProductInquiryItemResponse::from),
                totalCount = page.totalCount,
                page = page.page,
                size = page.size,
                totalPages = page.totalPages,
                hasNext = page.hasNext,
            )
    }
}
