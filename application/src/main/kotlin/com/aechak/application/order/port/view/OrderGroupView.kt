package com.aechak.application.order.port.view

import java.time.LocalDateTime

data class OrderGroupView(
    val id: Long,
    val publicId: String,
    val orderedAt: LocalDateTime,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val couponDiscountAmount: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
)
