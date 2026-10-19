package com.aechak.application.order.seller.usecase.query

import java.time.LocalDateTime

/**
 * 주문 지표 집계 입력. 주문일 범위가 둘 다 null이면 전체 기간이다.
 * [at]은 발송 기한을 재는 기준 시각 — 호출자가 한 번 떠서 넘긴다. 집계가 스스로 시계를 뜨면
 * 같은 응답의 현재 기간과 직전 기간이 서로 다른 기한으로 판정된다.
 */
data class SellerOrderMetricsQuery(
    val sellerId: Long,
    val at: LocalDateTime,
    val orderedFrom: LocalDateTime?,
    val orderedToExclusive: LocalDateTime?,
)
