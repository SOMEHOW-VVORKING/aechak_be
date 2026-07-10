package com.aechak.domain.order.order.enums

enum class OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PREPARING,

    /** 배송예정 — 취소는 셀러 승인제(즉시취소 불가, Claim 승인 경로로만 CANCELLED 전이). */
    DISPATCH_PENDING,

    SHIPPING,
    DELIVERED,
    PURCHASE_CONFIRMED,
    CANCELLED,
    ;

    fun isShippedOrLater(): Boolean = this in SHIPPED_OR_LATER

    fun canCancel(): Boolean = this in CANCELLABLE

    fun canTransitionTo(next: OrderStatus): Boolean = next in when (this) {
        PENDING_PAYMENT -> setOf(PAID, CANCELLED)
        PAID -> setOf(PREPARING, CANCELLED)
        PREPARING -> setOf(DISPATCH_PENDING, CANCELLED)
        DISPATCH_PENDING -> setOf(SHIPPING, CANCELLED)
        SHIPPING -> setOf(DELIVERED)
        DELIVERED -> setOf(PURCHASE_CONFIRMED)
        PURCHASE_CONFIRMED, CANCELLED -> emptySet()
    }

    private companion object {
        private val SHIPPED_OR_LATER = setOf(SHIPPING, DELIVERED, PURCHASE_CONFIRMED)
        private val CANCELLABLE = setOf(PAID, PREPARING)
    }
}
