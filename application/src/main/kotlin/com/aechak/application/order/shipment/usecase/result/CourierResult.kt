package com.aechak.application.order.shipment.usecase.result

import com.aechak.domain.order.shipment.CourierMaster

data class CourierResult(
    val courierCode: String,
    val courierName: String,
    val trackingFormatPattern: String?,
) {
    companion object {
        fun from(courierMaster: CourierMaster): CourierResult =
            CourierResult(
                courierCode = courierMaster.courierCode,
                courierName = courierMaster.courierName,
                trackingFormatPattern = courierMaster.trackingFormatPattern,
            )
    }
}
