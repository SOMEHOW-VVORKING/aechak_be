package com.aechak.application.order.seller.port.view

import com.aechak.domain.order.order.enums.OrderItemStatus

/** 상품명·썸네일은 주문 시점 product_versions 스냅샷, 옵션명·금액은 주문 시점 order_items 스냅샷. */
data class SellerOrderLineView(
    val orderId: Long,
    val orderItemId: Long,
    val productPublicId: String,
    val productName: String,
    val thumbnailKey: String,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val discountAllocatedAmount: Long,
    val itemStatus: OrderItemStatus,
)
