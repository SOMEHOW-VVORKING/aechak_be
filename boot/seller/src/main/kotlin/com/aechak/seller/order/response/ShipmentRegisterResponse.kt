package com.aechak.seller.order.response

import com.aechak.application.order.seller.usecase.result.RegisterShipmentResult
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

data class ShipmentRegisterResponse(
    val shipmentId: Long,
    val dispatchedAt: LocalDateTime,
    val orderStatus: OrderStatus,
) {
    companion object {
        fun from(result: RegisterShipmentResult): ShipmentRegisterResponse =
            ShipmentRegisterResponse(
                shipmentId = result.shipmentId,
                dispatchedAt = result.dispatchedAt,
                orderStatus = result.orderStatus,
            )
    }
}
