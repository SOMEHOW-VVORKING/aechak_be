package com.aechak.application.order.admin.support

import com.aechak.application.order.admin.usecase.result.AdminOrderDelayReason
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 지연 판정의 두 경계 시각. 목록 행 하나하나의 판정([reasonFor])과 지표 집계의 SQL 술어가
 * 같은 값을 받아 같은 기준으로 센다 — 한쪽만 시계를 뜨면 카드의 건수와 목록의 신호가 어긋난다.
 *
 * 두 경계 모두 주문 시각에서 센다. `orders.updated_at`은 구매확정 기한 부여처럼 뒤따르는 쓰기에도
 * 갱신돼 지연 건이 조용히 지연에서 빠진다.
 */
data class OrderDelayDeadlines(
    val dispatchDeadline: LocalDateTime,
    val deliveryDeadline: LocalDateTime,
) {
    /** 지연이 아니면 null. 두 축이 겹칠 수 없다 — 상태 집합이 서로 배타적이다. */
    fun reasonFor(
        status: OrderStatus,
        orderedAt: LocalDateTime,
    ): AdminOrderDelayReason? =
        when {
            status.isAwaitingDispatch() && orderedAt.isBefore(dispatchDeadline) -> {
                AdminOrderDelayReason.DISPATCH_OVERDUE
            }

            status == OrderStatus.SHIPPING && orderedAt.isBefore(deliveryDeadline) -> {
                AdminOrderDelayReason.DELIVERY_OVERDUE
            }

            else -> {
                null
            }
        }
}
