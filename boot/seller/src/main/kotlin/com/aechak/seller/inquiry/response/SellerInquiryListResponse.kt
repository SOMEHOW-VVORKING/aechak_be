package com.aechak.seller.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.SellerProductInquiryPageResult

data class SellerInquiryListResponse(
    val inquiries: List<SellerInquirySummaryResponse>,
    val metrics: SellerInquiryMetricsResponse,
    val totalCount: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(result: SellerProductInquiryPageResult): SellerInquiryListResponse =
            SellerInquiryListResponse(
                inquiries = result.page.items.map(SellerInquirySummaryResponse::from),
                metrics = SellerInquiryMetricsResponse.from(result.metrics),
                totalCount = result.page.totalCount,
                page = result.page.page,
                size = result.page.size,
                totalPages = result.page.totalPages,
                hasNext = result.page.hasNext,
            )
    }
}
