package com.aechak.application.order.admin.port.view

import com.aechak.domain.order.order.enums.OrderItemStatus

/** 주문 품목 한 줄. 상품명·옵션명·단가는 주문 시점 스냅샷이라 이후 상품 변경과 무관하다. */
data class AdminOrderLineView(
    val orderId: Long,
    val orderItemId: Long,
    val productPublicId: String,
    val productName: String,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val discountAllocatedAmount: Long,
    val itemStatus: OrderItemStatus,
)
