package com.aechak.application.order.delivery.facade

import com.aechak.application.order.delivery.service.OrderDeliveryService
import com.aechak.application.order.delivery.usecase.OrderDeliveryUseCase
import com.aechak.application.order.delivery.usecase.command.CompleteDeliveryBySystemCommand
import com.aechak.application.order.delivery.usecase.query.PendingDeliveryQuery
import com.aechak.application.order.delivery.usecase.result.PendingDeliveryResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderDeliveryFacade(
    private val orderDeliveryService: OrderDeliveryService,
) : OrderDeliveryUseCase {
    @Transactional(readOnly = true)
    override fun findPendingDeliveries(query: PendingDeliveryQuery): List<PendingDeliveryResult> =
        orderDeliveryService
            .findPendingDeliveries(query.dispatchedBefore, query.afterShipmentId, query.limit)
            .map(PendingDeliveryResult::from)

    @Transactional
    override fun completeDeliveryBySystem(command: CompleteDeliveryBySystemCommand) {
        orderDeliveryService.completeBySystem(command.orderId, command.deliveredAt)
    }
}
