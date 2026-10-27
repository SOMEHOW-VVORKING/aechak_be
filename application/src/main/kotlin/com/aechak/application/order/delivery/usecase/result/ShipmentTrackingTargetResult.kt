package com.aechak.application.order.delivery.usecase.result

import com.aechak.application.order.delivery.usecase.command.SyncShipmentTrackingCommand
import com.aechak.domain.order.shipment.Shipment

/** 추적 갱신 대상 발송. 주문 공개 식별자는 호출부 로그용이다. */
data class ShipmentTrackingTargetResult(
    val shipmentId: Long,
    val orderPublicId: String,
    val courierCode: String,
    val trackingNumber: String,
) {
    fun toCommand(): SyncShipmentTrackingCommand =
        SyncShipmentTrackingCommand(
            shipmentId = shipmentId,
            courierCode = courierCode,
            trackingNumber = trackingNumber,
        )

    companion object {
        fun from(shipment: Shipment): ShipmentTrackingTargetResult =
            ShipmentTrackingTargetResult(
                shipmentId = shipment.id,
                orderPublicId = shipment.order.publicId,
                courierCode = shipment.courier.courierCode,
                trackingNumber = shipment.trackingNumber,
            )
    }
}
