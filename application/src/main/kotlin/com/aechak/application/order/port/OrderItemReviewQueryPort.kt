package com.aechak.application.order.port

import com.aechak.application.order.port.view.OrderItemReviewView

interface OrderItemReviewQueryPort {
    fun findOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemReviewView?
}
