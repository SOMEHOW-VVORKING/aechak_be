package com.aechak.application.order.claim.usecase.command

data class RejectClaimCommand(
    val sellerId: Long,
    val claimPublicId: String,
    val rejectionReason: String,
)
