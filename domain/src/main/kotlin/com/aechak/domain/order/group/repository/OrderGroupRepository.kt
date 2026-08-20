package com.aechak.domain.order.group.repository

import com.aechak.domain.order.group.OrderGroup

interface OrderGroupRepository {
    fun save(orderGroup: OrderGroup): OrderGroup

    /** 멱등 재요청 판별용. 없으면 null. */
    fun findByIdempotencyKey(idempotencyKey: String): OrderGroup?
}
