package com.aechak.seller.order.response

import com.aechak.application.order.claim.usecase.result.ReshipClaimResult
import com.aechak.domain.order.shipment.enums.ShipmentType
import java.time.LocalDateTime

data class ReshipClaimResponse(
    val shipmentType: ShipmentType,
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: LocalDateTime?,
) {
    companion object {
        fun from(result: ReshipClaimResult): ReshipClaimResponse =
            ReshipClaimResponse(
                shipmentType = result.shipmentType,
                courierCode = result.courierCode,
                courierName = result.courierName,
                trackingNumber = result.trackingNumber,
                dispatchedAt = result.dispatchedAt,
            )
    }
}
