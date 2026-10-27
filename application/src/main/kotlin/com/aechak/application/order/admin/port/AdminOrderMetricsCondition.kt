package com.aechak.application.order.admin.port

import com.aechak.application.order.admin.support.OrderDelayDeadlines
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 어드민 주문 지표 집계 조건.
 *
 * 목록과 달리 **상태·지연·키워드 필터를 받지 않는다** — 카드는 그 축으로 모수를 쪼개 보여주는 것이라
 * 상태 필터를 걸면 카드 넷 중 셋이 0으로 눌린다. 운영자가 좁히는 축인 셀러·기간만 받는다.
 */
data class AdminOrderMetricsCondition(
    val countedStatuses: Set<OrderStatus>,
    val sellerId: Long?,
    val orderedFrom: LocalDateTime?,
    val orderedToExclusive: LocalDateTime?,
    val deadlines: OrderDelayDeadlines,
) {
    init {
        require(countedStatuses.isNotEmpty()) { "상태 집합은 비어 있을 수 없습니다." }
    }
}
