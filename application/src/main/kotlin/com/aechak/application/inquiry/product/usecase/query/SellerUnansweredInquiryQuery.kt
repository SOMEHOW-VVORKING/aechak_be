package com.aechak.application.inquiry.product.usecase.query

import java.time.LocalDateTime

/**
 * 대시보드 미응답 문의 집계 조건. 현재 구간은 접수일 [askedFrom, askedToExclusive)이고 둘 다 null이면 전체 기간이다.
 * 직전 구간은 현재 구간에 맞붙은 [previousAskedFrom, askedFrom)이다 — 대시보드 기간 필터가 늘 맞붙은 두 구간을 쓴다.
 */
data class SellerUnansweredInquiryQuery(
    val sellerId: Long,
    val askedFrom: LocalDateTime?,
    val askedToExclusive: LocalDateTime?,
    val previousAskedFrom: LocalDateTime?,
) {
    init {
        require(previousAskedFrom == null || askedFrom != null) { "직전 구간은 현재 구간의 시작이 있어야 정해진다" }
    }
}
