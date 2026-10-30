package com.aechak.application.order.admin.usecase.query

import java.time.LocalDate

/**
 * 어드민 주문 지표 집계 입력. 목록과 달리 상태·지연·키워드를 받지 않는다 —
 * 카드가 그 축으로 모수를 쪼개 보여주는 것이기 때문이다.
 */
data class AdminOrderMetricsQuery(
    val sellerId: Long?,
    val orderedFrom: LocalDate?,
    val orderedTo: LocalDate?,
)
