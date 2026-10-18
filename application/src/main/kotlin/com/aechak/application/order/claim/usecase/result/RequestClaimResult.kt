package com.aechak.application.order.claim.usecase.result

import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

data class RequestClaimResult(
    val claimPublicId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
    val refundAmount: Long,
    val sellerApprovalStatus: SellerApprovalStatus?,
    val autoApproveDueAt: LocalDateTime?,
    val requestedAt: LocalDateTime?,
) {
    companion object {
        fun of(
            claim: Claim,
            refundAmount: Long,
        ): RequestClaimResult =
            RequestClaimResult(
                claimPublicId = claim.publicId,
                claimType = claim.claimType,
                status = claim.status,
                refundAmount = refundAmount,
                sellerApprovalStatus = claim.sellerApprovalStatus,
                autoApproveDueAt = claim.autoApproveDueAt,
                requestedAt = claim.requestedAt,
            )
    }
}
