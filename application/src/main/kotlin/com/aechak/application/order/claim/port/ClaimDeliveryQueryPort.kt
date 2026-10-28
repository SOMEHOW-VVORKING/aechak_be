package com.aechak.application.order.claim.port

import java.time.LocalDateTime

interface ClaimDeliveryQueryPort {
    /** 배송완료 전이 시각 — 반품 기한의 기산점. 전이 이력이 없으면 null. */
    fun findDeliveredAt(orderId: Long): LocalDateTime?
}
