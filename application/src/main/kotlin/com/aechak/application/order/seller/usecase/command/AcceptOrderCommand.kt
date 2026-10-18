package com.aechak.application.order.seller.usecase.command

data class AcceptOrderCommand(
    val sellerId: Long,
    val orderPublicId: String,
)
