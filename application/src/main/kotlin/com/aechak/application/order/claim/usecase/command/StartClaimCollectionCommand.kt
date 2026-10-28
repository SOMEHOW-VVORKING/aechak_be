package com.aechak.application.order.claim.usecase.command

data class StartClaimCollectionCommand(
    val sellerId: Long,
    val claimPublicId: String,
    val courierCode: String,
    val trackingNumber: String,
)
