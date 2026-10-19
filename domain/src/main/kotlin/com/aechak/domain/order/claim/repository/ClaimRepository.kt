package com.aechak.domain.order.claim.repository

interface ClaimRepository {
    /** 결론이 나지 않은 클레임이 그 주문에 있는지 — 구매확정 차단 판정. */
    fun existsInProgressByOrderId(orderId: Long): Boolean
}
