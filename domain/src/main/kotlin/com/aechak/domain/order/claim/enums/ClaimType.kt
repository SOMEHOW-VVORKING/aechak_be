package com.aechak.domain.order.claim.enums

import com.aechak.domain.order.order.enums.OrderStatus

enum class ClaimType {
    CANCEL,
    RETURN,
    EXCHANGE,
    ;

    fun isRequestableFrom(orderStatus: OrderStatus): Boolean = orderStatus in requestableFrom()

    /** 회수 단계를 거쳐야 하는 유형 — 취소는 물건이 나가기 전이라 회수가 없다. */
    fun requiresCollection(): Boolean = this != CANCEL

    /** 돈을 돌려주는 유형 — 교환은 재발송으로 끝나고 환불이 없다. */
    fun isRefundable(): Boolean = this != EXCHANGE

    /**
     * 취소는 발송 전까지만 받는다 — 발송 이후는 반품 경로다.
     * 반품·교환은 배송완료 건만 받는다. 반품 기한은 배송완료 시각이 필요해 application이 따로 본다.
     */
    private fun requestableFrom(): Set<OrderStatus> =
        when (this) {
            CANCEL -> setOf(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING)
            RETURN, EXCHANGE -> setOf(OrderStatus.DELIVERED)
        }

    companion object {
        /** 환불 집행 스캔 대상 유형. */
        val REFUNDABLE: Set<ClaimType> = entries.filter { it.isRefundable() }.toSet()
    }
}
