package com.aechak.application.order.seller.facade

import com.aechak.application.order.seller.service.SellerOrderService
import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.command.CompleteDeliveryCommand
import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
import com.aechak.application.order.seller.usecase.result.RegisterShipmentResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SellerOrderFacade(
    private val sellerOrderService: SellerOrderService,
    private val sellerUseCase: SellerUseCase,
) : SellerOrderUseCase {
    @Transactional
    override fun acceptOrder(command: AcceptOrderCommand) {
        requireActiveSeller(command.sellerId)
        sellerOrderService.acceptOrder(command)
    }

    @Transactional
    override fun registerShipment(command: RegisterShipmentCommand): RegisterShipmentResult {
        requireActiveSeller(command.sellerId)
        return sellerOrderService.registerShipment(command)
    }

    @Transactional
    override fun completeDelivery(command: CompleteDeliveryCommand) {
        requireActiveSeller(command.sellerId)
        sellerOrderService.completeDelivery(command)
    }

    private fun requireActiveSeller(sellerId: Long) {
        if (!sellerUseCase.isActiveSeller(sellerId)) {
            throw BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED)
        }
    }
}
