package com.aechak.application.order.port.view

import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/** 리뷰 작성 자격 판정용 주문품목 읽기 모델 */
data class OrderItemForReviewView(
    val orderItemId: Long,
    val orderStatus: OrderStatus,
    val itemStatus: OrderItemStatus,
    val purchaseConfirmedAt: LocalDateTime?,
    val productId: Long,
    val optionNameSnapshot: String,
)
