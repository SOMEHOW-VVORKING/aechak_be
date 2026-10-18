package com.aechak.application.order.seller.usecase.command

data class RegisterShipmentCommand(
    val sellerId: Long,
    val orderPublicId: String,
    val courierCode: String,
    val trackingNumber: String,
)
