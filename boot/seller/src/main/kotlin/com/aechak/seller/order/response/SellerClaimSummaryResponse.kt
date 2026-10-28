package com.aechak.seller.order.response

import com.aechak.application.order.claim.usecase.result.SellerClaimSummaryResult
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimSettlementStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

/**
 * 네 축(status·refundStatus·sellerApprovalStatus·settlementStatus)을 접지 않고 그대로 내린다.
 * 사유는 코드와 화면 문구(`ClaimReasonCode.label`)를 함께 내린다.
 */
data class SellerClaimSummaryResponse(
    val claimPublicId: String,
    val orderPublicId: String,
    val claimType: ClaimType,
    val reasonCode: ClaimReasonCode?,
    val reasonLabel: String?,
    val reasonDetail: String?,
    val status: ClaimStatus,
    val refundStatus: RefundStatus?,
    val sellerApprovalStatus: SellerApprovalStatus?,
    val settlementStatus: ClaimSettlementStatus?,
    val returnCostBearer: ReturnCostBearer?,
    val returnShippingFee: Long?,
    val dispatched: Boolean,
    val refundAmount: Long,
    val productNames: List<String>,
    val itemCount: Int,
    val rejectionReason: String?,
    val sellerMemo: String?,
    val requestedAt: LocalDateTime?,
    val autoApproveDueAt: LocalDateTime?,
    val approvedAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?,
) {
    companion object {
        fun from(result: SellerClaimSummaryResult): SellerClaimSummaryResponse =
            SellerClaimSummaryResponse(
                claimPublicId = result.claimPublicId,
                orderPublicId = result.orderPublicId,
                claimType = result.claimType,
                reasonCode = result.reasonCode,
                reasonLabel = result.reasonCode?.label,
                reasonDetail = result.reasonDetail,
                status = result.status,
                refundStatus = result.refundStatus,
                sellerApprovalStatus = result.sellerApprovalStatus,
                settlementStatus = result.settlementStatus,
                returnCostBearer = result.returnCostBearer,
                returnShippingFee = result.returnShippingFee,
                dispatched = result.dispatched,
                refundAmount = result.refundAmount,
                productNames = result.productNames,
                itemCount = result.itemCount,
                rejectionReason = result.rejectionReason,
                sellerMemo = result.sellerMemo,
                requestedAt = result.requestedAt,
                autoApproveDueAt = result.autoApproveDueAt,
                approvedAt = result.approvedAt,
                resolvedAt = result.resolvedAt,
            )
    }
}
