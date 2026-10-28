package com.aechak.application.order.claim.usecase.command

data class ReshipClaimCommand(
    val sellerId: Long,
    val claimPublicId: String,
    val courierCode: String,
    val trackingNumber: String,
)
