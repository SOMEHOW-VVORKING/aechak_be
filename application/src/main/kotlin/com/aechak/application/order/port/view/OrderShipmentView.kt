package com.aechak.application.order.port.view

import java.time.LocalDateTime

/** 구매자 배송 추적용 운송장 읽기 모델. */
data class OrderShipmentView(
    val courierCode: String,
    val courierName: String,
    val trackingNumber: String,
    val dispatchedAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val lastTrackingStatus: String?,
)
