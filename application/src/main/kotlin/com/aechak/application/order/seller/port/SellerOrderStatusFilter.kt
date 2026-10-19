package com.aechak.application.order.seller.port

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.order.enums.OrderStatus

/**
 * 셀러 주문 목록 세그먼트 필터. 구매자 목록의 OrderStatusFilter와 세그먼트 구성이 달라 따로 둔다.
 * ALL에도 결제대기가 없다 — 결제 전 주문이 테이블에 남는 모델이라 상태 집합에 넣으면 셀러에게 미결제 건이 보인다.
 */
enum class SellerOrderStatusFilter(
    val code: String,
    val statuses: Set<OrderStatus>,
) {
    ALL("all", OrderStatus.entries.toSet() - OrderStatus.PENDING_PAYMENT),
    NEW("new", setOf(OrderStatus.PAID)),
    AWAITING_DISPATCH("awaiting-dispatch", setOf(OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING)),
    SHIPPING("shipping", setOf(OrderStatus.SHIPPING)),
    DELIVERED("delivered", setOf(OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED)),
    CANCELLED("cancelled", setOf(OrderStatus.CANCELLED)),
    ;

    companion object {
        fun from(code: String): SellerOrderStatusFilter =
            entries.find { it.code == code }
                ?: throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "status: 지원하지 않는 값입니다.")
    }
}
