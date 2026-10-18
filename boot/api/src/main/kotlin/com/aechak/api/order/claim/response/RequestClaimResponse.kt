package com.aechak.api.order.claim.response

import com.aechak.application.order.claim.usecase.result.RequestClaimResult
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

data class RequestClaimResponse(
    val claimPublicId: String,
    val claimType: ClaimType,
    val status: ClaimStatus,
    val refundAmount: Long,
    val sellerApprovalStatus: SellerApprovalStatus?,
    val autoApproveDueAt: LocalDateTime?,
    val requestedAt: LocalDateTime?,
) {
    companion object {
        fun from(result: RequestClaimResult): RequestClaimResponse =
            RequestClaimResponse(
                claimPublicId = result.claimPublicId,
                claimType = result.claimType,
                status = result.status,
                refundAmount = result.refundAmount,
                sellerApprovalStatus = result.sellerApprovalStatus,
                autoApproveDueAt = result.autoApproveDueAt,
                requestedAt = result.requestedAt,
            )
    }
}
