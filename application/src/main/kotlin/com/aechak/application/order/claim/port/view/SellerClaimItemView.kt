package com.aechak.application.order.claim.port.view

import com.aechak.domain.order.order.enums.OrderItemStatus

/** 클레임 대상 주문품목. 상품명·옵션명은 주문 시점 스냅샷이다. */
data class SellerClaimItemView(
    val claimId: Long,
    val orderItemId: Long,
    val productName: String,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val refundAmount: Long,
    val itemStatus: OrderItemStatus,
)
