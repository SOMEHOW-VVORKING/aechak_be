package com.aechak.application.order.port.view

import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/** 금액 중 total로 시작하는 넷과 usedPoint는 결제 단위인 주문그룹 값이고, sellerShippingFee만 이 셀러 몫. */
data class OrderDetailView(
    val orderId: Long,
    val orderPublicId: String,
    val orderGroupPublicId: String,
    val orderedAt: LocalDateTime,
    val status: OrderStatus,
    val sellerName: String?,
    val sellerShippingFee: Long,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val couponDiscountAmount: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
)
