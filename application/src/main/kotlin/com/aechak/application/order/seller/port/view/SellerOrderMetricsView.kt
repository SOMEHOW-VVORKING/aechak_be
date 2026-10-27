package com.aechak.application.order.seller.port.view

/** 주문 집계 한 묶음 — 지표마다 쿼리를 따로 돌지 않도록 한 번에 받는다. */
data class SellerOrderMetricsView(
    val newOrders: Long,
    val awaitingShipment: Long,
    val shippingDelayed: Long,
)
