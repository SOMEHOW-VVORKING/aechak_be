package com.aechak.api.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.MyProductInquiryItemResult
import com.aechak.application.support.OffsetPageResult

data class MyProductInquiryListResponse(
    val inquiries: List<MyProductInquiryItemResponse>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: OffsetPageResult<MyProductInquiryItemResult>): MyProductInquiryListResponse =
            MyProductInquiryListResponse(
                inquiries = page.items.map(MyProductInquiryItemResponse::from),
                totalCount = page.totalCount,
                page = page.page,
                size = page.size,
                totalPages = page.totalPages,
                hasNext = page.hasNext,
            )
    }
}
