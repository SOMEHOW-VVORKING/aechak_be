package com.aechak.application.inquiry.product.port.view

/**
 * 셀러 문의 지표. 세 값은 필터와 무관한 셀러 전체 범위다 — 탭을 눌러도 지표는 흔들리지 않는다.
 * 철회된 문의는 어디에도 세지 않는다(구매자가 질문을 거둬갔다).
 */
data class SellerProductInquiryMetricsView(
    val pending: Long,
    val overdue: Long,
    val total: Long,
)
