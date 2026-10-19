package com.aechak.application.order.claim.usecase.command

import com.aechak.domain.order.claim.enums.ReturnCostBearer

data class ApproveClaimCommand(
    val sellerId: Long,
    val claimPublicId: String,
    val returnCostBearer: ReturnCostBearer?,
)
