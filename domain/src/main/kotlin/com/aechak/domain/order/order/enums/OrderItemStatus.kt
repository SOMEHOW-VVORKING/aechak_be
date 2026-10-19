package com.aechak.domain.order.order.enums

enum class OrderItemStatus {
    ORDERED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURNED,
    EXCHANGE_REQUESTED,
    ;

    fun canTransitionTo(next: OrderItemStatus): Boolean =
        next in
            when (this) {
                ORDERED -> setOf(CANCELLED, RETURN_REQUESTED, EXCHANGE_REQUESTED)
                RETURN_REQUESTED -> setOf(RETURNED)
                EXCHANGE_REQUESTED, RETURNED, CANCELLED -> emptySet()
            }
}
