package com.aechak.application.order.port.view

import com.aechak.domain.order.order.enums.OrderStatus

data class SellerOrderView(
    val orderGroupId: Long,
    val orderId: Long,
    val orderPublicId: String,
    val sellerName: String?,
    val status: OrderStatus,
)
