package com.aechak.application.order.claim.usecase

import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand

interface SellerClaimUseCase {
    /** 승인만 한다 — 환불 집행은 별도 경로다. */
    fun approveClaim(command: ApproveClaimCommand)

    fun rejectClaim(command: RejectClaimCommand)
}
