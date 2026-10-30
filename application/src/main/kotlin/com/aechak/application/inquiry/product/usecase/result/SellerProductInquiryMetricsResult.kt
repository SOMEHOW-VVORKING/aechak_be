package com.aechak.application.inquiry.product.usecase.result

import com.aechak.application.inquiry.product.port.view.SellerProductInquiryMetricsView

/** 셀러 문의 화면 상단 지표 3종. 탭·필터와 무관한 셀러 전체 범위다. */
data class SellerProductInquiryMetricsResult(
    val pending: Long,
    val overdue: Long,
    val total: Long,
) {
    companion object {
        fun from(view: SellerProductInquiryMetricsView): SellerProductInquiryMetricsResult =
            SellerProductInquiryMetricsResult(pending = view.pending, overdue = view.overdue, total = view.total)
    }
}
