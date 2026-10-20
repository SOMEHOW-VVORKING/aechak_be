package com.aechak.application.order.claim.usecase

import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.CompleteClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.ReshipClaimCommand
import com.aechak.application.order.claim.usecase.command.StartClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.UpdateClaimMemoCommand
import com.aechak.application.order.claim.usecase.query.SellerClaimSearchQuery
import com.aechak.application.order.claim.usecase.result.ReshipClaimResult
import com.aechak.application.order.claim.usecase.result.SellerClaimDetailResult
import com.aechak.application.order.claim.usecase.result.SellerClaimPageResult

interface SellerClaimUseCase {
    /** 승인만 한다 — 환불 집행은 별도 경로다. 반품·교환은 회수 대기로 넘어간다. */
    fun approveClaim(command: ApproveClaimCommand)

    fun rejectClaim(command: RejectClaimCommand)

    fun searchClaims(query: SellerClaimSearchQuery): SellerClaimPageResult

    fun getClaim(
        claimPublicId: String,
        sellerId: Long,
    ): SellerClaimDetailResult

    fun updateSellerMemo(command: UpdateClaimMemoCommand)

    /** 회수 송장 등록. */
    fun startCollection(command: StartClaimCollectionCommand)

    /** 회수 완료 — 반품 환불은 이 뒤에만 집행된다. */
    fun completeCollection(command: CompleteClaimCollectionCommand)

    /** 교환 재발송 송장 등록. */
    fun reship(command: ReshipClaimCommand): ReshipClaimResult
}
