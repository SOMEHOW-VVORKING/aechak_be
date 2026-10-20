package com.aechak.application.order.delivery.service

import java.time.LocalDateTime

/** 배송완료부터 구매확정 기한까지의 일수. */
data class AutoConfirmPolicy(
    val days: Long,
) {
    fun dueAt(deliveredAt: LocalDateTime): LocalDateTime = deliveredAt.plusDays(days)
}
