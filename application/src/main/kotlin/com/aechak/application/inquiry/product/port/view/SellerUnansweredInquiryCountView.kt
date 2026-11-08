package com.aechak.application.inquiry.product.port.view

/** 대시보드 미응답 문의. [previous]는 직전 기간을 묻지 않았으면 null이다. */
data class SellerUnansweredInquiryCountView(
    val current: Long,
    val previous: Long?,
)
