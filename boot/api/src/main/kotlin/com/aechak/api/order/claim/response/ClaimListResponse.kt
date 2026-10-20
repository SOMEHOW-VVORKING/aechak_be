package com.aechak.api.order.claim.response

import com.aechak.application.order.claim.usecase.result.ClaimListResult
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

data class ClaimListResponse(
    val items: List<ClaimItem>,
) {
    data class ClaimItem(
        val claimPublicId: String,
        val claimType: ClaimType,
        val status: ClaimStatus,
        val refundStatus: RefundStatus?,
        val sellerApprovalStatus: SellerApprovalStatus?,
        val returnCostBearer: ReturnCostBearer?,
        val refundAmount: Long,
        val rejectionReason: String?,
        val requestedAt: LocalDateTime?,
        val approvedAt: LocalDateTime?,
        val resolvedAt: LocalDateTime?,
        val items: List<ClaimLine>,
    )

    data class ClaimLine(
        val orderItemId: Long,
        val productName: String,
        val quantity: Int,
        val refundAmount: Long,
    )

    companion object {
        fun from(result: ClaimListResult): ClaimListResponse =
            ClaimListResponse(
                items =
                    result.items.map { claim ->
                        ClaimItem(
                            claimPublicId = claim.claimPublicId,
                            claimType = claim.claimType,
                            status = claim.status,
                            refundStatus = claim.refundStatus,
                            sellerApprovalStatus = claim.sellerApprovalStatus,
                            returnCostBearer = claim.returnCostBearer,
                            refundAmount = claim.refundAmount,
                            rejectionReason = claim.rejectionReason,
                            requestedAt = claim.requestedAt,
                            approvedAt = claim.approvedAt,
                            resolvedAt = claim.resolvedAt,
                            items =
                                claim.items.map {
                                    ClaimLine(
                                        orderItemId = it.orderItemId,
                                        productName = it.productName,
                                        quantity = it.quantity,
                                        refundAmount = it.refundAmount,
                                    )
                                },
                        )
                    },
            )
    }
}
