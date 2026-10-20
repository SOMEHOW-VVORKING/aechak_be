package com.aechak.application.order.claim.usecase.command

data class CompleteClaimCollectionCommand(
    val sellerId: Long,
    val claimPublicId: String,
)
