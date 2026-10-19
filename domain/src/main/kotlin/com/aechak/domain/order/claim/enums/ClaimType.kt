package com.aechak.domain.order.claim.enums

import com.aechak.domain.order.order.enums.OrderStatus

enum class ClaimType {
    CANCEL,
    RETURN,
    EXCHANGE,
    ;

    fun isRequestableFrom(orderStatus: OrderStatus): Boolean = orderStatus in requestableFrom()

    /**
     * 취소는 발송 전까지만 받는다 — 발송 이후는 반품 경로다.
     * 반품·교환은 회수와 재발송 경로가 열리기 전까지 요청을 받지 않는다.
     */
    private fun requestableFrom(): Set<OrderStatus> =
        when (this) {
            CANCEL -> setOf(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING)
            RETURN, EXCHANGE -> emptySet()
        }
}
