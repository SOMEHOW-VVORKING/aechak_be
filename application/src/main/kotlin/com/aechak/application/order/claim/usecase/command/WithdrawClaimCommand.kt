package com.aechak.application.order.claim.usecase.command

data class WithdrawClaimCommand(
    val buyerId: Long,
    val orderPublicId: String,
    val claimPublicId: String,
)
