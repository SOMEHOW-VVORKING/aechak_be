package com.aechak.seller.order.response

import com.aechak.application.order.seller.usecase.result.RegisterShipmentResult
import java.time.LocalDateTime

data class ShipmentRegisterResponse(
    val shipmentId: Long,
    val dispatchedAt: LocalDateTime,
    val orderStatus: String,
) {
    companion object {
        fun from(result: RegisterShipmentResult): ShipmentRegisterResponse =
            ShipmentRegisterResponse(
                shipmentId = result.shipmentId,
                dispatchedAt = result.dispatchedAt,
                orderStatus = result.orderStatus.name,
            )
    }
}
