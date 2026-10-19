package com.aechak.seller.order.response

import com.aechak.application.order.shipment.usecase.result.CourierResult

data class CourierListResponse(
    val items: List<CourierResponse>,
) {
    data class CourierResponse(
        val courierCode: String,
        val courierName: String,
        val trackingFormatPattern: String?,
    )

    companion object {
        fun from(results: List<CourierResult>): CourierListResponse =
            CourierListResponse(
                items =
                    results.map {
                        CourierResponse(
                            courierCode = it.courierCode,
                            courierName = it.courierName,
                            trackingFormatPattern = it.trackingFormatPattern,
                        )
                    },
            )
    }
}
