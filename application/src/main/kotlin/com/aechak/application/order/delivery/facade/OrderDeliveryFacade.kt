package com.aechak.application.order.delivery.facade

import com.aechak.application.order.delivery.service.OrderDeliveryService
import com.aechak.application.order.delivery.usecase.OrderDeliveryUseCase
import com.aechak.application.order.delivery.usecase.command.CompleteDeliveryBySystemCommand
import com.aechak.application.order.delivery.usecase.result.PendingDeliveryResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class OrderDeliveryFacade(
    private val orderDeliveryService: OrderDeliveryService,
) : OrderDeliveryUseCase {
    @Transactional(readOnly = true)
    override fun findPendingDeliveries(
        dispatchedBefore: LocalDateTime,
        limit: Int,
    ): List<PendingDeliveryResult> = orderDeliveryService.findPendingDeliveries(dispatchedBefore, limit).map(PendingDeliveryResult::from)

    @Transactional
    override fun completeDeliveryBySystem(command: CompleteDeliveryBySystemCommand) {
        orderDeliveryService.completeBySystem(command.orderId, command.deliveredAt)
    }
}
