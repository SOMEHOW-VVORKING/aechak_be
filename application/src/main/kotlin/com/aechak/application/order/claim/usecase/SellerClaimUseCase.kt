package com.aechak.application.order.claim.usecase

import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.UpdateClaimMemoCommand
import com.aechak.application.order.claim.usecase.query.SellerClaimSearchQuery
import com.aechak.application.order.claim.usecase.result.SellerClaimDetailResult
import com.aechak.application.order.claim.usecase.result.SellerClaimPageResult

interface SellerClaimUseCase {
    /** 승인만 한다 — 환불 집행은 별도 경로다. */
    fun approveClaim(command: ApproveClaimCommand)

    fun rejectClaim(command: RejectClaimCommand)

    fun searchClaims(query: SellerClaimSearchQuery): SellerClaimPageResult

    fun getClaim(
        claimPublicId: String,
        sellerId: Long,
    ): SellerClaimDetailResult

    fun updateSellerMemo(command: UpdateClaimMemoCommand)
}
