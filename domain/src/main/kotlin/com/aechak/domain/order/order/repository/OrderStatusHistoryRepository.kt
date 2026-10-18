package com.aechak.domain.order.order.repository

import com.aechak.domain.order.order.OrderStatusHistory

/** append-only 로그 — 저장만 노출한다. */
interface OrderStatusHistoryRepository {
    fun save(history: OrderStatusHistory): OrderStatusHistory
}
