package com.aechak.application.order.claim.usecase.result

import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.application.order.claim.port.view.SellerClaimView
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimSettlementStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

/**
 * 셀러 클레임 목록 한 행. 상태는 네 축을 그대로 내린다 — 한 값으로 접는 판정은 화면이 한다.
 * 반품비 부담 문구도 조립하지 않고 근거 3필드(returnCostBearer·returnShippingFee·dispatched)만 내린다.
 */
data class SellerClaimSummaryResult(
    val claimPublicId: String,
    val orderPublicId: String,
    val claimType: ClaimType,
    val reasonCode: ClaimReasonCode?,
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
        fun of(
            view: SellerClaimView,
            items: List<SellerClaimItemView>,
            refundAmount: Long,
            returnShippingFee: Long?,
            dispatched: Boolean,
        ): SellerClaimSummaryResult =
            SellerClaimSummaryResult(
                claimPublicId = view.claimPublicId,
                orderPublicId = view.orderPublicId,
                claimType = view.claimType,
                reasonCode = view.reasonCode,
                reasonDetail = view.reasonDetail,
                status = view.status,
                refundStatus = view.refundStatus,
                sellerApprovalStatus = view.sellerApprovalStatus,
                settlementStatus = view.settlementStatus,
                returnCostBearer = view.returnCostBearer,
                returnShippingFee = returnShippingFee,
                dispatched = dispatched,
                refundAmount = refundAmount,
                productNames = items.map { it.productName }.distinct(),
                itemCount = items.size,
                rejectionReason = view.rejectionReason,
                sellerMemo = view.sellerMemo,
                requestedAt = view.requestedAt,
                autoApproveDueAt = view.autoApproveDueAt,
                approvedAt = view.approvedAt,
                resolvedAt = view.resolvedAt,
            )
    }
}
