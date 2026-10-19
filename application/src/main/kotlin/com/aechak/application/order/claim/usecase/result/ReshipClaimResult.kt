package com.aechak.application.order.claim.usecase.result

import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import java.time.LocalDateTime

data class ReshipClaimResult(
    val shipmentType: ShipmentType,
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: LocalDateTime?,
) {
    companion object {
        fun from(shipment: Shipment): ReshipClaimResult =
            ReshipClaimResult(
                shipmentType = shipment.shipmentType,
                courierCode = shipment.courier.courierCode,
                courierName = shipment.courier.courierName,
                trackingNumber = shipment.trackingNumber,
                dispatchedAt = shipment.dispatchedAt,
            )
    }
}
