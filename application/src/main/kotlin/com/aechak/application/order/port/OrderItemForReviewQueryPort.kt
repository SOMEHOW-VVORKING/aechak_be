package com.aechak.application.order.port

import com.aechak.application.order.port.view.OrderItemForReviewView

interface OrderItemForReviewQueryPort {
    fun findOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemForReviewView?
}
