package com.aechak.application.order.admin.port.view

import java.time.LocalDateTime

/** 주문에 붙은 배송 한 건. 교환 재발송이 생기면 주문당 여러 행이라 호출자가 최신 한 건을 고른다. */
data class AdminOrderShipmentView(
    val orderId: Long,
    val shipmentId: Long,
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val lastTrackingStatus: String?,
)
