package com.aechak.application.order.seller.service

import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.order.repository.OrderStatusHistoryRepository
import org.springframework.stereotype.Service

@Service
class SellerOrderService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
) {
    fun acceptOrder(command: AcceptOrderCommand) {
        val order =
            orderRepository.findByPublicIdAndSellerId(command.orderPublicId, command.sellerId)
                ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

        // 전이의 심판은 조건부 UPDATE다. 경합에서 진 쪽은 0행을 받는다
        if (!orderRepository.transitionStatusIf(order.id, OrderStatus.PAID, OrderStatus.PREPARING)) {
            throw BusinessException(OrderErrorCode.ORDER_NOT_ACCEPTABLE)
        }
        order.accept()

        orderStatusHistoryRepository.save(
            OrderStatusHistory.record(
                order = order,
                fromStatus = OrderStatus.PAID,
                toStatus = order.status,
                triggeredBy = TriggeredBy.SELLER,
            ),
        )
    }
}
