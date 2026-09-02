package com.aechak.application.order.facade

import com.aechak.application.order.port.OrderItemForReviewQueryPort
import com.aechak.application.order.service.OrderService
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.result.OrderItemForReviewResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderFacade(
    private val orderService: OrderService,
    private val orderItemForReviewQueryPort: OrderItemForReviewQueryPort,
) : OrderUseCase {
    @Transactional(readOnly = true)
    override fun getOrderItemForReview(
        orderItemId: Long,
        buyerId: Long,
    ): OrderItemForReviewResult? =
        orderItemForReviewQueryPort.findOrderItemForReview(orderItemId, buyerId)?.let(OrderItemForReviewResult::from)
}
