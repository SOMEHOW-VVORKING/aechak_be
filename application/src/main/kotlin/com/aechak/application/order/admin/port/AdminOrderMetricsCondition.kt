package com.aechak.application.order.admin.port

import com.aechak.application.order.admin.support.OrderDelayDeadlines
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 어드민 주문 지표 집계 조건.
 *
 * 목록과 달리 **상태·지연·키워드 필터를 받지 않는다** — 카드는 그 축으로 모수를 쪼개 보여주는 것이라
 * 상태 필터를 걸면 카드 넷 중 셋이 0으로 눌린다. 운영자가 좁히는 축인 셀러·기간만 받는다.
 *
 * - countedStatuses: 건수 카드 셋의 모수
 * - amountStatuses: 주문 금액 카드의 모수. 건수와 모수가 다르다 — 취소 주문은 건수 술어에 애초에
 *   걸리지 않지만 금액에는 그대로 더해지므로 여기서 빼야 한다
 */
data class AdminOrderMetricsCondition(
    val countedStatuses: Set<OrderStatus>,
    val amountStatuses: Set<OrderStatus>,
    val sellerId: Long?,
    val orderedFrom: LocalDateTime?,
    val orderedToExclusive: LocalDateTime?,
    val deadlines: OrderDelayDeadlines,
) {
    init {
        require(countedStatuses.isNotEmpty()) { "상태 집합은 비어 있을 수 없습니다." }
        require(amountStatuses.isNotEmpty()) { "금액 집계 상태 집합은 비어 있을 수 없습니다." }
    }
}
