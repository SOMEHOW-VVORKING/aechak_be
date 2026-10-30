package com.aechak.application.order.seller.usecase.result

import com.aechak.application.order.seller.port.view.SellerOrderMetricsView

data class SellerOrderMetricsResult(
    val newOrders: Long,
    val awaitingShipment: Long,
    val shippingDelayed: Long,
) {
    companion object {
        fun from(view: SellerOrderMetricsView): SellerOrderMetricsResult =
            SellerOrderMetricsResult(
                newOrders = view.newOrders,
                awaitingShipment = view.awaitingShipment,
                shippingDelayed = view.shippingDelayed,
            )
    }
}
