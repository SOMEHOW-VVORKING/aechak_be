package com.aechak.application.order.seller.usecase.command

data class CompleteDeliveryCommand(
    val sellerId: Long,
    val orderPublicId: String,
)
