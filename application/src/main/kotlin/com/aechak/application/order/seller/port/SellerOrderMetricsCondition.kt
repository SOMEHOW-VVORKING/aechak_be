package com.aechak.application.order.seller.port

import java.time.LocalDateTime

/**
 * 셀러 대시보드 주문 집계 조건. 주문일 축은 null이면 걸지 않는다(전체 기간).
 * [dispatchDeadline] 이전에 주문된 미발송 건을 발송 지연으로 센다.
 */
data class SellerOrderMetricsCondition(
    val sellerId: Long,
    val orderedFrom: LocalDateTime?,
    val orderedToExclusive: LocalDateTime?,
    val dispatchDeadline: LocalDateTime,
)
