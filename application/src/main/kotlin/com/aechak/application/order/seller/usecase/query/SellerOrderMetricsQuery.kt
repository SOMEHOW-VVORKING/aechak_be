package com.aechak.application.order.seller.usecase.query

import java.time.LocalDateTime

/** 주문 지표 집계 입력. 주문일 범위가 둘 다 null이면 전체 기간이다. */
data class SellerOrderMetricsQuery(
    val sellerId: Long,
    val orderedFrom: LocalDateTime?,
    val orderedToExclusive: LocalDateTime?,
)
