package com.aechak.application.order.seller.usecase.result

import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

data class RegisterShipmentResult(
    val shipmentId: Long,
    val dispatchedAt: LocalDateTime,
    val orderStatus: OrderStatus,
)
