package com.aechak.seller.order.request

import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.domain.order.claim.enums.ReturnCostBearer

/** 반품비 부담 주체는 반품·교환만 쓴다 — 취소는 비워 보낸다. */
data class ApproveClaimRequest(
    val returnCostBearer: ReturnCostBearer? = null,
) {
    fun toCommand(
        sellerId: Long,
        claimPublicId: String,
    ): ApproveClaimCommand =
        ApproveClaimCommand(
            sellerId = sellerId,
            claimPublicId = claimPublicId,
            returnCostBearer = returnCostBearer,
        )
}
