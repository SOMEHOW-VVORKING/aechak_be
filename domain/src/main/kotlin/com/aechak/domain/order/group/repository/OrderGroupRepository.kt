package com.aechak.domain.order.group.repository

import com.aechak.domain.order.group.OrderGroup
import java.time.LocalDateTime

interface OrderGroupRepository {
    fun save(orderGroup: OrderGroup): OrderGroup

    /** 멱등 재요청 판별용. 없으면 null. */
    fun findByIdempotencyKey(idempotencyKey: String): OrderGroup?

    /** 만료 전(expiresAt >= now) 결제대기 주문그룹 수. expiresAt이 없는 행은 세지 않는다 */
    fun countUnexpiredPendingPayment(
        buyerId: Long,
        now: LocalDateTime,
    ): Long
}
