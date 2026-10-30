package com.aechak.application.order.admin.port.view

import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import java.time.LocalDateTime

/**
 * 주문 상태 전이 한 건. 전이가 일어난 트랜잭션에서 동기 insert되므로 `created_at`이 전이 시각이다.
 * 최초 전이는 [fromStatus]가 null일 수 있다.
 */
data class AdminOrderStatusHistoryView(
    val historyId: Long,
    val fromStatus: OrderStatus?,
    val toStatus: OrderStatus,
    val triggeredBy: TriggeredBy,
    val occurredAt: LocalDateTime,
)
