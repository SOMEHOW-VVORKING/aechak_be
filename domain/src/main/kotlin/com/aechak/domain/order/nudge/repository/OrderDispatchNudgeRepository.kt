package com.aechak.domain.order.nudge.repository

import com.aechak.domain.order.nudge.OrderDispatchNudge

/** append-only 로그 — 저장과 읽기만 노출한다. */
interface OrderDispatchNudgeRepository {
    fun save(nudge: OrderDispatchNudge): OrderDispatchNudge

    /** 쿨다운 판정용 최근 한 건 */
    fun findLatestByOrderId(orderId: Long): OrderDispatchNudge?

    /** 주문 상세의 독촉 이력 — 최근 순 */
    fun findAllByOrderIdDesc(orderId: Long): List<OrderDispatchNudge>
}
