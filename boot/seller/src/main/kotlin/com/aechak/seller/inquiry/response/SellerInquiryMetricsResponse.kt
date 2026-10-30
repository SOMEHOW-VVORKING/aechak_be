package com.aechak.seller.inquiry.response

import com.aechak.application.inquiry.product.usecase.result.SellerProductInquiryMetricsResult

/**
 * 화면 상단 지표 3종. 탭·필터와 무관한 셀러 전체 범위이고, 기한 지남은 서버가 센 값이다 —
 * 프런트가 날짜를 빼서 다시 세면 행의 overdue와 기준이 갈린다.
 */
data class SellerInquiryMetricsResponse(
    val pending: Long,
    val overdue: Long,
    val total: Long,
) {
    companion object {
        fun from(result: SellerProductInquiryMetricsResult): SellerInquiryMetricsResponse =
            SellerInquiryMetricsResponse(pending = result.pending, overdue = result.overdue, total = result.total)
    }
}
