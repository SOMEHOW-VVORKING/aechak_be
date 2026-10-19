package com.aechak.domain.order.group.repository

import com.aechak.domain.order.group.OrderGroup

interface OrderGroupRepository {
    fun save(orderGroup: OrderGroup): OrderGroup

    fun findById(id: Long): OrderGroup?

    /** 멱등 재요청 판별용. 없으면 null. */
    fun findByIdempotencyKey(idempotencyKey: String): OrderGroup?

    fun findByPublicId(publicId: String): OrderGroup?

    /** 결제 확정의 선점 — 결제대기일 때만 결제완료로. 확정·만료가 경쟁하면 먼저 전이한 쪽만 true */
    fun markPaidIfPending(id: Long): Boolean
}
