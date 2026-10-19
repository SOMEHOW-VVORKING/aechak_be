package com.aechak.application.order.delivery.usecase.command

import java.time.LocalDateTime

data class CompleteDeliveryBySystemCommand(
    val orderId: Long,
    val deliveredAt: LocalDateTime,
)
