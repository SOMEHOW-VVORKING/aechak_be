package com.aechak.domain.order.group.repository

import com.aechak.domain.order.group.OrderGroup
import java.time.LocalDateTime

interface OrderGroupRepository {
    fun save(orderGroup: OrderGroup): OrderGroup

    fun findById(id: Long): OrderGroup?

    /** 멱등 재요청 판별용. 없으면 null. */
    fun findByIdempotencyKey(idempotencyKey: String): OrderGroup?

    fun findByPublicId(publicId: String): OrderGroup?

    /** 결제 확정의 선점 — 결제대기일 때만 결제완료로. 확정·만료가 경쟁하면 먼저 전이한 쪽만 true */
    fun markPaidIfPending(id: Long): Boolean

    /** 만료 취소의 선점 — 결제대기일 때만 취소로. 확정·만료가 경쟁하면 먼저 전이한 쪽만 true */
    fun cancelIfPending(id: Long): Boolean

    /** 만료 배치 스캔 — 결제대기이면서 만료 시각이 지난 그룹을 만료가 오래된 순으로 limit건까지 */
    fun findExpiredPending(
        now: LocalDateTime,
        limit: Int,
    ): List<OrderGroup>
}
