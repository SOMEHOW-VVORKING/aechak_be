package com.aechak.application.order.port

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.order.enums.OrderStatus

/**
 * 주문 목록 세그먼트 필터. 하위 주문 중 하나라도 해당 상태면 그 주문그룹이 걸림.
 * ALL에도 결제대기가 없음. 결제 전 주문이 테이블에 남는 모델이라 상태 집합에 넣으면 미결제 건이 목록에 뜸.
 */
enum class OrderStatusFilter(
    val code: String,
    val statuses: Set<OrderStatus>,
) {
    ALL("all", OrderStatus.entries.toSet() - OrderStatus.PENDING_PAYMENT),
    ONGOING("ongoing", setOf(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING, OrderStatus.SHIPPING)),
    COMPLETED("completed", setOf(OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED)),
    CANCELLED("cancelled", setOf(OrderStatus.CANCELLED)),
    ;

    companion object {
        fun from(code: String): OrderStatusFilter =
            entries.find { it.code == code } ?: throw BusinessException(CommonErrorCode.INVALID_REQUEST)
    }
}
