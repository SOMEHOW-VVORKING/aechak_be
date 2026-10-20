package com.aechak.domain.order.order.enums

enum class OrderItemStatus {
    ORDERED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURNED,
    EXCHANGE_REQUESTED,
    ;

    /** 반품·교환 요청에서 ORDERED로 돌아가는 길은 거부·철회의 되돌림이다 — 없으면 그 품목은 다시 요청할 수 없다. */
    fun canTransitionTo(next: OrderItemStatus): Boolean =
        next in
            when (this) {
                ORDERED -> setOf(CANCELLED, RETURN_REQUESTED, EXCHANGE_REQUESTED)
                RETURN_REQUESTED -> setOf(RETURNED, ORDERED)
                EXCHANGE_REQUESTED -> setOf(ORDERED)
                RETURNED, CANCELLED -> emptySet()
            }

    companion object {
        /** 더 이상 이행 대상이 아닌 상태 — 주문 전체가 이 상태면 주문이 종결된다. */
        val RESOLVED: Set<OrderItemStatus> = setOf(CANCELLED, RETURNED)
    }
}
