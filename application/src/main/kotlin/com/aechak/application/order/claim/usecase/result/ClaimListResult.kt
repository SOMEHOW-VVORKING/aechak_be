package com.aechak.application.order.claim.usecase.result

import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

data class ClaimListResult(
    val items: List<ClaimResult>,
) {
    data class ClaimResult(
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
        val items: List<ClaimItemResult>,
    )

    data class ClaimItemResult(
        val orderItemId: Long,
        val productName: String,
        val quantity: Int,
        val refundAmount: Long,
    )

    companion object {
        fun of(
            claims: List<Claim>,
            refundAmounts: Map<String, Long>,
            productNames: Map<Long, String>,
        ): ClaimListResult =
            ClaimListResult(
                items =
                    claims.map { claim ->
                        ClaimResult(
                            claimPublicId = claim.publicId,
                            claimType = claim.claimType,
                            status = claim.status,
                            refundStatus = claim.refundStatus,
                            sellerApprovalStatus = claim.sellerApprovalStatus,
                            returnCostBearer = claim.returnCostBearer,
                            refundAmount = refundAmounts[claim.publicId] ?: claim.itemRefundTotal(),
                            rejectionReason = claim.rejectionReason,
                            requestedAt = claim.requestedAt,
                            approvedAt = claim.approvedAt,
                            resolvedAt = claim.resolvedAt,
                            items = claim.items.map { itemResult(it, productNames) },
                        )
                    },
            )

        private fun itemResult(
            item: ClaimItem,
            productNames: Map<Long, String>,
        ): ClaimItemResult =
            ClaimItemResult(
                orderItemId = item.orderItem.id,
                productName = productNames[item.orderItem.id] ?: "",
                quantity = item.quantity,
                refundAmount = item.refundAmount,
            )
    }
}
