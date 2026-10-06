package com.aechak.application.order.facade

import com.aechak.application.order.port.OrderItemReviewQueryPort
import com.aechak.application.order.service.OrderService
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.result.ReviewOrderItemResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderFacade(
    private val orderService: OrderService,
    private val orderItemReviewQueryPort: OrderItemReviewQueryPort,
) : OrderUseCase {
    @Transactional(readOnly = true)
    override fun getOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): ReviewOrderItemResult? = orderItemReviewQueryPort.findOrderItemForReview(orderItemId, buyerId)?.let(ReviewOrderItemResult::from)
}
