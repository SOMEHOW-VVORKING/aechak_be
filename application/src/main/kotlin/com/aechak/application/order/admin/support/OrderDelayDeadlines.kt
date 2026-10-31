package com.aechak.application.order.admin.support

import com.aechak.application.order.admin.usecase.result.AdminOrderDelayReason
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 지연 판정의 두 경계 시각. 목록 행 하나하나의 판정([reasonFor])과 지표 집계의 SQL 술어가
 * 같은 값을 받아 같은 기준으로 센다 — 한쪽만 시계를 뜨면 카드의 건수와 목록의 신호가 어긋난다.
 *
 * 축마다 시계의 시작점이 다르다. 발송 축은 주문 시각에서, 배송 축은 발송 시각에서 센다.
 * `orders.updated_at`은 어느 축에도 쓰지 않는다 — 구매확정 기한 부여처럼 뒤따르는 쓰기에도
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
        dispatchedAt: LocalDateTime?,
    ): AdminOrderDelayReason? =
        when {
            status.isAwaitingDispatch() && orderedAt.isBefore(dispatchDeadline) -> {
                AdminOrderDelayReason.DISPATCH_OVERDUE
            }

            status == OrderStatus.SHIPPING && deliveryClock(orderedAt, dispatchedAt).isBefore(deliveryDeadline) -> {
                AdminOrderDelayReason.DELIVERY_OVERDUE
            }

            else -> {
                null
            }
        }

    /**
     * 배송 지연 시계의 시작점 — 발송 시각이 있으면 그것, 없으면 주문 시각.
     *
     * 주문 시각에서 재면 발송이 늦은 주문이 송장을 받는 순간 발송 축에서 빠지고 배송 축에 그대로
     * 걸려, 운영자가 셀러를 독촉해야 할 건에 택배사를 쫓는다. 반대로 발송 기록이 없는 배송중
     * 주문의 시계를 멈추면 그 주문이 두 축 어디에도 안 잡혀 영구히 보이지 않는다.
     */
    private fun deliveryClock(
        orderedAt: LocalDateTime,
        dispatchedAt: LocalDateTime?,
    ): LocalDateTime = dispatchedAt ?: orderedAt
}
