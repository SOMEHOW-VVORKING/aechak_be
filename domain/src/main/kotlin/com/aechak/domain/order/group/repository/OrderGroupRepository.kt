package com.aechak.domain.order.group.repository

import com.aechak.domain.order.group.OrderGroup
import java.time.LocalDateTime

interface OrderGroupRepository {
    fun save(orderGroup: OrderGroup): OrderGroup

    /** 멱등 재요청 판별용. 없으면 null. */
    fun findByIdempotencyKey(idempotencyKey: String): OrderGroup?

    fun findByPublicId(publicId: String): OrderGroup?

    fun findById(id: Long): OrderGroup?

    fun findExpiredPendingTargets(
        now: LocalDateTime,
        after: ExpiredPendingOrderGroup?,
        limit: Int,
    ): List<ExpiredPendingOrderGroup>

    fun cancelIfPending(id: Long): Boolean
}

// data 객체로 처리. 엔티티보다 가볍고 취소 시 DB와 결제 서비스에서 다시 확인해야 하므로
data class ExpiredPendingOrderGroup(
    val id: Long,
    val publicId: String,
    val expiresAt: LocalDateTime,
)
